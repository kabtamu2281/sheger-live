// Verifies a Google Play in-app purchase server-side, credits coins once, then consumes the purchase.
// POST { order_id, product_id, purchase_token } — the app sets obfuscatedAccountId = order_id when launching billing,
// so a purchase token can only ever credit the order it was bought for.
import { googleAccessToken, parseServiceAccount } from '../_shared/google.ts';
import { handler, HttpError, json, requireEnv, requireUser, serviceClient } from '../_shared/http.ts';

const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';

Deno.serve(handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  const packageName = requireEnv('ANDROID_PACKAGE_NAME');
  const sa = parseServiceAccount(requireEnv('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON'));
  const user = await requireUser(req);
  const body = await req.json().catch(() => ({})) as { order_id?: string; product_id?: string; purchase_token?: string };
  if (!body.order_id || !body.product_id || !body.purchase_token) throw new HttpError(400, 'missing_fields');

  const service = serviceClient();
  const { data: order } = await service.from('payment_orders').select('*, coin_packages(play_product_id)')
    .eq('id', body.order_id).single();
  if (!order || order.user_id !== user.id || order.provider !== 'google_play') throw new HttpError(404, 'order_not_found');
  if (order.coin_packages?.play_product_id !== body.product_id) throw new HttpError(400, 'product_mismatch');

  const token = await googleAccessToken(sa, SCOPE);
  const base = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/purchases/products/${encodeURIComponent(body.product_id)}/tokens/${encodeURIComponent(body.purchase_token)}`;
  const res = await fetch(base, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) return json({ error: 'purchase_not_found' }, 400);
  const purchase = await res.json() as { purchaseState: number; obfuscatedExternalAccountId?: string; consumptionState: number; priceAmountMicros?: string };
  if (purchase.purchaseState !== 0) return json({ error: 'purchase_not_completed', state: purchase.purchaseState }, 409);
  if (purchase.obfuscatedExternalAccountId !== order.id) return json({ error: 'purchase_order_mismatch' }, 409);

  // Google settles in the buyer's currency; the order amount is our list price, so pass it through as matched.
  const { data: credit, error } = await service.rpc('credit_payment_order', {
    p_order: order.id, p_provider_ref: body.purchase_token, p_amount_minor: order.amount_minor, p_currency: order.currency,
  });
  if (error) return json({ error: error.message }, 409);

  if (purchase.consumptionState === 0) {
    const consume = await fetch(`${base}:consume`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
    if (!consume.ok) console.error('consume failed', consume.status, await consume.text());
  }
  return json({ ok: true, ...credit });
}));
