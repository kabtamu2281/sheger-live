export type StaffRole = 'support' | 'moderator' | 'finance' | 'admin' | 'superadmin';

export type Section =
  | 'overview' | 'users' | 'live' | 'reports' | 'appeals' | 'support' | 'catalog' | 'config'
  | 'events' | 'rankings' | 'payments' | 'payouts' | 'adjustments' | 'staff' | 'audit';

// Mirrors the require_staff() checks in supabase/migrations/*_staff*.sql. The server is the authority;
// this only hides screens a role would get "insufficient_staff_role" on.
const ACCESS: Record<Section, StaffRole[]> = {
  overview: ['support', 'moderator', 'finance', 'admin'],
  users: ['support', 'moderator', 'finance', 'admin'],
  live: ['support', 'moderator', 'admin'],
  reports: ['support', 'moderator', 'admin'],
  appeals: ['moderator', 'admin'],
  support: ['support', 'admin'],
  catalog: ['admin', 'finance'],
  config: ['admin', 'finance'],
  events: ['admin', 'moderator'],
  rankings: ['support', 'moderator', 'finance', 'admin'],
  payments: ['finance', 'support'],
  payouts: ['finance'],
  adjustments: ['finance'],
  staff: ['admin'],
  audit: ['admin', 'finance'],
};

export const SECTION_LABELS: Record<Section, string> = {
  overview: 'Overview', users: 'Users', live: 'Live rooms', reports: 'Reports', appeals: 'Appeals',
  support: 'Support', catalog: 'Gifts & coins', config: 'Configuration', events: 'Events', rankings: 'Rankings',
  payments: 'Payments', payouts: 'Payouts', adjustments: 'Ledger adjustments', staff: 'Staff', audit: 'Audit log',
};

export function canSee(role: StaffRole | null, section: Section): boolean {
  if (!role) return false;
  return role === 'superadmin' || ACCESS[section].includes(role);
}

export function sectionsFor(role: StaffRole | null): Section[] {
  return (Object.keys(ACCESS) as Section[]).filter((s) => canSee(role, s));
}

/** Config keys that the server only lets finance change with MFA (see staff_set_config). */
export const ECONOMIC_KEYS = new Set([
  'creator_bean_share_bps', 'promo_bean_share_bps', 'bean_to_etb_minor', 'payout_fee_bps', 'payout_min_beans',
  'payout_second_approval_beans', 'settlement_hold_days', 'pk_points_per_coin',
]);
