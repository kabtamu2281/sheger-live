# Setup and local development

All commands are run from the `ethio-live/` folder unless a step says otherwise.

## Tools

| Tool | Version used | For |
|---|---|---|
| JDK | 17 | Android build |
| Android SDK | platform 35, build-tools 35 | Android build (Android Studio Ladybug or newer is easiest) |
| Gradle | wrapper 8.14.3 (`android/gradlew`) | Android build |
| Node.js | 20 or 22 | DB tests, admin dashboard |
| Supabase CLI | 2.x | Local backend, deploying migrations and functions |
| Deno | 2.x | Type-checking Edge Functions (`deno check`) |
| PostgreSQL | 15 or 16 | Only for running the DB tests without the Supabase CLI |

## 1. Backend (Supabase)

### Local

```bash
supabase start                      # starts Postgres, Auth, Realtime, Storage, Edge runtime
supabase db reset                   # applies supabase/migrations/*.sql in order
supabase functions serve --env-file supabase/.env   # optional: run Edge Functions locally
```

`supabase start` prints the local API URL and anon key; use them in step 2 and 3.

### Hosted project

1. Create a project at supabase.com (choose the region closest to your users; Europe (Frankfurt) is usually the lowest latency to Ethiopia).
2. Database, Extensions: enable `pg_cron` and `pg_net` **before** applying migrations, so the maintenance job is scheduled automatically.
3. Link and push:
   ```bash
   supabase link --project-ref <ref>
   supabase db push
   ```
4. Secrets: copy section 3 of `.env.example` to `supabase/.env`, fill it in, then
   ```bash
   supabase secrets set --env-file supabase/.env
   supabase functions deploy stream-token play-verify play-notifications payments-chapa chapa-webhook delete-account send-push maintenance
   ```
5. Auth settings (dashboard, Authentication):
   - URL configuration: add `ethiolive://auth` to Redirect URLs (email confirmation and password recovery open the app).
   - Phone: configure an SMS provider (Twilio, MessageBird, Vonage or Textlocal) and confirm with them that +251 numbers are delivered. Phone sign-in does not work until this is done.
   - Google: enable the provider with a Web OAuth client ID and secret (same client ID as `GOOGLE_WEB_CLIENT_ID`).
   - Multi-factor: TOTP must stay enabled; the admin dashboard requires it.
6. Push delivery: `send-push` must be called regularly. With `pg_net` enabled, schedule it from the SQL editor:
   ```sql
   select cron.schedule('ethiolive-push', '30 seconds', $$
     select net.http_post(
       url := 'https://<ref>.supabase.co/functions/v1/send-push',
       headers := jsonb_build_object('x-cron-secret', '<CRON_SECRET>', 'Content-Type', 'application/json'),
       body := '{}'::jsonb)
   $$);
   ```
   Without `pg_cron`, call `functions/v1/maintenance` every 20 to 60 seconds and `functions/v1/send-push` every 30 seconds from any external scheduler with the same header.

### First administrator

There is no default admin password. To create the first superadmin:

1. Sign up in the Android app **with an email address** and finish the profile.
2. In the Supabase SQL editor (which runs as a privileged role) run:
   ```sql
   select bootstrap_superadmin('owner@example.com');
   ```
   It refuses to run once a superadmin exists, and it is not callable by app users.
3. Sign in to the admin dashboard with that email and enrol an authenticator app when asked. Add other staff from the Staff page.

## 2. Android app

```bash
cd android
cp ../.env.example local.properties   # then keep only section 1 and fill it in
./gradlew :app:assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test                  # pure-Kotlin unit tests
```

- For a local backend on an emulator use `SUPABASE_URL=http://10.0.2.2:54321`. Cleartext HTTP to `10.0.2.2` is allowed in debug builds only (`src/debug/res/xml/network_security_config.xml`); release builds are HTTPS-only.
- `local.properties` is git-ignored. In CI the same names are read from environment variables.
- Strings are generated: edit `tools/i18n/en.py`, `am.py` or `om.py`, then run `python3 tools/i18n/gen.py`, which also fails if a key used in code is missing or a translation's placeholders differ from English.

### Services the app needs at runtime

| Feature | Needs |
|---|---|
| Everything | `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| Live video and audio | `AGORA_APP_ID` in the app and `AGORA_APP_ID` + `AGORA_APP_CERTIFICATE` secrets (tokens are minted by `stream-token`) |
| Coin purchases (Play) | App published to a Play Console track, in-app products created with IDs matching `coin_packages.play_product_id` (`el_coins_100`, `el_coins_500`, `el_coins_1200`, `el_coins_3000`, `el_coins_6500`), `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`, `ANDROID_PACKAGE_NAME`, Pub/Sub RTDN pointing at `play-notifications` |
| Coin purchases (Chapa, outside Play only) | `PAYMENT_PROVIDERS=google_play,chapa`, Chapa keys and webhook secret |
| Push notifications | Firebase project, `FIREBASE_*` in the app, `FIREBASE_SERVICE_ACCOUNT_JSON` secret, the send-push schedule |
| Google sign-in | `GOOGLE_WEB_CLIENT_ID` and the Supabase Google provider; the app's SHA-1 registered in Google Cloud |

## 3. Admin dashboard

```bash
cd admin
cp .env.example .env.local    # fill in URL and anon key
npm install
npm run dev                   # http://localhost:5174
npm run build                 # static files in admin/dist, host on any static host behind HTTPS
```

Host it on a separate domain, never inside the app. It only holds the anon key; every permission is checked in the database.

## 4. Running the tests

See [TESTING.md](TESTING.md).
