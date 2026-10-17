// Allow-list of AI tasks and the only fields each may carry. Anything else is dropped.
// Kept in sync with et.bizassistant.core.TaskType on the Android side.
export const LANGUAGES = {
  en: 'English',
  am: 'Amharic (written in Ethiopic / Geʽez script)',
  om: 'Afaan Oromo (written in Qubee, the Latin-based script)',
};

export const TASKS = {
  customer_message: {
    fields: ['customer_name', 'purpose', 'details', 'tone'],
    required: ['customer_name', 'purpose', 'details'],
    instruction:
      'Write a short message the business owner can send to a customer by SMS, Telegram or WhatsApp. ' +
      'Use the purpose, tone and details given. Keep it under 120 words.',
  },
  product_description: {
    fields: ['product_name', 'features', 'price', 'audience'],
    required: ['product_name', 'features'],
    instruction:
      'Write an appealing product description for a shop listing or social media post, using only the features given. ' +
      'Mention the price only if one is given, exactly as written. Keep it under 120 words.',
  },
  translation: {
    fields: ['source_text', 'target_language'],
    required: ['source_text', 'target_language'],
    instruction:
      'Translate the source text into the target language faithfully. Keep names, amounts, dates and phone numbers exactly as written. ' +
      'Output only the translation.',
  },
  business_letter: {
    fields: ['recipient', 'letter_type', 'subject', 'details'],
    required: ['recipient', 'letter_type', 'subject', 'details'],
    instruction:
      'Write a polite, formal business letter from the business to the recipient about the subject, using only the details given. ' +
      'Include a subject line, greeting, body and closing signed with the business name.',
  },
  report: {
    fields: ['report_period', 'audience', 'details'],
    required: ['report_period'],
    instruction:
      'Write a short plain-language business report (under 200 words) explaining the figures provided in <facts>. ' +
      'Restate figures exactly as given; do not compute new totals, percentages, growth rates or forecasts. ' +
      'If there are no facts, say that no records exist for the period.',
  },
};

export const MAX_FIELD_CHARS = 4000;
export const MAX_FACTS = 20;

/** Validates and minimises a client request. Returns {ok, error?, task?, language?, inputs?, facts?}. */
export function sanitizeRequest(body) {
  if (!body || typeof body !== 'object') return { ok: false, error: 'invalid_body' };
  const task = TASKS[body.task];
  if (!task) return { ok: false, error: 'unknown_task' };
  if (!LANGUAGES[body.language]) return { ok: false, error: 'unsupported_language' };
  const rawInputs = body.inputs && typeof body.inputs === 'object' ? body.inputs : {};
  const inputs = {};
  for (const key of task.fields) {
    const v = rawInputs[key];
    if (typeof v === 'string' && v.trim()) inputs[key] = v.trim().slice(0, MAX_FIELD_CHARS);
  }
  const missing = task.required.filter((k) => !inputs[k]);
  if (missing.length) return { ok: false, error: 'missing_fields', missing };
  if (body.task === 'translation' && !LANGUAGES[inputs.target_language]) {
    return { ok: false, error: 'unsupported_language' };
  }
  const facts = {};
  if (body.task === 'report' && body.facts && typeof body.facts === 'object') {
    for (const [k, v] of Object.entries(body.facts).slice(0, MAX_FACTS)) {
      if (/^[a-z0-9_]{1,40}$/.test(k) && typeof v === 'string') facts[k] = v.slice(0, 200);
    }
  }
  const businessName = typeof body.businessName === 'string' ? body.businessName.trim().slice(0, 120) : '';
  return { ok: true, taskName: body.task, task, language: body.language, inputs, facts, businessName };
}
