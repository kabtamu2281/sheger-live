import { ProviderError } from './aiProvider.js';
import { BUNDLES, PLANS, consume, usageView } from './plans.js';
import { buildUserMessage } from './prompts.js';
import { sanitizeRequest } from './tasks.js';
import { unsupportedNumbers } from './numberGuard.js';

const MAX_BODY_BYTES = 32 * 1024;

/**
 * Creates the HTTP request handler.
 * @param {{store: import('./store.js').Store, provider: {generate(msg: string): Promise<string>} | null,
 *          paymentProvider?: string, log?: (event: object) => void, now?: () => Date}} deps
 */
export function createApp({ store, provider, paymentProvider = '', log = () => {}, now = () => new Date() }) {
  const routes = {
    'GET /health': () => [200, { ok: true, ai: Boolean(provider) }],
    'GET /v1/plans': () => [200, { plans: Object.values(PLANS), bundles: Object.values(BUNDLES), paymentsConfigured: Boolean(paymentProvider) }],
    'POST /v1/devices': () => [201, { token: store.createDevice() }],
    'GET /v1/usage': (req, ctx) => [200, usageView(ctx.device, now())],
    'DELETE /v1/devices/me': (req, ctx) => {
      store.deleteDevice(ctx.token);
      return [200, { deleted: true }];
    },
    'POST /v1/subscriptions/checkout': async (req, ctx, body) => {
      if (!paymentProvider) {
        return [501, { error: 'payments_not_configured', message: 'No payment provider is configured. Nothing was charged.' }];
      }
      // A real integration (e.g. Telebirr or Chapa) creates a checkout session here and activates the plan
      // only from the provider's verified webhook – never from the app's say-so.
      return [501, { error: 'payments_not_implemented' }];
    },
    'POST /v1/ai/generate': async (req, ctx, body) => {
      // Two independent signals of consent: an explicit header and a body flag. Both are required.
      if (req.headers['x-ai-consent'] !== 'granted' || body?.consent !== true) {
        return [403, { error: 'consent_required' }];
      }
      const clean = sanitizeRequest(body);
      if (!clean.ok) return [400, { error: clean.error, missing: clean.missing }];

      const usage = usageView(ctx.device, now());
      if (usage.remaining <= 0) return [429, { error: 'quota_exceeded', usage }];
      if (!provider) return [503, { error: 'ai_unavailable', fallback: 'template', usage }];

      let text;
      const started = Date.now();
      try {
        text = await provider.generate(buildUserMessage(clean));
      } catch (err) {
        const kind = err instanceof ProviderError ? err.kind : 'unavailable';
        // Log the failure class only – never the user's content.
        log({ event: 'ai_error', task: clean.taskName, kind, detail: err.message, ms: Date.now() - started });
        return [kind === 'rejected' ? 422 : 503, { error: kind === 'rejected' ? 'ai_rejected' : 'ai_unavailable', fallback: 'template', usage }];
      }

      const allowed = [...Object.values(clean.inputs), ...Object.values(clean.facts), clean.businessName];
      const unverified = unsupportedNumbers(text, allowed);
      consume(ctx.device);
      store.save();
      log({ event: 'ai_ok', task: clean.taskName, language: clean.language, ms: Date.now() - started, unverified: unverified.length });
      return [200, { text, unverifiedNumbers: unverified, usage: usageView(ctx.device, now()) }];
    },
  };

  const PUBLIC = new Set(['GET /health', 'GET /v1/plans', 'POST /v1/devices']);

  return async function handler(req, res) {
    const path = new URL(req.url, 'http://localhost').pathname;
    const key = `${req.method} ${path}`;
    const route = routes[key];
    const send = (status, payload) => {
      res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
      res.end(JSON.stringify(payload));
    };
    if (!route) return send(404, { error: 'not_found' });

    const ctx = {};
    if (!PUBLIC.has(key)) {
      const auth = req.headers.authorization ?? '';
      ctx.token = auth.startsWith('Bearer ') ? auth.slice(7) : '';
      ctx.device = store.device(ctx.token);
      if (!ctx.device) return send(401, { error: 'unauthorized' });
    }

    let body;
    if (req.method === 'POST') {
      try {
        body = await readJson(req);
      } catch (e) {
        return send(e.message === 'too_large' ? 413 : 400, { error: e.message });
      }
    }
    try {
      const [status, payload] = await route(req, ctx, body);
      send(status, payload);
    } catch (err) {
      log({ event: 'internal_error', route: key, detail: err.message });
      send(500, { error: 'internal' });
    }
  };
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY_BYTES) {
        reject(new Error('too_large'));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        reject(new Error('invalid_json'));
      }
    });
    req.on('error', reject);
  });
}
