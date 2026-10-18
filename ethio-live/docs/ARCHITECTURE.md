# Architecture

## Principles

1. **The server decides.** Balances, gift effects, PK scores, viewer counts, seat assignments, age rules, rate limits and moderation are computed in Postgres functions. The app only asks; it never sends a balance, a score or a count.
2. **Money moves only through a double-entry ledger.** Every coin and bean movement is a `ledger_transactions` row with balanced `ledger_entries` (sum per unit is zero, enforced by a constraint trigger). Rows are immutable. Corrections are new transactions approved by two people.
3. **Every write is idempotent.** Gift sends, purchases, fan club joins, payouts and DMs carry a client key; replays return the first result instead of charging again.
4. **Secrets stay on the server.** The app and dashboard hold only the Supabase URL, the anon key and the Agora App ID. The Agora certificate, payment keys and service-role key live in Supabase secrets.
5. **Missing services are visible.** If Agora, push or payments are not configured the UI says so; nothing is simulated.

## Components

```
Android app (Compose, MVVM) ──► Supabase
  data/*Repository            ├─ PostgREST  → SQL functions (security definer) + RLS-protected reads
  stream/AgoraStreamEngine    ├─ Realtime   → room messages, participants, seats, PK, DMs, notifications
  billing/PlayBilling         ├─ Storage    → avatars, covers (size and type limited)
  push/EthioMessagingService  └─ Edge Functions (Deno)
                                   stream-token       Agora RTC tokens after authorize_stream()
                                   play-verify        verifies Play purchases with the Android Publisher API, credits once, consumes
                                   play-notifications refunds and chargebacks from Play RTDN
                                   payments-chapa     creates a Chapa checkout (outside-Play builds)
                                   chapa-webhook      HMAC-verified, re-verified with Chapa's API, credits once
                                   send-push          FCM HTTP v1, claims each notification once
                                   delete-account     erases personal data, soft-deletes the auth identity
                                   maintenance        fallback scheduler for run_maintenance()
Admin dashboard (React) ──► same SQL functions, gated by staff role and MFA (aal2)
Agora SD-RTN ◄──► devices (audio/video media only; tokens expire, renewed on onTokenPrivilegeWillExpire)
```

## Database (supabase/migrations)

| File | Contents |
|---|---|
| `…01_schema.sql` | Types, tables, platform config (economics), constraints, ledger tables and immutability triggers |
| `…02_functions.sql` | App-facing functions: signup, profiles, follows, blocks, discovery, live rooms, seats, chat, gifts, wallet, payments, payouts, DMs, notifications, PK, fan clubs, events, rankings, reports, maintenance |
| `…03_staff.sql` | Staff RBAC (`support < moderator < admin < superadmin`, `finance` separate), moderation, appeals, catalog, config, finance review, adjustments, support, analytics, audit log |
| `…04_security.sql` | RLS policies, revoked table privileges, function grants, Realtime publication, pg_cron schedule |
| `…05_catalog.sql` | Initial gifts (original Ethiopian-themed artwork keys), coin packages, badges, creator levels |
| `…06_storage.sql` | Storage buckets and policies (images only, 5 MB, per-user folders) |
| `…07_staff_reads.sql` | Read functions for the dashboard |

### Economics (platform_config, editable only by finance with MFA)

- 1 coin is sold at about ETB 0.43 to 0.50 depending on the package.
- Creator share: 5000 bps, so 100 purchased coins spent on gifts give the creator 50 beans. Promotional coins give 0 beans by default.
- 100 beans = ETB 40.00 before fees, so creators receive about 40% of the gift's coin price. The remainder covers app-store fees (15 to 30%), payment fees, streaming and operations.
- Beans are held for 7 days before they can be withdrawn. Minimum payout is 20,000 beans (ETB 8,000). Fee 2%. Payouts of 200,000 beans or more need two finance approvers.

### Live room lifecycle

`start_live` (status `preparing`) → app joins Agora with a token from `stream-token` → on Agora's first successful publish the app calls `confirm_live` (status `live`, followers notified) → heartbeats every 20 s → `end_live`, a moderator action, or maintenance (no heartbeat for 60 s) ends it. A room that never publishes within two minutes is marked `failed` and was never visible.

Viewers `join_room`, heartbeat every 20 s, and are counted only while their heartbeat is fresh (45 s). Joining Agora requires a token, which requires `authorize_stream`, which requires active, unblocked participation, so kicked or blocked users cannot keep watching.

### PK battles

Two solo video hosts. `pk_invite` → `pk_respond`. While running, every gift to either host inside the match window adds a `pk_score_events` row in the same transaction as the gift. Clients have no write access to scores. `pk_finish` (callable by anyone after the deadline, idempotent) sets the winner. Video: each host relays its channel into the other's with Agora channel media relay, using tokens minted per side by `stream-token`.

## Android app

- **UI:** Jetpack Compose, Material 3, always-dark theme with Ethiopian accent colours. Navigation Compose with a five-tab scaffold. Gift artwork and animations are original vectors drawn in code (`ui/gifts`).
- **State:** ViewModels per screen expose `StateFlow`; simple screens use a generic `LoadViewModel`. Manual dependency injection through `AppContainer`.
- **Data:** repositories wrap supabase-kt; server error codes map to `ServerError` and to translated strings. Public lists are cached in Room so Home opens instantly on slow networks.
- **Streaming:** `StreamEngine` interface with an Agora implementation and an "unconfigured" implementation. Adaptive quality steps down on poor network and up slowly; a data-saver preference caps it. Broadcasting runs in a foreground service so it survives screen-off.
- **Payments:** Play Billing for Play builds (one-time consumable products; obfuscated account ID = order ID; consumed only after server verification). Chapa through a browser checkout for non-Play builds.
- **Localisation:** English, Amharic (complete), Afaan Oromo (partial, falls back to English), per-app language picker.

## Admin dashboard

Static React app. Signs in with Supabase Auth, enforces TOTP enrolment, reads the role with `staff_whoami`, and only shows sections the role can use. All decisions are enforced again in SQL; the audit log is append-only at the database level.
