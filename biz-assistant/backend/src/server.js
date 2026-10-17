import { createServer } from 'node:http';
import { AnthropicProvider, ProviderError } from './aiProvider.js';
import { createApp } from './app.js';
import { Store } from './store.js';

const port = Number(process.env.PORT ?? 8080);
const store = new Store(process.env.DATA_FILE ?? './data/store.json');

let provider = null;
try {
  provider = new AnthropicProvider({ apiKey: process.env.ANTHROPIC_API_KEY, model: process.env.AI_MODEL || 'claude-opus-5-5' });
} catch (err) {
  if (!(err instanceof ProviderError)) throw err;
  console.warn('AI provider not configured – /v1/ai/generate will return 503 and the app will use templates.');
}

const log = (event) => console.log(JSON.stringify({ ts: new Date().toISOString(), ...event }));
const app = createApp({ store, provider, paymentProvider: process.env.PAYMENT_PROVIDER ?? '', log });

createServer(app).listen(port, () => console.log(`Business assistant backend listening on :${port}`));
