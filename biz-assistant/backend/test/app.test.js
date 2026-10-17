import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { after, before, beforeEach, describe, it } from 'node:test';
import Anthropic from '@anthropic-ai/sdk';
import { AnthropicProvider, ProviderError } from '../src/aiProvider.js';
import { createApp } from '../src/app.js';
import { Store } from '../src/store.js';
import { unsupportedNumbers } from '../src/numberGuard.js';

const SECRET = 'sk-ant-test-secret-should-never-leak';

class FakeProvider {
  constructor() {
    this.calls = [];
    this.reply = 'Dear Almaz, a reminder that ETB 1,250.50 is due.';
    this.error = null;
  }
  async generate(msg) {
    this.calls.push(msg);
    if (this.error) throw this.error;
    return this.reply;
  }
}

let server, base, store, provider, logs;

async function call(method, path, { token, body, headers = {} } = {}) {
  const res = await fetch(base + path, {
    method,
    headers: {
      'content-type': 'application/json',
      ...(token ? { authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { status: res.status, json: JSON.parse(text), raw: text };
}

const goodBody = {
  consent: true,
  task: 'customer_message',
  language: 'en',
  businessName: 'Sheger Cafe',
  inputs: { customer_name: 'Almaz', purpose: 'payment_reminder', details: 'ETB 1,250.50 is due on 2026-10-30', unexpected: 'drop me' },
};
const consentHeader = { 'x-ai-consent': 'granted' };

before(async () => {
  store = new Store(null);
  provider = new FakeProvider();
  logs = [];
  server = createServer(createApp({ store, provider, log: (e) => logs.push(e) }));
  await new Promise((r) => server.listen(0, r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => server.close());
beforeEach(() => {
  provider.calls = [];
  provider.error = null;
  provider.reply = 'Dear Almaz, a reminder that ETB 1,250.50 is due.';
  logs.length = 0;
});

async function newToken() {
  return (await call('POST', '/v1/devices')).json.token;
}

describe('authorization and consent', () => {
  it('rejects requests without a device token', async () => {
    const r = await call('POST', '/v1/ai/generate', { body: goodBody, headers: consentHeader });
    assert.equal(r.status, 401);
    assert.equal(provider.calls.length, 0);
  });

  it('never forwards content without both consent signals', async () => {
    const token = await newToken();
    const noHeader = await call('POST', '/v1/ai/generate', { token, body: goodBody });
    const noFlag = await call('POST', '/v1/ai/generate', { token, body: { ...goodBody, consent: false }, headers: consentHeader });
    const stringFlag = await call('POST', '/v1/ai/generate', { token, body: { ...goodBody, consent: 'true' }, headers: consentHeader });
    for (const r of [noHeader, noFlag, stringFlag]) assert.equal(r.status, 403);
    assert.equal(provider.calls.length, 0);
  });

  it('forwards only allow-listed fields and never logs content', async () => {
    const token = await newToken();
    const r = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    assert.equal(r.status, 200);
    assert.equal(r.json.text, provider.reply);
    assert.deepEqual(r.json.unverifiedNumbers, []);
    assert.equal(provider.calls.length, 1);
    assert.ok(provider.calls[0].includes('Almaz'));
    assert.ok(!provider.calls[0].includes('drop me'));
    const logged = JSON.stringify(logs);
    assert.ok(!logged.includes('Almaz') && !logged.includes('1,250.50'));
  });

  it('validates task, language and required fields before calling the AI', async () => {
    const token = await newToken();
    const cases = [
      [{ ...goodBody, task: 'invoice' }, 'unknown_task'],
      [{ ...goodBody, language: 'fr' }, 'unsupported_language'],
      [{ ...goodBody, inputs: { customer_name: 'Almaz' } }, 'missing_fields'],
    ];
    for (const [body, error] of cases) {
      const r = await call('POST', '/v1/ai/generate', { token, body, headers: consentHeader });
      assert.equal(r.status, 400);
      assert.equal(r.json.error, error);
    }
    assert.equal(provider.calls.length, 0);
  });
});

describe('quota', () => {
  it('counts successful drafts and blocks when the allowance is used up', async () => {
    const token = await newToken();
    const u0 = await call('GET', '/v1/usage', { token });
    assert.deepEqual([u0.json.plan, u0.json.used, u0.json.limit], ['free', 0, 20]);
    for (let i = 0; i < 20; i++) {
      const r = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
      assert.equal(r.status, 200);
    }
    const blocked = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    assert.equal(blocked.status, 429);
    assert.equal(blocked.json.usage.remaining, 0);
    assert.equal(provider.calls.length, 20);
  });

  it('does not charge the allowance when the AI fails', async () => {
    const token = await newToken();
    provider.error = new ProviderError('unavailable', 'timeout');
    const r = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    assert.equal(r.status, 503);
    assert.equal(r.json.fallback, 'template');
    assert.equal(r.json.usage.used, 0);
  });
});

describe('AI output safety', () => {
  it('reports numbers the AI invented', async () => {
    const token = await newToken();
    provider.reply = 'Dear Almaz, please pay ETB 1,437.08 including 15% VAT.';
    const r = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    assert.deepEqual(r.json.unverifiedNumbers, ['1437.08', '15']);
  });

  it('maps refusals to a template fallback', async () => {
    const token = await newToken();
    provider.error = new ProviderError('rejected', 'declined');
    const r = await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    assert.equal(r.status, 422);
    assert.equal(r.json.fallback, 'template');
  });

  it('number guard normalises formats', () => {
    assert.deepEqual(unsupportedNumbers('Total ETB 1250.5 on 2026-10-30', ['1,250.50', '2026-10-30']), []);
  });
});

describe('privacy, plans and payments', () => {
  it('lets a device delete its server-side data', async () => {
    const token = await newToken();
    assert.equal((await call('DELETE', '/v1/devices/me', { token })).status, 200);
    assert.equal((await call('GET', '/v1/usage', { token })).status, 401);
  });

  it('publishes plans and refuses checkout until payments are configured', async () => {
    const plans = await call('GET', '/v1/plans');
    assert.equal(plans.json.paymentsConfigured, false);
    assert.equal(plans.json.plans.length, 3);
    const token = await newToken();
    const r = await call('POST', '/v1/subscriptions/checkout', { token, body: { plan: 'starter' } });
    assert.equal(r.status, 501);
  });

  it('stores only token hashes and counters, never content', async () => {
    const token = await newToken();
    await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader });
    const dump = JSON.stringify(store.data);
    assert.ok(!dump.includes(token));
    assert.ok(!dump.includes('Almaz'));
  });
});

describe('AnthropicProvider', () => {
  function fakeClient(impl) {
    return { beta: { messages: { create: impl } } };
  }

  it('sends the frozen system prompt and returns text', async () => {
    let params;
    const p = new AnthropicProvider({
      model: 'claude-opus-5-5',
      client: fakeClient(async (x) => {
        params = x;
        return { stop_reason: 'end_turn', content: [{ type: 'text', text: ' Selam ' }] };
      }),
    });
    assert.equal(await p.generate('hi'), 'Selam');
    assert.equal(params.model, 'claude-opus-5-5');
    assert.equal(params.fallbacks, 'default');
    assert.ok(params.system[0].text.includes('Never invent or change numbers'));
  });

  it('turns refusals and SDK errors into ProviderError', async () => {
    const refused = new AnthropicProvider({ model: 'm', client: fakeClient(async () => ({ stop_reason: 'refusal', content: [] })) });
    await assert.rejects(refused.generate('x'), (e) => e instanceof ProviderError && e.kind === 'rejected');
    const offline = new AnthropicProvider({
      model: 'm',
      client: fakeClient(async () => {
        throw new Anthropic.APIConnectionError({ message: 'down' });
      }),
    });
    await assert.rejects(offline.generate('x'), (e) => e instanceof ProviderError && e.kind === 'unavailable');
  });

  it('refuses to start without a server-side key', () => {
    assert.throws(() => new AnthropicProvider({ model: 'm' }), (e) => e.kind === 'misconfigured');
  });
});

describe('secrets', () => {
  it('never returns the API key in any response', async () => {
    process.env.ANTHROPIC_API_KEY = SECRET;
    const token = await newToken();
    const responses = [
      await call('GET', '/health'),
      await call('GET', '/v1/plans'),
      await call('GET', '/v1/usage', { token }),
      await call('POST', '/v1/ai/generate', { token, body: goodBody, headers: consentHeader }),
    ];
    for (const r of responses) assert.ok(!r.raw.includes(SECRET));
    delete process.env.ANTHROPIC_API_KEY;
  });
});
