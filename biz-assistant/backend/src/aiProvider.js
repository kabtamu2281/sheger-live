import Anthropic from '@anthropic-ai/sdk';
import { SYSTEM_PROMPT } from './prompts.js';

export class ProviderError extends Error {
  /** @param {'unavailable'|'rejected'|'misconfigured'} kind */
  constructor(kind, message) {
    super(message);
    this.kind = kind;
  }
}

/** Claude via the official SDK. The API key is read from the server environment only. */
export class AnthropicProvider {
  constructor({ apiKey, model, client } = {}) {
    if (!client && !apiKey) throw new ProviderError('misconfigured', 'ANTHROPIC_API_KEY is not set');
    this.client = client ?? new Anthropic({ apiKey, timeout: 60_000, maxRetries: 2 });
    this.model = model;
  }

  async generate(userMessage) {
    let response;
    try {
      response = await this.client.beta.messages.create({
        model: this.model,
        max_tokens: 4096, // short business texts
        betas: ['server-side-fallback-2026-07-01'],
        fallbacks: 'default',
        output_config: { effort: 'low' },
        system: [{ type: 'text', text: SYSTEM_PROMPT, cache_control: { type: 'ephemeral' } }],
        messages: [{ role: 'user', content: userMessage }],
      });
    } catch (err) {
      if (err instanceof Anthropic.AuthenticationError || err instanceof Anthropic.PermissionDeniedError) {
        throw new ProviderError('misconfigured', `provider auth failed (${err.status})`);
      }
      if (err instanceof Anthropic.BadRequestError) {
        throw new ProviderError('unavailable', `provider rejected request (${err.status})`);
      }
      if (err instanceof Anthropic.RateLimitError) throw new ProviderError('unavailable', 'provider rate limited');
      if (err instanceof Anthropic.APIConnectionError) throw new ProviderError('unavailable', 'provider unreachable');
      if (err instanceof Anthropic.APIError) throw new ProviderError('unavailable', `provider error (${err.status})`);
      throw new ProviderError('unavailable', 'provider call failed');
    }
    if (response.stop_reason === 'refusal') throw new ProviderError('rejected', 'request declined');
    const text = response.content.filter((b) => b.type === 'text').map((b) => b.text).join('').trim();
    if (!text) throw new ProviderError('unavailable', 'empty response');
    return text;
  }
}
