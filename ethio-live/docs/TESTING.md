# Testing

Results below are from real runs. Anything not listed as run was not run.

## Latest results (2026-10-18, development container)

| Suite | Command | Result |
|---|---|---|
| Database (business rules, RLS, ledger, concurrency) | `cd supabase/tests && npm test` | **39 passed, 0 failed** (PostgreSQL 16) |
| Android `:core` unit tests | `cd android && ./gradlew :core:test` | **19 passed, 0 failed** |
| Edge Function shared code | `cd supabase/functions && deno test _shared/shared_test.ts` | **3 passed, 0 failed** |
| Edge Function type check | `cd supabase/functions && deno check */index.ts` | **passed** (all 9 functions) |
| Admin dashboard unit tests | `cd admin && npm test` | **3 passed, 0 failed** |
| Admin dashboard type check and build | `cd admin && npm run build` | **passed** |
| String resources check | `python3 tools/i18n/gen.py` | **passed** (502 keys in English and Amharic, 93 in Afaan Oromo, placeholders match) |
| RPC contract check (app and dashboard call only existing SQL functions with existing parameter names) | script in this doc's appendix | **passed** (60 app calls, all dashboard calls) |
| Android app data and streaming layers compiled against the real supabase-kt 3.1.4 and Agora 4.5.2 jars | JVM harness | **passed** |
| Android `:app:assembleDebug` (full build with Compose UI) | `./gradlew :app:assembleDebug` | **Runs in CI** (`.github/workflows/ethio-live.yml`). The development container cannot reach Google's Maven repository, so the full Android build was not run locally. See the PR's CI result. |

Not yet run anywhere: instrumented UI tests on a device, live streaming between two real devices, Google Play test purchases, Chapa sandbox payments, push delivery. Each needs the credentials listed in [SETUP.md](SETUP.md) and is covered by the manual checklist below.

## What the automated tests cover

### Database (`supabase/tests`, 39 tests)

Each test file builds a throwaway database from the real migrations and calls the same SQL functions the app calls, as a signed-in user (role `authenticated` with JWT claims), as the service role, or as staff with and without MFA (`aal2`).

- Accounts: minimum age 16, reserved and duplicate usernames, protective defaults for 16 and 17 year olds, Nearby off for minors, account deletion keeps the ledger but erases personal data, data export.
- Social: follow counters, blocking removes follows and hides profiles, DM privacy and message requests, adults cannot open conversations with minors who don't follow them, DM rate limits, reports capture evidence and prioritise child-safety reasons.
- Security: row level security hides private data and rejects direct writes to wallets, scores and messages.
- Wallet: purchases credit exactly once and reject amount mismatches; minors cannot buy or send paid gifts; gift debit and credit with replay protection; promo coins spent first and earning no beans; gift validation; free gift caps; **25 concurrent gift requests leave balances correct**; **the same idempotency key sent concurrently charges once**; ledger rows immutable even for the owner; settlement hold, KYC-gated payouts, two-person approval; adjustments need a second finance approver; refunds claw back coins and record shortfalls.
- Live: rooms discoverable only after the stream is confirmed, one live room per host, failed publishes never go live, adults-only broadcasting, broadcast bans, viewer counts from heartbeats not the client, chat rules and rate limits, mutes, moderator removal, guest seat requests and invitations with capacity limits, host mute that guests cannot override, kicks, blocks mid-stream, followers-only rooms, clean session shutdown, maintenance of abandoned rooms.
- PK: invitations and expiry, scores derived only from gift transactions inside the match window, clients cannot write scores, authoritative winner, rankings.
- Staff: role checks, one-time superadmin bootstrap, MFA for role and finance changes, audit log, moderation queue, suspension ends the stream, appeals reviewed by a different moderator, fan club joins through the ledger, event leaderboards, dashboard read functions.

### Android `:core` (19 tests)

Money formatting in birr, compact counts, payout estimate identical to the server's maths, error-code parsing from PostgREST messages, username, age, Ethiopian phone number and hashtag validation, seat layouts, PK share and server-clock countdown, adaptive video quality, chat buffer de-duplication and caps, gift combo window, duration formatting.

## Manual test checklist (needs configured services)

Run on two physical Android devices (one Android 8 to 10, one Android 13 or newer), on Wi-Fi and on mobile data.

1. Sign up with phone OTP (+251), then sign out and sign in again. Sign up with email, confirm the email link opens the app. Reset a password.
2. Try a birth date under 16: signup is refused. Use 16 or 17: Go live, Recharge and paid gifts are blocked.
3. Device A goes live (video). Device B sees it under Popular and New, joins, chats. Viewer count on A goes to 1, then back to 0 when B leaves; kill B's app and confirm the count drops within about a minute.
4. Deny the camera permission on A: a clear message, no crash. Grant it and go live.
5. Switch A to airplane mode for 10 seconds and back: both devices show Reconnecting and recover.
6. Multi-guest: B requests a seat, A accepts, B's video appears. A mutes B; B cannot unmute. A removes B.
7. Audio room with 8 seats: invite, accept, speaking indicators.
8. Gifts: buy the smallest Play package with a licence-tester account, send gifts, check the wallet ledger on both sides and in the admin dashboard. Send the same gift twice quickly and confirm only one debit per tap.
9. PK: two hosts, invite, accept, both videos side by side, gifts move the bar, the result after the timer matches the admin's view.
10. Report a live from B; in the admin dashboard, suspend A from the report. A's stream ends, A sees the restricted screen, appeals; a different moderator overturns.
11. Payout: give A beans (gifts), wait for settlement (or shorten `settlement_hold_days` in a test project), verify KYC in the dashboard, request a payout, approve with two finance accounts, mark paid.
12. Delete an account from Settings; confirm the user can no longer sign in and their profile shows as deleted.
13. Switch language to Amharic and Afaan Oromo; check that no screen is cut off on a 5-inch display.

## Appendix: RPC contract check

```bash
python3 - <<'PY'
import re, glob
sql = "\n".join(open(f).read() for f in glob.glob('supabase/migrations/*.sql'))
sigs = {m[1]: [p.strip().split()[0] for p in re.split(r',(?![^(]*\))', m[2]) if p.strip()]
        for m in re.finditer(r'create or replace function (\w+)\((.*?)\)\s*(returns|\n)', sql, re.S)}
bad = 0
for f in glob.glob('android/app/src/main/kotlin/**/*.kt', recursive=True):
    for m in re.finditer(r'rpc(?:Raw|Unit)?(?:<[^>]*>)?\(\s*"(\w+)"(.{0,400}?)\)\s*$', open(f).read(), re.M | re.S):
        if m[1] not in sigs or any(k not in sigs[m[1]] for k in re.findall(r'"(p_\w+)"\s*to', m[2])): bad += 1; print(f, m[1])
print('problems:', bad)
PY
```
