// Shared HTTP helpers for Ethio Live edge functions.
import { createClient, type SupabaseClient } from 'jsr:@supabase/supabase-js@2';

export const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } });
}

/** Maps a database error code raised by our SQL functions to an HTTP response. */
export function dbError(error: { message?: string } | null, fallbackStatus = 400): Response {
  const code = (error?.message ?? 'unknown_error').split(':')[0].trim();
  const status = code === 'not_authenticated' ? 401 : code.endsWith('_not_found') ? 404 : fallbackStatus;
  return json({ error: code }, status);
}

export function requireEnv(name: string): string {
  const v = Deno.env.get(name);
  if (!v) throw new MissingConfig(name);
  return v;
}

export class MissingConfig extends Error {
  constructor(public variable: string) { super(`missing configuration: ${variable}`); }
}

/** Client that acts as the calling user, so RLS and auth.uid() apply. */
export function userClient(req: Request): SupabaseClient {
  return createClient(requireEnv('SUPABASE_URL'), requireEnv('SUPABASE_ANON_KEY'), {
    global: { headers: { Authorization: req.headers.get('Authorization') ?? '' } },
    auth: { persistSession: false },
  });
}

/** Privileged client. Only used for the narrow service-only SQL functions. */
export function serviceClient(): SupabaseClient {
  return createClient(requireEnv('SUPABASE_URL'), requireEnv('SUPABASE_SERVICE_ROLE_KEY'), {
    auth: { persistSession: false },
  });
}

export async function requireUser(req: Request): Promise<{ id: string; email?: string }> {
  const { data, error } = await userClient(req).auth.getUser();
  if (error || !data.user) throw new HttpError(401, 'not_authenticated');
  return { id: data.user.id, email: data.user.email };
}

export class HttpError extends Error {
  constructor(public status: number, public code: string) { super(code); }
}

export function handler(fn: (req: Request) => Promise<Response>) {
  return async (req: Request): Promise<Response> => {
    if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders });
    try {
      return await fn(req);
    } catch (e) {
      if (e instanceof HttpError) return json({ error: e.code }, e.status);
      if (e instanceof MissingConfig) {
        console.error(e.message);
        return json({ error: 'service_not_configured', missing: e.variable }, 503);
      }
      console.error(e);
      return json({ error: 'internal_error' }, 500);
    }
  };
}

/** Constant-time string comparison for signatures and shared secrets. */
export function safeEqual(a: string, b: string): boolean {
  const ea = new TextEncoder().encode(a);
  const eb = new TextEncoder().encode(b);
  if (ea.length !== eb.length) return false;
  let diff = 0;
  for (let i = 0; i < ea.length; i++) diff |= ea[i] ^ eb[i];
  return diff === 0;
}

export async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(message));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}
