// Mirrors et.bizassistant.core.NumberGuard on the Android side.
const NUMBER = /\d[\d,]*(?:\.\d+)?/g;

export function normalize(raw) {
  let s = raw.replace(/,/g, '');
  if (s.includes('.')) s = s.replace(/0+$/, '').replace(/\.$/, '');
  s = s.replace(/^0+/, '') || '0';
  if (s.startsWith('.')) s = '0' + s;
  return s;
}

export function extractNumbers(text) {
  const out = new Set();
  for (const m of String(text).matchAll(NUMBER)) out.add(normalize(m[0].replace(/,$/, '')));
  return out;
}

/** Numbers in `output` that do not appear anywhere in `allowedTexts`. */
export function unsupportedNumbers(output, allowedTexts) {
  const allowed = new Set();
  for (const t of allowedTexts) for (const n of extractNumbers(t)) allowed.add(n);
  return [...extractNumbers(output)].filter((n) => !allowed.has(n)).sort();
}
