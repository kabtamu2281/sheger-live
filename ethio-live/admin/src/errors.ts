const MESSAGES: Record<string, string> = {
  staff_only: 'This account is not a staff member.',
  insufficient_staff_role: 'Your role is not allowed to do this.',
  mfa_required: 'This action needs two-step verification. Verify with your authenticator app and try again.',
  cannot_review_own_action: 'A different moderator must decide appeals on your own actions.',
  cannot_review_own_payout: 'You cannot review your own payout.',
  second_approver_must_differ: 'A second, different finance approver is required.',
  cannot_change_own_role: 'You cannot change your own role.',
  cannot_moderate_staff: 'Only a superadmin can take action on staff accounts.',
  reason_required: 'Give a reason of at least 5 characters.',
  kyc_required: 'The creator must have verified identity (KYC) before approval.',
  payout_not_reviewable: 'This payout is no longer awaiting review.',
  payout_not_approved: 'Only approved payouts can be marked paid or failed.',
  adjustment_not_pending: 'This adjustment has already been decided.',
  user_accounts_only: 'Adjustments can only target user wallet accounts.',
  unknown_config_key: 'Unknown configuration key.',
  not_configured: 'Set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY in admin/.env.local.',
  'Failed to fetch': 'Could not reach the server.',
};

/** Turns a server error code (raised by Postgres functions) into a sentence for staff. */
export function describeError(e: unknown): string {
  const raw = e instanceof Error ? e.message : String(e);
  if (MESSAGES[raw]) return MESSAGES[raw];
  const code = raw.split(':')[0].trim();
  if (MESSAGES[code]) return MESSAGES[code];
  if (raw.includes('violates check constraint')) return `The value is not allowed (${raw}).`;
  return raw || 'Something went wrong.';
}

export function formatEtb(minor: number | null | undefined): string {
  if (minor == null) return '—';
  return 'ETB ' + (minor / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  return iso.replace('T', ' ').slice(0, 16);
}
