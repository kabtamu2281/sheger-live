// Deletes the caller's account: personal data is erased in SQL (delete_my_account), then the auth identity
// is soft-deleted so the phone/email can no longer sign in. Financial ledger rows are retained, pseudonymised.
import { dbError, handler, HttpError, json, requireUser, serviceClient, userClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  const user = await requireUser(req);
  const body = await req.json().catch(() => ({})) as { confirm?: string };
  if (body.confirm !== 'DELETE') throw new HttpError(400, 'confirmation_required');
  const { error } = await userClient(req).rpc('delete_my_account');
  if (error) return dbError(error);
  const { error: authError } = await serviceClient().auth.admin.deleteUser(user.id, true);
  if (authError) {
    console.error('auth soft delete failed', authError);
    return json({ deleted: true, auth_identity_removed: false });
  }
  return json({ deleted: true, auth_identity_removed: true });
}));
