// Prices are placeholders (ETB) for the operator to set before launch.
export const PLANS = {
  free: { id: 'free', name: 'Free', monthlyAiDrafts: 20, monthlyPriceEtb: 0 },
  starter: { id: 'starter', name: 'Starter', monthlyAiDrafts: 200, monthlyPriceEtb: 149 },
  business: { id: 'business', name: 'Business', monthlyAiDrafts: 1000, monthlyPriceEtb: 449 },
};

export const BUNDLES = {
  bundle_100: { id: 'bundle_100', name: 'Business pack 100', extraAiDrafts: 100, priceEtb: 79 },
  bundle_500: { id: 'bundle_500', name: 'Business pack 500', extraAiDrafts: 500, priceEtb: 299 },
};

export function currentPeriod(now = new Date()) {
  return `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`;
}

export function nextResetDate(now = new Date()) {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1)).toISOString().slice(0, 10);
}

/** Rolls the monthly counter over if needed and returns the usage view sent to the app. */
export function usageView(device, now = new Date()) {
  const period = currentPeriod(now);
  if (device.period !== period) {
    device.period = period;
    device.used = 0;
  }
  const plan = PLANS[device.plan] ?? PLANS.free;
  const remaining = Math.max(plan.monthlyAiDrafts - device.used, 0) + device.bonus;
  return { plan: plan.id, used: device.used, limit: plan.monthlyAiDrafts, bonusRemaining: device.bonus, remaining, resetsOn: nextResetDate(now) };
}

export function consume(device) {
  const plan = PLANS[device.plan] ?? PLANS.free;
  if (device.used < plan.monthlyAiDrafts) device.used += 1;
  else if (device.bonus > 0) device.bonus -= 1;
  else return false;
  return true;
}
