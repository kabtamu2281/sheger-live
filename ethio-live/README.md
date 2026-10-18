# Ethio Live

An Ethiopia-first live streaming app for Android: video and audio rooms, multi-guest seats, PK battles, chat, gifts,
a server-authoritative coin and bean wallet, creator payouts, fan clubs, rankings, events, direct messages and
built-in moderation. English and Amharic are complete; Afaan Oromo is partial.

| Part | Path | Stack |
|---|---|---|
| Android app | `android/app` | Kotlin, Jetpack Compose, Material 3, MVVM, supabase-kt, Agora RTC 4.5, Play Billing 7 |
| Pure logic | `android/core` | Kotlin/JVM (money, PK maths, validation, chat buffer…), unit tested |
| Backend | `supabase/` | Postgres migrations (RLS, ledger, all business rules as SQL functions), Deno Edge Functions |
| Admin dashboard | `admin/` | Vite, React, TypeScript, supabase-js |
| Docs | `docs/` | Setup, architecture, release, testing, status, legal |

Start here:

- [docs/SETUP.md](docs/SETUP.md): run everything locally and connect real services.
- [docs/FEATURE_STATUS.md](docs/FEATURE_STATUS.md): what is complete, what is incomplete, what needs credentials.
- [docs/TESTING.md](docs/TESTING.md): the tests and their latest real results.
- [docs/RELEASE.md](docs/RELEASE.md): building a signed AAB and the Google Play checklist.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): how the pieces fit and why.
- [docs/SERVICES_AND_PRICING.md](docs/SERVICES_AND_PRICING.md): third-party services and cost drivers.
- [docs/STORE_LISTING.md](docs/STORE_LISTING.md): Play Store text and screenshot checklist.
- [docs/legal/](docs/legal): Terms, Privacy Policy, Community Guidelines, Data safety answers (also bundled in the app).

Without credentials the app still builds and runs: it shows a "Server not configured" screen, and features whose
service is missing (live video, push, payments) say so instead of pretending to work.
