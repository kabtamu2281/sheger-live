import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createTestDb, rejects } from './harness.js';

let db;
before(async () => { db = await createTestDb('live'); });
after(async () => { await db.close(); });

const discover = async (uid, tab = 'popular') => (await db.as(uid, `select discover_rooms($1) as v`, [tab]))[0].v;

test('a room is only discoverable after the stream is confirmed, and one live room per host', async () => {
  const host = await db.newUser();
  const viewer = await db.newUser();
  const [room] = await db.as(host, `select * from start_live('video', 'Kaldi coffee talk', 'chat', 'am', 'public', '{#buna,#Addis}', 4, null)`);
  assert.equal(room.status, 'preparing');
  assert.equal((await discover(viewer)).filter((r) => r.id === room.id).length, 0);
  await rejects(db.as(viewer, 'select join_room($1)', [room.id]), 'room_not_live');

  await db.as(host, 'select confirm_live($1)', [room.id]);
  const list = await discover(viewer);
  assert.equal(list.filter((r) => r.id === room.id).length, 1);
  assert.deepEqual(list.find((r) => r.id === room.id).hashtags.sort(), ['addis', 'buna']);
  await rejects(db.as(host, `select * from start_live('video', 'again', 'chat', 'am', 'public', '{}', 4, null)`), 'already_live');

  const found = (await db.as(viewer, `select search_rooms('#buna') as v`))[0].v;
  assert.equal(found.length, 1);
});

test('a failed publish never marks the room live', async () => {
  const host = await db.newUser();
  const [room] = await db.as(host, `select * from start_live('audio', 'Mic check', 'music', 'en', 'public', '{}', 9, null)`);
  await db.as(host, `select fail_live($1, 'sdk_error_110')`, [room.id]);
  const [r] = await db.sql('select status from live_rooms where id = $1', [room.id]);
  assert.equal(r.status, 'failed');
  await rejects(db.as(host, 'select confirm_live($1)', [room.id]), 'room_not_preparing');
});

test('only adults can broadcast and broadcast bans are enforced', async () => {
  const minor = await db.newUser({ adult: false });
  await rejects(db.as(minor, `select * from start_live('video', 'hi', 'chat', 'en', 'public', '{}', 4, null)`), 'broadcast_age_restricted');
  const banned = await db.newUser();
  await db.sql(`update profiles set broadcast_banned_until = now() + interval '1 day' where id = $1`, [banned]);
  await rejects(db.as(banned, `select * from start_live('video', 'hi', 'chat', 'en', 'public', '{}', 4, null)`), 'broadcast_banned');
});

test('viewer counts are real presence, not client-supplied', async () => {
  const host = await db.newUser();
  const room = await db.goLive(host);
  const viewers = [await db.newUser(), await db.newUser(), await db.newUser()];
  for (const v of viewers) await db.as(v, 'select join_room($1)', [room]);
  let [r] = await db.sql('select viewer_count from live_rooms where id = $1', [room]);
  assert.equal(r.viewer_count, 3);
  await db.as(viewers[0], 'select leave_room($1)', [room]);
  // A viewer whose app stopped sending heartbeats drops out of the count.
  await db.sql(`update room_participants set last_seen_at = now() - interval '5 minutes' where room_id = $1 and user_id = $2`, [room, viewers[1]]);
  await db.as(host, 'select room_heartbeat($1)', [room]);
  [r] = await db.sql('select viewer_count, peak_viewers from live_rooms where id = $1', [room]);
  assert.equal(r.viewer_count, 1);
  assert.equal(r.peak_viewers, 3);
});

test('chat: participants only, length and rate limits, room mutes, moderator removal', async () => {
  const host = await db.newUser();
  const room = await db.goLive(host);
  const v = await db.newUser();
  await rejects(db.as(v, 'select send_room_message($1, $2)', [room, 'hi']), 'not_in_room');
  await db.as(v, 'select join_room($1)', [room]);
  const id = await db.call(v, 'send_room_message', [room, '  Selam all  ']);
  await rejects(db.as(v, 'select send_room_message($1, $2)', [room, 'x'.repeat(301)]), 'message_too_long');
  await rejects(db.as(v, 'select send_room_message($1, $2)', [room, '   ']), 'message_empty');
  for (let i = 0; i < 4; i++) await db.as(v, 'select send_room_message($1, $2)', [room, `m${i}`]);
  await rejects(db.as(v, 'select send_room_message($1, $2)', [room, 'flood']), 'rate_limited');

  const other = await db.newUser();
  await db.as(other, 'select join_room($1)', [room]);
  await rejects(db.as(other, 'select remove_room_message($1)', [id]), 'not_allowed');
  await db.as(host, 'select remove_room_message($1)', [id]);
  const visible = await db.as(other, 'select id from room_messages where id = $1', [id]);
  assert.equal(visible.length, 0);

  await db.as(host, 'select mute_in_room($1, $2, 10)', [room, other]);
  await rejects(db.as(other, 'select send_room_message($1, $2)', [room, 'hello']), 'muted_in_room');
  const snap = await db.call(other, 'room_snapshot', [room]);
  assert.ok(snap.messages.every((m) => m.id !== id));
});

test('guest seats: request, accept, invite, capacity, host mute, kick', async () => {
  const host = await db.newUser();
  const room = await db.goLive(host, { mode: 'multi_guest', seats: 2 });
  const g1 = await db.newUser();
  const g2 = await db.newUser();
  for (const g of [g1, g2]) await db.as(g, 'select join_room($1)', [room]);

  const req = await db.call(g1, 'request_seat', [room]);
  await rejects(db.as(g1, 'select request_seat($1)', [room]), 'request_already_pending');
  await rejects(db.as(g2, 'select respond_seat_request($1, true)', [req]), 'not_room_manager');
  const seat = await db.call(host, 'respond_seat_request', [req, true]);
  assert.equal(seat, 1);

  const inv = await db.call(host, 'invite_to_seat', [room, g2]);
  await rejects(db.as(g2, 'select respond_seat_invite($1, true)', [inv]), 'no_free_seat');

  await db.as(host, 'select host_set_guest_mute($1, $2, true)', [room, g1]);
  await db.as(g1, 'select set_media_state($1, false, false)', [room]);
  let [p] = await db.sql('select mic_muted from room_participants where room_id = $1 and user_id = $2', [room, g1]);
  assert.equal(p.mic_muted, true, 'a guest cannot unmute while muted by the host');

  const auth = await db.call(g1, 'authorize_stream', [room]);
  assert.equal(auth.publisher, true);
  const auth2 = await db.call(g2, 'authorize_stream', [room]);
  assert.equal(auth2.publisher, false);

  await db.as(host, 'select remove_guest($1, $2, true)', [room, g1]);
  await rejects(db.as(g1, 'select join_room($1)', [room]), 'removed_from_room');
  await rejects(db.as(g1, 'select authorize_stream($1)', [room]), 'not_in_room');

  // The earlier invitation is still pending; now that a seat is free it can be accepted.
  assert.equal(await db.call(g2, 'respond_seat_invite', [inv, true]), 1);
});

test('blocked users cannot join and lose access when blocked mid-stream', async () => {
  const host = await db.newUser();
  const room = await db.goLive(host);
  const troll = await db.newUser();
  await db.as(troll, 'select join_room($1)', [room]);
  await db.as(host, 'select block_user($1)', [troll]);
  await rejects(db.as(troll, 'select send_room_message($1, $2)', [room, 'x']), 'not_in_room');
  await rejects(db.as(troll, 'select join_room($1)', [room]), 'room_not_found');
  assert.equal((await discover(troll)).filter((r) => r.id === room).length, 0);
});

test('followers-only rooms', async () => {
  const host = await db.newUser();
  const [room] = await db.as(host, `select * from start_live('video', 'Close friends', 'chat', 'en', 'followers', '{}', 4, null)`);
  await db.as(host, 'select confirm_live($1)', [room.id]);
  const fan = await db.newUser();
  const stranger = await db.newUser();
  await db.as(fan, 'select follow_user($1)', [host]);
  await db.as(fan, 'select join_room($1)', [room.id]);
  await rejects(db.as(stranger, 'select join_room($1)', [room.id]), 'followers_only');
});

test('ending a stream closes the session, frees seats and notifies nobody falsely', async () => {
  const host = await db.newUser();
  const fan = await db.newUser();
  await db.as(fan, 'select follow_user($1)', [host]);
  const room = await db.goLive(host);
  const notes = await db.as(fan, `select * from notifications where type = 'live_start'`);
  assert.equal(notes.length, 1);
  await db.as(fan, 'select join_room($1)', [room]);
  await db.as(host, 'select end_live($1)', [room]);
  const [r] = await db.sql('select status, viewer_count from live_rooms where id = $1', [room]);
  assert.equal(r.status, 'ended');
  const [s] = await db.sql('select ended_at, duration_seconds from stream_sessions where room_id = $1', [room]);
  assert.ok(s.ended_at);
  const active = await db.sql('select * from room_participants where room_id = $1 and left_at is null', [room]);
  assert.equal(active.length, 0);
  const badge = await db.sql(`select * from user_badges where user_id = $1 and badge_id = 'first_live'`, [host]);
  assert.equal(badge.length, 1);
});

test('maintenance ends rooms whose host vanished and fails rooms that never published', async () => {
  const host = await db.newUser();
  const room = await db.goLive(host);
  const host2 = await db.newUser();
  const [stuck] = await db.as(host2, `select * from start_live('video', 'stuck', 'chat', 'en', 'public', '{}', 4, null)`);
  await db.sql(`update live_rooms set last_heartbeat_at = now() - interval '5 minutes' where id = $1`, [room]);
  await db.sql(`update live_rooms set created_at = now() - interval '5 minutes' where id = $1`, [stuck.id]);
  const res = await db.service('select run_maintenance() as v');
  assert.ok(res[0].v.rooms_ended >= 1);
  const rows = await db.sql('select id, status, end_reason from live_rooms where id = any($1)', [[room, stuck.id]]);
  assert.equal(rows.find((r) => r.id === room).status, 'ended');
  assert.equal(rows.find((r) => r.id === stuck.id).status, 'failed');
});
