import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createTestDb, rejects, key } from './harness.js';

let db;
before(async () => { db = await createTestDb('pk'); });
after(async () => { await db.close(); });

test('PK battle: invite, accept, server-derived scores, authoritative result', async () => {
  const hostA = await db.newUser();
  const hostB = await db.newUser();
  const roomA = await db.goLive(hostA);
  const roomB = await db.goLive(hostB);
  const fanA = await db.newUser();
  const fanB = await db.newUser();
  await db.as(fanA, 'select join_room($1)', [roomA]);
  await db.as(fanB, 'select join_room($1)', [roomB]);
  await db.fund(fanA, 'coins_1200');
  await db.fund(fanB, 'coins_1200');

  const candidates = await db.call(hostA, 'pk_candidates', [roomA]);
  assert.ok(candidates.some((c) => c.id === roomB));
  const match = await db.call(hostA, 'pk_invite', [roomA, roomB, 120]);
  await rejects(db.as(hostA, 'select pk_invite($1, $2, 120)', [roomA, roomB]), 'pk_already_active');
  await rejects(db.as(hostA, 'select pk_respond($1, true)', [match]), 'pk_not_found');
  await db.as(hostB, 'select pk_respond($1, true)', [match]);

  // Gifts before promo are spent first; spend the promo with a free-standing gift to keep numbers simple.
  await db.call(fanA, 'send_gift', [roomA, hostA, 'jebena', 1, key()]); // 99 coins
  await db.call(fanB, 'send_gift', [roomB, hostB, 'kebero', 1, key()]); // 199 coins
  await db.call(fanB, 'send_gift', [roomB, hostB, 'rose', 1, key()]); // 20 coins

  // Clients cannot touch scores.
  await rejects(db.as(fanA, 'update pk_matches set score_a = 999999 where id = $1', [match]), 'permission denied');
  await rejects(db.as(fanA, `insert into pk_score_events (match_id, side, gift_transaction_id, points) values ($1, 'a', gen_random_uuid(), 5)`, [match]), 'permission denied');

  let finished = await db.as(fanA, 'select status from pk_finish($1)', [match]);
  assert.equal(finished[0].status, 'running', 'cannot be finished early by viewers');

  await db.sql(`update pk_matches set ends_at = now() - interval '1 second' where id = $1`, [match]);
  // Gifts after the deadline do not count.
  await db.call(fanA, 'send_gift', [roomA, hostA, 'rose', 1, key()]);
  finished = await db.as(fanA, 'select * from pk_finish($1)', [match]);
  assert.equal(finished[0].status, 'finished');
  assert.equal(Number(finished[0].score_a), 99);
  assert.equal(Number(finished[0].score_b), 219);
  assert.equal(finished[0].winner, 'b');
  const badge = await db.sql(`select * from user_badges where user_id = $1 and badge_id = 'pk_winner'`, [hostB]);
  assert.equal(badge.length, 1);

  const rank = await db.as(fanA, `select * from get_rankings('pk', 'daily')`);
  assert.equal(rank[0].user_id, hostB);
  const creators = await db.as(fanA, `select * from get_rankings('creators', 'weekly')`);
  assert.ok(creators.length >= 2);
});

test('PK invitations expire and ending a stream closes the match', async () => {
  const a = await db.newUser();
  const b = await db.newUser();
  const ra = await db.goLive(a);
  const rb = await db.goLive(b);
  const m1 = await db.call(a, 'pk_invite', [ra, rb, 60]);
  await db.sql(`update pk_matches set invited_at = now() - interval '2 minutes' where id = $1`, [m1]);
  await rejects(db.as(b, 'select pk_respond($1, true)', [m1]), 'pk_invite_expired');
  const m2 = await db.call(a, 'pk_invite', [ra, rb, 60]);
  await db.as(b, 'select pk_respond($1, true)', [m2]);
  await db.as(a, 'select end_live($1)', [ra]);
  const [m] = await db.sql('select status from pk_matches where id = $1', [m2]);
  assert.equal(m.status, 'finished');
});

test('staff functions require a staff role; superadmin bootstrap works exactly once', async () => {
  const nobody = await db.newUser();
  await rejects(db.as(nobody, 'select staff_analytics()'), 'staff_only');
  await rejects(db.as(nobody, `select bootstrap_superadmin('x@example.test')`), 'permission denied');

  const owner = await db.newUser({ username: 'owner_account' });
  await db.service(`select bootstrap_superadmin('owner_account@example.test')`);
  await rejects(db.service(`select bootstrap_superadmin('owner_account@example.test')`), 'superadmin_exists');
  const who = await db.call(owner, 'staff_whoami');
  assert.equal(who.role, 'superadmin');
  const mod = await db.newUser();
  await rejects(db.as(owner, `select staff_set_role($1, 'moderator', true)`, [mod]), 'mfa_required');
  await db.as(owner, `select staff_set_role($1, 'moderator', true)`, [mod], { aal: 'aal2' });
  await rejects(db.as(mod, `select staff_set_config('free_gifts_per_day', '50')`), 'insufficient_staff_role');
  await rejects(db.as(owner, `select staff_set_config('creator_bean_share_bps', '6000')`), 'mfa_required');
  await db.as(owner, `select staff_set_config('creator_bean_share_bps', '6000')`, [], { aal: 'aal2' });
  const log = await db.as(owner, `select * from staff_audit_log(10)`);
  assert.ok(log.some((l) => l.action === 'config.set'));
  await rejects(db.as(mod, 'select * from staff_audit_log(10)'), 'insufficient_staff_role');
});

test('moderation: reports queue, suspension ends the stream, appeals need a different reviewer', async () => {
  const [mod1, mod2] = [await db.newUser(), await db.newUser()];
  await db.sql(`insert into staff_members (user_id, role) values ($1, 'moderator'), ($2, 'moderator')`, [mod1, mod2]);
  const host = await db.newUser();
  const room = await db.goLive(host);
  const viewer = await db.newUser();
  await db.as(viewer, 'select join_room($1)', [room]);
  const reportId = await db.call(viewer, 'report_content', ['room', room, 'nudity', 'inappropriate']);
  const queue = await db.as(mod1, `select * from staff_report_queue('open')`);
  assert.ok(queue.some((r) => r.id === reportId && r.evidence.title === 'Test live'));

  const actionId = await db.call(mod1, 'staff_moderate', [host, 'suspend', 'Nudity on stream', 24, reportId, room]);
  const [r] = await db.sql('select status from live_rooms where id = $1', [room]);
  assert.equal(r.status, 'banned');
  await rejects(db.as(host, `select * from start_live('video', 'back', 'chat', 'en', 'public', '{}', 4, null)`), 'account_suspended');
  await rejects(db.as(mod1, `select staff_moderate($1, 'ban', 'too far', null)`, [host]), 'insufficient_staff_role');

  const appeal = await db.call(host, 'submit_appeal', [actionId, 'It was a misunderstanding, please review.']);
  await rejects(db.as(mod1, 'select staff_decide_appeal($1, true, $2)', [appeal, 'ok']), 'cannot_review_own_action');
  await db.as(mod2, 'select staff_decide_appeal($1, true, $2)', [appeal, 'Context reviewed']);
  const [p] = await db.sql('select account_status from profiles where id = $1', [host]);
  assert.equal(p.account_status, 'active');
  const [rep] = await db.sql('select status from reports where id = $1', [reportId]);
  assert.equal(rep.status, 'actioned');
});

test('fan clubs: paid join is idempotent and goes through the ledger', async () => {
  const creator = await db.newUser();
  await db.goLive(creator);
  const [club] = await db.as(creator, `select * from upsert_fan_club('Buna Crew', 'BUNA', 'Badge in chat', 'Welcome!', 100)`);
  const fan = await db.newUser();
  await db.fund(fan, 'coins_100');
  const k = key();
  await db.call(fan, 'join_fan_club', [club.id, k]);
  const again = await db.call(fan, 'join_fan_club', [club.id, k]);
  assert.equal(again.duplicate, true);
  assert.equal(Number((await db.wallet(fan)).coins), 0);
  const detail = await db.call(fan, 'fan_club_detail', [club.id]);
  assert.equal(Number(detail.member_count), 1);
});

test('events: leaderboard counts only validated records inside the window', async () => {
  const admin = await db.newUser();
  await db.sql(`insert into staff_members (user_id, role) values ($1, 'admin')`, [admin]);
  const [ev] = await db.as(admin, `select * from staff_upsert_event($1::jsonb)`, [JSON.stringify({
    title: 'Meskel Stars', description: 'Celebrate Meskel', rules: 'Gifts received during the event count.',
    reward_description: 'Featured placement', metric: 'gift_coins_received',
    starts_at: new Date(Date.now() - 3600e3).toISOString(), ends_at: new Date(Date.now() + 3600e3).toISOString(), published: true })]);
  const host = await db.newUser();
  const room = await db.goLive(host);
  await db.as(host, 'select join_event($1)', [ev.id]);
  const fan = await db.newUser();
  await db.as(fan, 'select join_room($1)', [room]);
  await db.fund(fan, 'coins_100');
  await db.call(fan, 'send_gift', [room, host, 'injera', 1, key()]);
  const board = await db.as(fan, 'select * from event_leaderboard($1)', [ev.id]);
  assert.equal(board[0].user_id, host);
  assert.equal(Number(board[0].score), 50);
});

test('dashboard reads are staff-only and show hidden catalog items, config and adjustments', async () => {
  const nobody = await db.newUser();
  for (const q of ['select * from staff_config()', 'select staff_catalog()', 'select * from staff_events()', `select * from staff_adjustments('pending')`]) {
    await rejects(db.as(nobody, q), 'staff_only');
  }
  const admin = await db.newUser();
  const fin1 = await db.newUser();
  const fin2 = await db.newUser();
  await db.sql(`insert into staff_members (user_id, role) values ($1, 'admin'), ($2, 'finance'), ($3, 'finance')`, [admin, fin1, fin2]);
  await db.sql(`update gifts set available = false where id = 'lalibela'`);
  const catalog = await db.call(admin, 'staff_catalog');
  assert.ok(catalog.gifts.some((g) => g.id === 'lalibela' && g.available === false));
  const cfg = await db.as(admin, 'select * from staff_config()');
  assert.ok(cfg.some((c) => c.key === 'bean_to_etb_minor'));
  await rejects(db.as(admin, 'select * from staff_wallet_accounts($1)', [nobody]), 'insufficient_staff_role');

  const creator = await db.newUser();
  await db.fund(creator, 'coins_100');
  const accounts = await db.as(fin1, 'select * from staff_wallet_accounts($1)', [creator]);
  const coins = accounts.find((a) => a.kind === 'user_coins');
  const before = Number((await db.wallet(creator)).coins);
  const req = await db.call(fin1, 'staff_request_adjustment', [coins.id, 5, 'coin', 'Goodwill credit for failed stream'], { aal: 'aal2' });
  const pending = await db.as(fin2, `select * from staff_adjustments('pending')`);
  assert.ok(pending.some((a) => a.id === req && a.requested_by === fin1));
  await rejects(db.as(fin1, 'select staff_decide_adjustment($1, true)', [req], { aal: 'aal2' }), 'second_approver_must_differ');
  await db.as(fin2, 'select staff_decide_adjustment($1, true)', [req], { aal: 'aal2' });
  assert.equal(Number((await db.wallet(creator)).coins), before + 5);
  const applied = await db.as(fin2, `select * from staff_adjustments('applied')`);
  assert.ok(applied.some((a) => a.id === req));
});
