// Issues short-lived Agora RTC tokens. The Agora App Certificate never leaves the server.
// POST { room_id }                -> token for the caller's role in that room (publisher only for host/seated guests)
// POST { pk_match_id }            -> token that lets a PK host relay their stream into the opponent's channel
import { RtcRole, RtcTokenBuilder } from 'npm:agora-token@2.0.4';
import { dbError, handler, HttpError, json, requireEnv, userClient } from '../_shared/http.ts';

Deno.serve(handler(async (req) => {
  if (req.method !== 'POST') throw new HttpError(405, 'method_not_allowed');
  const appId = requireEnv('AGORA_APP_ID');
  const certificate = requireEnv('AGORA_APP_CERTIFICATE');
  const ttl = Math.min(Number(Deno.env.get('AGORA_TOKEN_TTL_SECONDS') ?? 900), 3600);
  const body = await req.json().catch(() => ({})) as { room_id?: string; pk_match_id?: string };
  const supabase = userClient(req);

  if (body.pk_match_id) {
    const { data, error } = await supabase.rpc('authorize_pk_relay', { p_match: body.pk_match_id });
    if (error) return dbError(error, 403);
    const relay = data as { source_channel: string; dest_channel: string; rtc_uid: number };
    // Agora requires the relay's source-channel token to be built for uid 0; the destination token carries the
    // host's own uid so the relayed stream appears under that uid in the opponent's channel.
    const build = (channel: string, uid: number) =>
      RtcTokenBuilder.buildTokenWithUid(appId, certificate, channel, uid, RtcRole.PUBLISHER, ttl, ttl);
    return json({
      app_id: appId,
      rtc_uid: relay.rtc_uid,
      source_channel: relay.source_channel,
      source_token: build(relay.source_channel, 0),
      dest_channel: relay.dest_channel,
      dest_token: build(relay.dest_channel, relay.rtc_uid),
      expires_in: ttl,
    });
  }

  if (!body.room_id) throw new HttpError(400, 'room_id_required');
  const { data, error } = await supabase.rpc('authorize_stream', { p_room: body.room_id });
  if (error) return dbError(error, 403);
  const auth = data as { channel_name: string; rtc_uid: number; publisher: boolean; mode: string };
  const role = auth.publisher ? RtcRole.PUBLISHER : RtcRole.SUBSCRIBER;
  const token = RtcTokenBuilder.buildTokenWithUid(appId, certificate, auth.channel_name, auth.rtc_uid, role, ttl, ttl);
  return json({ app_id: appId, token, channel_name: auth.channel_name, rtc_uid: auth.rtc_uid,
                publisher: auth.publisher, mode: auth.mode, expires_in: ttl });
}));
