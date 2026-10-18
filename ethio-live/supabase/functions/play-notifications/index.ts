// Google Play Real-time Developer Notifications (Pub/Sub push subscription).
// Configure the push endpoint as .../play-notifications?token=$PLAY_RTDN_TOKEN.
// Voided (refunded / charged back) one-time purchases reverse the coins they credited.
import { handler, HttpError, json, requireEnv, safeEqual, serviceClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  const url = new URL(req.url);
  if (!safeEqual(url.searchParams.get('token') ?? '', requireEnv('PLAY_RTDN_TOKEN'))) throw new HttpError(401, 'unauthorized');
  const envelope = await req.json().catch(() => null) as { message?: { data?: string; messageId?: string } } | null;
  if (!envelope?.message?.data) throw new HttpError(400, 'invalid_message');
  const note = JSON.parse(atob(envelope.message.data));
  const service = serviceClient();
  const { error: dupe } = await service.from('payment_webhook_events').insert({
    provider: 'google_play', event_ref: envelope.message.messageId ?? crypto.randomUUID(), signature_valid: true, payload: note });
  if (dupe?.code === '23505') return json({ ok: true, duplicate: true });

  const voided = note.voidedPurchaseNotification;
  if (voided?.purchaseToken) {
    const { data: order } = await service.from('payment_orders').select('id, status')
      .eq('provider', 'google_play').eq('provider_ref', voided.purchaseToken).maybeSingle();
    if (order?.status === 'paid') {
      const { data, error } = await service.rpc('refund_payment_order', { p_order: order.id, p_reason: 'play_voided' });
      if (error) console.error('refund failed', error);
      return json({ ok: true, refunded: data });
    }
  }
  return json({ ok: true });
}));
