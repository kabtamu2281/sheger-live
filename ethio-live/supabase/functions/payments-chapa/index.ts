// Starts a Chapa checkout for a coin package (Ethiopian birr). Coins are credited only by chapa-webhook,
// after Chapa's verify API confirms the payment; this function never credits anything itself.
// Chapa merchant approval for the business is required before enabling this in production.
import { dbError, handler, HttpError, json, requireEnv, requireUser, serviceClient, userClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  const secret = requireEnv('CHAPA_SECRET_KEY');
  const webhookUrl = requireEnv('CHAPA_CALLBACK_URL');
  const returnUrl = Deno.env.get('CHAPA_RETURN_URL') ?? 'https://ethiolive.app/payment-complete';
  const user = await requireUser(req);
  const body = await req.json().catch(() => ({})) as { package_id?: string };
  if (!body.package_id) throw new HttpError(400, 'package_id_required');

  const { data: order, error } = await userClient(req).rpc('create_payment_order', { p_package: body.package_id, p_provider: 'chapa' });
  if (error) return dbError(error);

  const res = await fetch('https://api.chapa.co/v1/transaction/initialize', {
    method: 'POST',
    headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      amount: (order.amount_minor / 100).toFixed(2),
      currency: order.currency,
      email: user.email ?? undefined,
      tx_ref: order.id,
      callback_url: webhookUrl,
      return_url: `${returnUrl}?order=${order.id}`,
      customization: { title: 'Ethio Live coins', description: `${order.coins} coins` },
    }),
  });
  const payload = await res.json().catch(() => ({}));
  const service = serviceClient();
  if (!res.ok || payload.status !== 'success') {
    await service.rpc('fail_payment_order', { p_order: order.id, p_reason: `init_failed:${res.status}` });
    return json({ error: 'payment_provider_unavailable' }, 502);
  }
  const checkoutUrl = payload.data?.checkout_url as string;
  await service.from('payment_orders').update({ status: 'pending', checkout_url: checkoutUrl }).eq('id', order.id);
  return json({ order_id: order.id, checkout_url: checkoutUrl, amount_minor: order.amount_minor, currency: order.currency });
}));
