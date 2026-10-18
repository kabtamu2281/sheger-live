import { createClient, type SupabaseClient } from '@supabase/supabase-js';

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const configured = Boolean(url && anonKey);

// The dashboard only ever holds the anon key plus the signed-in staff member's session.
export const supabase: SupabaseClient | null = configured
  ? createClient(url!, anonKey!, { auth: { persistSession: true, storageKey: 'ethio-live-admin' } })
  : null;

export function db(): SupabaseClient {
  if (!supabase) throw new Error('not_configured');
  return supabase;
}

/** Calls a Postgres function and throws its error message (the server's error code) on failure. */
export async function rpc<T = unknown>(fn: string, args?: Record<string, unknown>): Promise<T> {
  const { data, error } = await db().rpc(fn, args);
  if (error) throw new Error(error.message);
  return data as T;
}
