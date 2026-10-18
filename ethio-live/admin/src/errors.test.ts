import { describe, expect, it } from 'vitest';
import { describeError, formatEtb, formatDate } from './errors';
import { canSee, sectionsFor } from './roles';

describe('errors', () => {
  it('maps server codes to sentences', () => {
    expect(describeError(new Error('mfa_required'))).toMatch(/two-step/);
    expect(describeError(new Error('user_not_found: sign up first'))).toBe('user_not_found: sign up first');
    expect(describeError(new Error('insufficient_staff_role'))).toMatch(/role/);
  });
  it('formats money and dates', () => {
    expect(formatEtb(392000)).toBe('ETB 3,920.00');
    expect(formatEtb(null)).toBe('—');
    expect(formatDate('2026-10-17T09:30:12.123Z')).toBe('2026-10-17 09:30');
  });
});

describe('roles', () => {
  it('limits sections by role, superadmin sees everything', () => {
    expect(canSee('support', 'payouts')).toBe(false);
    expect(canSee('finance', 'payouts')).toBe(true);
    expect(canSee('moderator', 'staff')).toBe(false);
    expect(canSee(null, 'overview')).toBe(false);
    expect(sectionsFor('superadmin').length).toBe(15);
  });
});
