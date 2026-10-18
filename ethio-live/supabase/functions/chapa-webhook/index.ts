// Chapa payment webhook. The signature is checked, the event is stored once, and the payment is always
// re-verified with Chapa's verify API before coins are credited (amount and currency must match the order).
import { handler, HttpError, hmacSha256Hex, json, requireEnv, safeEqual, serviceClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  const secret = requireEnv('CHAPA_SECRET_KEY');
  const webhookSecret = requireEnv('CHAPA_WEBHOOK_SECRET');
  const raw = await req.text();
  const signature = req.headers.get('x-chapa-signature') ?? req.headers.get('chapa-signature') ?? '';
  const expected = await hmacSha256Hex(webhookSecret, raw);
  const valid = signature.length > 0 && safeEqual(signature.toLowerCase(), expected);

  let event: Record<string, unknown> = {};
  try { event = JSON.parse(raw); } catch { throw new HttpError(400, 'invalid_json'); }
  const txRef = String(event.tx_ref ?? event.trx_ref ?? '');
  const service = serviceClient();
  const eventRef = `${txRef}:${event.status ?? ''}:${event.reference ?? ''}`;
  const { error: insertError } = await service.from('payment_webhook_events')
    .insert({ provider: 'chapa', event_ref: eventRef, signature_valid: valid, payload: event });
  const duplicate = insertError?.code === '23505';
  if (!valid) throw new HttpError(401, 'invalid_signature');
  if (duplicate) return json({ ok: true, duplicate: true });
  if (!/^[0-9a-f-]{36}$/.test(txRef)) throw new HttpError(400, 'unknown_tx_ref');

  const verify = await fetch(`https://api.chapa.co/v1/transaction/verify/${encodeURIComponent(txRef)}`, {
    headers: { Authorization: `Bearer ${secret}` },
  });
  const v = await verify.json().catch(() => ({}));
  if (!verify.ok || v.status !== 'success') return json({ ok: false, reason: 'verify_failed' }, 202);
  const data = v.data ?? {};
  if (data.status !== 'success') {
    if (data.status === 'failed') await service.rpc('fail_payment_order', { p_order: txRef, p_reason: 'provider_failed' });
    return json({ ok: true, credited: false, status: data.status });
  }
  const amountMinor = Math.round(Number(data.amount) * 100);
  const { data: credit, error } = await service.rpc('credit_payment_order', {
    p_order: txRef, p_provider_ref: String(data.reference ?? txRef), p_amount_minor: amountMinor, p_currency: String(data.currency ?? ''),
  });
  if (error) {
    console.error('credit failed', error);
    return json({ ok: false, error: error.message }, 409);
  }
  return json({ ok: true, ...credit });
}));
