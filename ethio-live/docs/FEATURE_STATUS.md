# Feature status

Legend:
- **Complete**: implemented end to end and the server side is covered by passing automated tests. The Android UI for it compiles in CI but has not been exercised on a device yet.
- **Needs credentials**: implemented, but it only works once the named service is configured; never tested against the real service.
- **Incomplete**: missing or partial, with what is left.

Last updated 2026-10-18.

| Area | Feature | Status | Notes |
|---|---|---|---|
| Accounts | Phone OTP sign-in | Needs credentials | Supabase phone auth + an SMS provider that delivers to +251 |
| | Email sign-up, sign-in, password reset | Complete | Needs Supabase Auth email (built-in for testing, custom SMTP for production) |
| | Google sign-in | Needs credentials | `GOOGLE_WEB_CLIENT_ID`, Supabase Google provider, SHA-1 registered |
| | Profile completion, age gate (16+), terms acceptance | Complete | |
| | Edit profile, avatar and cover upload | Complete | Images resized on device, 5 MB limit and image types enforced by Storage policies |
| | Username change limit, reserved names | Complete | |
| | Account deletion, data export | Complete | |
| Discovery | Home tabs (For you, Popular, New, Following, Nearby, Multi-guest, Audio, PK), categories, search, trending | Complete | Nearby uses a user-chosen city, no GPS |
| | Personalised recommendations | Complete (basic) | Ranks by the user's language and interests, then viewers; no ML. Can be turned off |
| Live | Video live with go-live preview | Needs credentials | Agora App ID + certificate |
| | Audio rooms (up to 9 seats) with speaking indicators | Needs credentials | Agora |
| | Multi-guest video (2, 4, 6, 9 seats) | Needs credentials | Agora |
| | Seat requests, invitations, host mute, removal, moderators | Complete | Server rules tested; media needs Agora |
| | Chat, rate limits, mutes, message removal | Complete | Supabase Realtime |
| | Real viewer counts (heartbeat presence) | Complete | |
| | Reconnection and adaptive quality, data saver | Complete (logic), Needs credentials (media) | |
| | Background broadcasting (foreground service) | Complete | |
| | Beauty filter | Needs credentials | Agora basic beauty only (smoothing, lightening) |
| | Gaming mode | Incomplete | Labelled category with camera video. Screen capture streaming is not implemented |
| | Share a live outside the app | Incomplete | No share links or deep links to a room yet |
| PK | Invite, accept, timer, server scores, result, rankings | Complete | Side-by-side video uses Agora channel media relay (needs credentials, and media relay must be enabled for the Agora project) |
| Gifts | Catalog with original Ethiopian-themed artwork and animations | Complete | 14 gifts, editable in the dashboard |
| | Sending gifts, combos, idempotency, free daily gifts | Complete | |
| Wallet | Double-entry ledger, coins, promo coins, beans, history | Complete | |
| | Google Play Billing purchases | Needs credentials | Play Console products, service account, RTDN. Server verification code untested against Google |
| | Chapa purchases (non-Play builds only) | Needs credentials | Chapa keys and webhook; not offered in Play builds |
| | Refunds and chargebacks | Complete (server), Needs credentials (Play RTDN, Chapa) | |
| Creators | Dashboard (earnings, sessions, top supporters, gifts) | Complete | |
| | Payout requests to telebirr, CBE Birr, M-Pesa, bank | Complete (request and review flow) | Money is sent manually by finance staff, then recorded. No payout API integration |
| | Identity verification (KYC) | Incomplete | Staff record the result and a reference; there is no in-app document capture or KYC provider integration |
| Community | Fan clubs, events and leaderboards, badges, levels | Complete | |
| Messages | DMs, message requests, read receipts, minor protections | Complete | |
| | Push notifications | Needs credentials | Firebase project, service account, send-push schedule. In-app notifications work without it |
| Safety | Report live, user, message; block; mute | Complete | |
| | Moderation queue with evidence, suspensions, bans, broadcast bans, appeals | Complete | Dashboard |
| | Automated content detection (nudity, CSAM hashing) | Incomplete | Not implemented. Human review only. Strongly recommended before public launch |
| Admin | Dashboard: users, live rooms, reports, appeals, support, catalog, config, events, rankings, payments, payouts, adjustments, staff, audit log | Complete | MFA required; roles enforced in SQL |
| Localisation | English, Amharic | Complete | Amharic should be reviewed by a native speaker before release |
| | Afaan Oromo | Incomplete | 93 of 502 strings; the rest falls back to English |
| Release | Signed AAB, store listing, screenshots | Incomplete | Needs your upload key and real screenshots; see RELEASE.md |
| Tests | Database, core, Edge Function, dashboard tests | Complete | See TESTING.md |
| | Instrumented Android UI tests | Incomplete | Not written; manual checklist in TESTING.md |
