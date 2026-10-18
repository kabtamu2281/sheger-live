import { assert, assertEquals } from 'jsr:@std/assert@1';
import { RtcRole, RtcTokenBuilder } from 'npm:agora-token@2.0.4';
import { hmacSha256Hex, safeEqual } from './http.ts';

Deno.test('HMAC-SHA256 matches RFC 4231 test case 2', async () => {
  assertEquals(await hmacSha256Hex('Jefe', 'what do ya want for nothing?'),
    '5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843');
});

Deno.test('safeEqual', () => {
  assert(safeEqual('abc', 'abc'));
  assert(!safeEqual('abc', 'abd'));
  assert(!safeEqual('abc', 'abcd'));
});

Deno.test('Agora AccessToken2 is produced for a uid/channel', () => {
  const token = RtcTokenBuilder.buildTokenWithUid('970CA35de60c44645bbae8a215061b33', '5CFd2fd1755d40ecb72977518be15d3b',
    'el_test', 1234, RtcRole.SUBSCRIBER, 600, 600);
  assert(token.startsWith('007'), token);
});
