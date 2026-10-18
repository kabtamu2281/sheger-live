import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createTestDb, rejects, key } from './harness.js';

let db;
before(async () => { db = await createTestDb('accounts'); });
after(async () => { await db.close(); });

test('signup enforces minimum age, reserved and duplicate usernames', async () => {
  const [{ id: kid }] = await db.sql(`insert into auth.users (email) values ('kid@example.test') returning id`);
  const tooYoung = `${new Date().getFullYear() - 12}-01-01`;
  await rejects(db.as(kid, `select complete_signup('kiddo', 'Kid', $1::date, 'am', '2026-10')`, [tooYoung]), 'age_ineligible');

  const [{ id: u }] = await db.sql(`insert into auth.users (email) values ('u@example.test') returning id`);
  await rejects(db.as(u, `select complete_signup('admin', 'X', '1990-01-01', 'en', '2026-10')`), 'username_reserved');
  await rejects(db.as(u, `select complete_signup('ethio_live_official', 'X', '1990-01-01', 'en', '2026-10')`), 'username_reserved');
  await rejects(db.as(u, `select complete_signup('a', 'X', '1990-01-01', 'en', '2026-10')`), 'username_invalid');
  await db.as(u, `select complete_signup('selam', 'Selam', '1990-01-01', 'am', '2026-10')`);
  await rejects(db.as(u, `select complete_signup('selam2', 'Selam', '1990-01-01', 'am', '2026-10')`), 'already_registered');

  const [{ id: other }] = await db.sql(`insert into auth.users (email) values ('o@example.test') returning id`);
  await rejects(db.as(other, `select complete_signup('SELAM', 'Other', '1990-01-01', 'en', '2026-10')`), 'username_taken');
});

test('younger users get protective defaults and cannot opt into Nearby', async () => {
  const minor = await db.newUser({ adult: false });
  const acct = await db.call(minor, 'my_account');
  assert.equal(acct.profile.age_band, 'minor');
  assert.equal(acct.settings.dm_policy, 'followers');
  await db.as(minor, `select update_settings('{"show_in_nearby": true, "dm_policy": "everyone"}')`);
  const after2 = await db.call(minor, 'my_account');
  assert.equal(after2.settings.show_in_nearby, false);
  assert.equal(after2.settings.dm_policy, 'followers');
});

test('follow counters, block removes follows and hides profiles', async () => {
  const a = await db.newUser();
  const b = await db.newUser();
  await db.as(a, 'select follow_user($1)', [b]);
  await db.as(a, 'select follow_user($1)', [b]); // idempotent
  let pb = await db.call(a, 'public_profile', [b]);
  assert.equal(pb.follower_count, 1);
  assert.equal(pb.is_following, true);
  await rejects(db.as(a, 'select follow_user($1)', [a]), 'cannot_follow_self');

  await db.as(b, 'select block_user($1)', [a]);
  await rejects(db.call(a, 'public_profile', [b]), 'user_not_found');
  pb = await db.call(b, 'public_profile', [b]);
  assert.equal(pb.follower_count, 0);
  await rejects(db.as(a, 'select follow_user($1)', [b]), 'blocked');
  const blocked = await db.as(b, 'select * from list_blocked()');
  assert.equal(blocked.length, 1);
});

test('direct messages respect privacy, requests, blocks and dedupe', async () => {
  const alice = await db.newUser();
  const bob = await db.newUser();
  await db.as(bob, `select update_settings('{"dm_policy": "followers"}')`);
  const conv = await db.call(alice, 'open_conversation', [bob]);
  const [c] = await db.sql('select status from conversations where id = $1', [conv]);
  assert.equal(c.status, 'request');

  const clientId = key();
  await db.as(alice, 'select send_dm($1, $2, $3::uuid)', [conv, 'Selam! ', clientId]);
  await db.as(alice, 'select send_dm($1, $2, $3::uuid)', [conv, 'Selam! ', clientId]); // retry: same message
  const count = await db.sql('select count(*)::int as n from dm_messages where conversation_id = $1', [conv]);
  assert.equal(count[0].n, 1);
  await rejects(db.as(alice, 'select send_dm($1, $2, $3::uuid)', [conv, 'again', key()]), 'awaiting_acceptance');

  const requests = await db.as(bob, 'select * from list_conversations(true)');
  assert.equal(requests.length, 1);
  assert.equal(requests[0].unread_count, '1');
  await db.as(bob, 'select respond_message_request($1, true)', [conv]);
  await db.as(alice, 'select send_dm($1, $2, $3::uuid)', [conv, 'Thanks', key()]);

  // A third user cannot read the conversation even with a direct table query.
  const eve = await db.newUser();
  const leaked = await db.as(eve, 'select * from dm_messages where conversation_id = $1', [conv]);
  assert.equal(leaked.length, 0);
  await rejects(db.as(eve, 'select send_dm($1, $2, $3::uuid)', [conv, 'hi', key()]), 'conversation_not_found');

  await db.as(bob, 'select block_user($1)', [alice]);
  await rejects(db.as(alice, 'select send_dm($1, $2, $3::uuid)', [conv, 'hello?', key()]), 'dm_not_allowed');
});

test('adults cannot start conversations with younger users who do not follow them', async () => {
  const adult = await db.newUser();
  const minor = await db.newUser({ adult: false });
  await rejects(db.call(adult, 'open_conversation', [minor]), 'dm_not_allowed');
  await db.as(minor, 'select follow_user($1)', [adult]);
  const conv = await db.call(adult, 'open_conversation', [minor]);
  assert.ok(conv);
});

test('DM rate limiting', async () => {
  const a = await db.newUser();
  const b = await db.newUser();
  const conv = await db.call(a, 'open_conversation', [b]);
  for (let i = 0; i < 20; i++) await db.as(a, 'select send_dm($1, $2, $3::uuid)', [conv, `m${i}`, key()]);
  await rejects(db.as(a, 'select send_dm($1, $2, $3::uuid)', [conv, 'one too many', key()]), 'rate_limited');
});

test('reports capture evidence and prioritise child safety', async () => {
  const a = await db.newUser();
  const b = await db.newUser();
  const conv = await db.call(a, 'open_conversation', [b]);
  const [msg] = await db.as(a, 'select (send_dm($1, $2, $3::uuid)).id', [conv, 'nasty text', key()]);
  const reportId = await db.call(b, 'report_content', ['dm_message', String(msg.id), 'minor_safety', 'context']);
  const [r] = await db.sql('select * from reports where id = $1', [reportId]);
  assert.equal(r.priority, 1);
  assert.equal(r.target_user_id, a);
  assert.equal(r.evidence.body, 'nasty text');
  // Someone outside the conversation cannot report (and thereby read) the message.
  const eve = await db.newUser();
  await rejects(db.call(eve, 'report_content', ['dm_message', String(msg.id), 'spam', '']), 'target_not_found');
});

test('row level security hides private data and blocks direct writes', async () => {
  const a = await db.newUser();
  const b = await db.newUser();
  assert.equal((await db.as(b, 'select * from user_private where user_id = $1', [a])).length, 0);
  assert.equal((await db.as(b, 'select * from wallet_accounts where owner_id = $1', [a])).length, 0);
  assert.equal((await db.as(b, 'select * from user_settings where user_id = $1', [a])).length, 0);
  await rejects(db.as(b, 'select age_band from profiles where id = $1', [a]), 'permission denied');
  await rejects(db.as(b, `update profiles set display_name = 'hacked' where id = $1`, [a]), 'permission denied');
  await rejects(db.as(a, `update wallet_accounts set balance = 1000000 where owner_id = $1`, [a]), 'permission denied');
  await rejects(db.as(a, `insert into notifications (user_id, type) values ($1, 'gift')`, [b]), 'permission denied');
  await rejects(db.as(a, 'select * from audit_logs'), 'permission denied');
  // Internal helpers are not callable by users.
  await rejects(db.as(a, `select wallet_account($1, 'user_coins')`, [a]), 'permission denied');
  await rejects(db.as(a, `select my_balances($1)`, [b]), 'permission denied');
});

test('account deletion erases personal data but keeps the ledger', async () => {
  const u = await db.newUser();
  const fan = await db.newUser();
  await db.fund(u);
  await db.as(fan, 'select follow_user($1)', [u]);
  await db.as(u, 'select delete_my_account()');
  const [p] = await db.sql('select username, display_name, bio, account_status from profiles where id = $1', [u]);
  assert.match(p.username, /^deleted_/);
  assert.equal(p.display_name, 'Deleted user');
  assert.equal(p.account_status, 'deleted');
  const follows = await db.sql('select * from follows where followee_id = $1', [u]);
  assert.equal(follows.length, 0);
  const entries = await db.sql('select count(*)::int as n from ledger_entries e join wallet_accounts a on a.id = e.account_id where a.owner_id = $1', [u]);
  assert.ok(entries[0].n > 0);
  await rejects(db.as(u, 'select follow_user($1)', [fan]), 'account_deleted');
});

test('data export includes the user\'s own records', async () => {
  const u = await db.newUser();
  const exp = await db.call(u, 'export_my_data');
  assert.ok(exp.account.profile.id === u);
  assert.ok(Array.isArray(exp.ledger));
});
