import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createTestDb, rejects, key } from './harness.js';

let db;
before(async () => { db = await createTestDb('gifts'); });
after(async () => { await db.close(); });

async function liveRoomWithViewer() {
  const host = await db.newUser();
  const room = await db.goLive(host);
  const fan = await db.newUser();
  await db.as(fan, 'select join_room($1)', [room]);
  return { host, room, fan };
}

const gift = (uid, room, to, giftId, qty, k = key()) =>
  db.call(uid, 'send_gift', [room, to, giftId, qty, k]);

async function assertLedgerBalanced() {
  const bad = await db.sql(`select transaction_id, unit, sum(amount) s from ledger_entries group by 1, 2 having sum(amount) <> 0`);
  assert.equal(bad.length, 0, 'every ledger transaction balances');
  const neg = await db.sql(`select * from wallet_accounts where owner_id is not null and balance < 0`);
  assert.equal(neg.length, 0, 'no user balance is negative');
  // Stored balances equal the sum of their entries.
  const drift = await db.sql(`select a.id from wallet_accounts a left join ledger_entries e on e.account_id = a.id
                              where a.owner_id is not null group by a.id, a.balance having a.balance <> coalesce(sum(e.amount), 0)`);
  assert.equal(drift.length, 0, 'balances match the ledger');
}

test('purchases credit coins exactly once and reject amount mismatches', async () => {
  const u = await db.newUser();
  const [order] = await db.as(u, `select * from create_payment_order('coins_500', 'chapa')`);
  const ref = `chapa-${key()}`;
  await db.service('select credit_payment_order($1, $2, $3, $4)', [order.id, ref, order.amount_minor, 'ETB']);
  const dup = await db.service('select credit_payment_order($1, $2, $3, $4) as v', [order.id, ref, order.amount_minor, 'ETB']);
  assert.equal(dup[0].v.duplicate, true);
  const w = await db.wallet(u);
  assert.equal(Number(w.coins), 500);
  assert.equal(Number(w.promo_coins), 25);

  const [bad] = await db.as(u, `select * from create_payment_order('coins_100', 'chapa')`);
  const res = await db.service('select credit_payment_order($1, $2, $3, $4) as v', [bad.id, `chapa-${key()}`, 1, 'ETB']);
  assert.equal(res[0].v.credited, false);
  assert.equal(Number((await db.wallet(u)).coins), 500);

  // App users can never call the crediting function themselves.
  const [mine] = await db.as(u, `select * from create_payment_order('coins_100', 'chapa')`);
  await rejects(db.as(u, 'select credit_payment_order($1, $2, $3, $4)', [mine.id, 'x', mine.amount_minor, 'ETB']), 'permission denied');
  await assertLedgerBalanced();
});

test('younger users cannot buy coins or send paid gifts', async () => {
  const minor = await db.newUser({ adult: false });
  await rejects(db.as(minor, `select * from create_payment_order('coins_100', 'chapa')`), 'purchase_age_restricted');
  const { host, room } = await liveRoomWithViewer();
  await db.as(minor, 'select join_room($1)', [room]);
  await rejects(gift(minor, room, host, 'rose', 1), 'paid_gifts_age_restricted');
  const free = await gift(minor, room, host, 'adey', 1);
  assert.equal(free.coins_spent, 0);
});

test('sending a gift debits the sender, credits the creator, and replays never double-charge', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  await db.fund(fan, 'coins_100');
  const k = key();
  const first = await gift(fan, room, host, 'injera', 1, k);
  assert.equal(first.duplicate, false);
  assert.equal(Number(first.coins), 50);
  assert.equal(first.beans_earned, 25);
  const replay = await gift(fan, room, host, 'injera', 1, k);
  assert.equal(replay.duplicate, true);
  assert.equal(Number(replay.coins), 50);
  const hostWallet = await db.wallet(host);
  assert.equal(Number(hostWallet.beans_pending), 25);
  assert.equal(Number(hostWallet.beans), 0);
  await rejects(gift(fan, room, host, 'jebena', 1), 'insufficient_balance');
  await assertLedgerBalanced();
});

test('promotional coins are spent first and earn no withdrawable beans by default', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  await db.fund(fan, 'coins_500'); // 500 coins + 25 promo
  const r = await gift(fan, room, host, 'rose', 2); // 40 coins
  assert.equal(r.promo_coins_spent, 25);
  assert.equal(r.coins_spent, 15);
  assert.equal(r.beans_earned, 7); // floor(15 * 50%)
  await assertLedgerBalanced();
});

test('gift validation: self, blocked, not on stage, ended room, unknown gift', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  await db.fund(fan, 'coins_100');
  await rejects(gift(host, room, host, 'rose', 1), 'cannot_gift_self');
  const bystander = await db.newUser();
  await db.as(bystander, 'select join_room($1)', [room]);
  await rejects(gift(fan, room, bystander, 'rose', 1), 'recipient_not_on_stage');
  await rejects(gift(fan, room, host, 'no_such_gift', 1), 'gift_unavailable');
  await rejects(gift(fan, room, host, 'rose', 0), 'invalid_quantity');
  await rejects(db.call(fan, 'send_gift', [room, host, 'rose', 1, 'short']), 'idempotency_key_required');
  await db.as(host, 'select end_live($1)', [room]);
  await rejects(gift(fan, room, host, 'rose', 1), 'room_not_live');
});

test('free gifts are capped per day and never touch the ledger', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  const before2 = await db.sql('select count(*)::int n from ledger_entries');
  await gift(fan, room, host, 'clap', 20);
  await rejects(gift(fan, room, host, 'clap', 1), 'free_gift_limit');
  const after2 = await db.sql('select count(*)::int n from ledger_entries');
  assert.equal(after2[0].n, before2[0].n);
});

test('wallet stays correct under 25 concurrent gift requests', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  await db.fund(fan, 'coins_100'); // exactly 10 x 10-coin gifts
  const results = await Promise.allSettled(Array.from({ length: 25 }, () => gift(fan, room, host, 'buna', 1)));
  const ok = results.filter((r) => r.status === 'fulfilled');
  const failed = results.filter((r) => r.status === 'rejected');
  assert.equal(ok.length, 10);
  assert.ok(failed.every((r) => r.reason.message.includes('insufficient_balance')), failed.map((f) => f.reason.message).join(','));
  const w = await db.wallet(fan);
  assert.equal(Number(w.coins), 0);
  assert.equal(Number((await db.wallet(host)).beans_pending), 50);
  await assertLedgerBalanced();
});

test('the same idempotency key sent concurrently charges once', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  await db.fund(fan, 'coins_100');
  const k = key();
  const results = await Promise.allSettled(Array.from({ length: 8 }, () => gift(fan, room, host, 'rose', 1, k)));
  const charged = results.filter((r) => r.status === 'fulfilled' && r.value.duplicate === false);
  assert.equal(charged.length, 1);
  results.filter((r) => r.status === 'rejected').forEach((r) =>
    assert.ok(/duplicate_in_progress|could not serialize/.test(r.reason.message), r.reason.message));
  assert.equal(Number((await db.wallet(fan)).coins), 80);
  await assertLedgerBalanced();
});

test('ledger rows are immutable, even for the database owner', async () => {
  await rejects(db.sql('update ledger_entries set amount = amount + 1'), 'immutable');
  await rejects(db.sql('delete from ledger_transactions'), 'immutable');
  await rejects(db.sql('delete from audit_logs'), 'append-only');
});

test('settlement, KYC-gated payouts with two-person approval', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  for (let i = 0; i < 4; i++) await db.fund(fan, 'coins_6500'); // 26,000 coins + promo
  await db.sql(`update platform_config set value = '0' where key = 'promo_bean_share_bps'`);
  // Spend promo first so all remaining spending is purchased coins.
  await gift(fan, room, host, 'lalibela', 5); // 24,995 coins
  let w = await db.wallet(host);
  const pending = Number(w.beans_pending);
  assert.ok(pending > 0);
  await rejects(db.as(host, `select request_payout(20000, '{"type":"telebirr","phone":"0911"}')`), 'kyc_required');

  // Settlement releases beans only after the hold period.
  assert.equal(await db.service('select settle_due_beans(100) as v').then((r) => r[0].v), 0);
  await db.sql(`update bean_settlements set release_at = now() - interval '1 minute' where creator_id = $1`, [host]);
  await db.service('select settle_due_beans(100)');
  w = await db.wallet(host);
  assert.equal(Number(w.beans), pending);
  assert.equal(Number(w.beans_pending), 0);

  const fin1 = await db.newUser();
  const fin2 = await db.newUser();
  await db.sql(`insert into staff_members (user_id, role) values ($1, 'finance'), ($2, 'finance')`, [fin1, fin2]);
  await rejects(db.as(fin1, `select staff_set_kyc($1, 'verified', 'NID-1')`, [host]), 'mfa_required');
  await db.as(fin1, `select staff_set_kyc($1, 'verified', 'NID-1')`, [host], { aal: 'aal2' });
  await rejects(db.as(host, `select request_payout(100, '{"type":"telebirr"}')`), 'payout_below_minimum');
  await db.sql(`update platform_config set value = '10000' where key = 'payout_min_beans'`);
  await db.sql(`update platform_config set value = '5000' where key = 'payout_second_approval_beans'`);
  const [pr] = await db.as(host, `select * from request_payout(10000, '{"type":"telebirr","phone":"0911000000"}')`);
  await rejects(db.as(host, `select request_payout(10000, '{"type":"telebirr"}')`), 'payout_already_open');
  w = await db.wallet(host);
  assert.equal(Number(w.beans_in_payout), 10000);
  assert.equal(Number(pr.net_etb_minor), 392000); // 10000 beans -> ETB 4000.00 gross, 2% fee

  await db.as(fin1, 'select staff_review_payout($1, true, null)', [pr.id], { aal: 'aal2' });
  await rejects(db.as(fin1, 'select staff_review_payout($1, true, null)', [pr.id], { aal: 'aal2' }), 'second_approver_must_differ');
  await db.as(fin2, 'select staff_review_payout($1, true, null)', [pr.id], { aal: 'aal2' });
  await db.as(fin2, `select staff_mark_payout_paid($1, 'TB-123', true)`, [pr.id], { aal: 'aal2' });
  w = await db.wallet(host);
  assert.equal(Number(w.beans_in_payout), 0);
  const [final] = await db.sql('select status, first_approver, second_approver from payout_requests where id = $1', [pr.id]);
  assert.equal(final.status, 'paid');
  assert.notEqual(final.first_approver, final.second_approver);
  const audits = await db.sql(`select action from audit_logs where target_id = $1 order by id`, [pr.id]);
  assert.ok(audits.length >= 3);
  await assertLedgerBalanced();
});

test('ledger adjustments need a second finance approver', async () => {
  const u = await db.newUser();
  const fin1 = await db.newUser();
  const fin2 = await db.newUser();
  await db.sql(`insert into staff_members (user_id, role) values ($1, 'finance'), ($2, 'finance')`, [fin1, fin2]);
  const [{ id: acct }] = await db.sql(`select id from wallet_accounts where owner_id = $1 and kind = 'user_coins'`, [u]);
  const req = await db.call(fin1, 'staff_request_adjustment', [acct, 300, 'coin', 'Goodwill credit for failed stream ticket #42'], { aal: 'aal2' });
  await rejects(db.as(fin1, 'select staff_decide_adjustment($1, true)', [req], { aal: 'aal2' }), 'second_approver_must_differ');
  await db.as(fin2, 'select staff_decide_adjustment($1, true)', [req], { aal: 'aal2' });
  assert.equal(Number((await db.wallet(u)).coins), 300);
  await assertLedgerBalanced();
});

test('refunds claw back unspent coins and record shortfalls', async () => {
  const { host, room, fan } = await liveRoomWithViewer();
  const order = await db.fund(fan, 'coins_100');
  await gift(fan, room, host, 'injera', 1);
  const res = await db.service('select refund_payment_order($1, $2) as v', [order.id, 'chargeback']);
  assert.equal(res[0].v.refunded_coins, 50);
  assert.equal(res[0].v.shortfall, 50);
  assert.equal(Number((await db.wallet(fan)).coins), 0);
  await assertLedgerBalanced();
});
