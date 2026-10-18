// Delivers queued notifications through Firebase Cloud Messaging (HTTP v1).
// Triggered by pg_cron/pg_net or any scheduler with header `x-cron-secret: $CRON_SECRET`.
// Each notification is claimed once (claim_push_batch marks it), so retries never double-send.
import { googleAccessToken, parseServiceAccount } from '../_shared/google.ts';
import { handler, HttpError, json, requireEnv, safeEqual, serviceClient } from '../_shared/http.ts';

const TITLES: Record<string, string> = {
  follow: 'New follower', live_start: 'Live now', gift: 'You received a gift', message: 'New message',
  pk_invite: 'PK battle invitation', guest_invite: 'You are invited on stage', moderation: 'Account notice',
  account: 'Ethio Live support', payout: 'Payout update', event: 'Event update',
};

function bodyFor(type: string, actor: string | null, payload: Record<string, unknown>): string {
  const who = actor ?? 'Someone';
  switch (type) {
    case 'follow': return `${who} started following you`;
    case 'live_start': return `${who} is live: ${payload.title ?? ''}`;
    case 'gift': return `${who} sent you ${payload.quantity ?? 1} gift(s)`;
    case 'message': return `${who}: ${payload.preview ?? ''}`;
    case 'pk_invite': return `${who} challenged you to a PK battle`;
    case 'guest_invite': return `${who} invited you to join their live`;
    case 'payout': return `Your payout is ${payload.status ?? 'updated'}`;
    default: return 'Open Ethio Live for details';
  }
}

Deno.serve(handler(async (req) => {
  if (!safeEqual(req.headers.get('x-cron-secret') ?? '', requireEnv('CRON_SECRET'))) throw new HttpError(401, 'unauthorized');
  const sa = parseServiceAccount(requireEnv('FIREBASE_SERVICE_ACCOUNT_JSON'));
  const projectId = Deno.env.get('FIREBASE_PROJECT_ID') ?? sa.project_id;
  const service = serviceClient();
  const { data: batch, error } = await service.rpc('claim_push_batch', { p_limit: 200 });
  if (error) throw new Error(error.message);
  const accessToken = await googleAccessToken(sa, 'https://www.googleapis.com/auth/firebase.messaging');

  let sent = 0;
  const dead: string[] = [];
  for (const n of batch ?? []) {
    for (const token of n.tokens ?? []) {
      const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: {
          token,
          notification: { title: TITLES[n.type] ?? 'Ethio Live', body: bodyFor(n.type, n.actor_name, n.payload ?? {}) },
          data: { type: n.type, notification_id: String(n.notification_id), payload: JSON.stringify(n.payload ?? {}) },
          android: { priority: n.type === 'message' || n.type === 'pk_invite' ? 'high' : 'normal',
                     notification: { channel_id: n.type === 'message' ? 'messages' : 'activity' } },
        } }),
      });
      if (res.ok) { sent++; continue; }
      const text = await res.text();
      if (res.status === 404 || text.includes('UNREGISTERED')) dead.push(token);
      else console.error('fcm error', res.status, text);
    }
  }
  if (dead.length) await service.from('push_tokens').delete().in('token', dead);
  return json({ claimed: batch?.length ?? 0, sent, removed_tokens: dead.length });
}));
