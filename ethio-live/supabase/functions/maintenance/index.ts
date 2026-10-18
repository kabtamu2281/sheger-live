// Fallback scheduler for projects without pg_cron: call every 20-60 s from any scheduler with
// header `x-cron-secret: $CRON_SECRET`. Ends dead rooms, frees stale seats, closes PK matches, settles beans.
import { handler, HttpError, json, requireEnv, safeEqual, serviceClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  if (!safeEqual(req.headers.get('x-cron-secret') ?? '', requireEnv('CRON_SECRET'))) throw new HttpError(401, 'unauthorized');
  const { data, error } = await serviceClient().rpc('run_maintenance');
  if (error) throw new Error(error.message);
  return json(data);
}));
