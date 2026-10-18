# Android release and Google Play checklist

Nothing in this repository has been signed, uploaded or published. These are the steps to do it.

## 1. Build a signed App Bundle

1. Create an upload key once and keep it out of git (and backed up):
   ```bash
   keytool -genkeypair -v -keystore ethio-live-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Create `android/keystore.properties` (git-ignored):
   ```properties
   storeFile=../ethio-live-upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```
3. Fill `android/local.properties` with production values (section 1 of `.env.example`), `PAYMENT_PROVIDERS=google_play`, and a new `VERSION_CODE` for each upload.
4. Build:
   ```bash
   cd android
   ./gradlew :app:bundleRelease     # app/build/outputs/bundle/release/app-release.aab
   ./gradlew :app:assembleRelease   # optional APK for testing: app/build/outputs/apk/release/app-release.apk
   ```
5. Enrol in Play App Signing when you create the app; Google keeps the app signing key and you keep the upload key.

Release builds are minified with R8 (`proguard-rules.pro` keeps the kotlinx.serialization and Agora classes). Test the release build on a device before uploading.

## 2. Google Play Console checklist

### Before the first upload

- [ ] Developer account verified (organisation account if payouts are made by a company).
- [ ] App created with package `et.ethiolive.app`, default language English, Amharic listing added.
- [ ] Play App Signing enabled; upload key SHA-1 and app signing SHA-1 added to the Google Cloud OAuth client (Google Sign-In) and Firebase.

### Monetisation

- [ ] Payments profile set up for selling in-app products.
- [ ] In-app products created, all **consumable one-time products**: `el_coins_100`, `el_coins_500`, `el_coins_1200`, `el_coins_3000`, `el_coins_6500`. Set prices per country; the ETB price in `coin_packages` is what the server checks for Chapa only, Play prices come from Play.
- [ ] Service account linked in Play Console (Users and permissions) with "View financial data" and "Manage orders and subscriptions", key stored as `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
- [ ] Real-time developer notifications: Pub/Sub topic created, push subscription to `https://<ref>.supabase.co/functions/v1/play-notifications?token=<PLAY_RTDN_TOKEN>`, topic set in Monetisation setup, test notification sent.
- [ ] Licence testers added and a test purchase completed end to end (coins appear, ledger entry exists, refund in Play Console reverses them).
- [ ] The Play build offers **only** Google Play Billing for coins (`PAYMENT_PROVIDERS=google_play`). Creator payouts are outside Play Billing's scope because they pay creators, not sell digital goods.

### Policy declarations (App content)

- [ ] Privacy policy URL: host `docs/legal/PRIVACY_POLICY.md` on a public HTTPS page after filling in the operator details.
- [ ] Account deletion: in-app path (Settings, Delete account) and a public web URL where users can request deletion.
- [ ] Data safety form: answers in `docs/legal/DATA_SAFETY.md`.
- [ ] Target audience and content: select only the "16-17" and "18 and over" age groups, matching the Terms. Do not select any group under 16. The app is not designed for children.
- [ ] Content rating questionnaire: user-generated content, live streaming, user interaction, digital purchases. Expect a Teen or Mature rating depending on region.
- [ ] User-generated content policy: in-app reporting (yes), blocking (yes), moderation (staff dashboard), terms that prohibit objectionable content (Community Guidelines). Keep moderators staffed before launch.
- [ ] Foreground service declaration: types `camera` and `microphone`, used only while broadcasting; record a short video of going live for the declaration.
- [ ] Permissions: camera and microphone requested only at Go live or when joining a seat. No location permission.
- [ ] Financial features declaration if asked (creator payouts): describe beans as earnings paid by the operator, not a wallet or money transfer service. Get local legal advice on payment and tax rules in Ethiopia.
- [ ] Ads: the app contains no ads.

### Store listing

- [ ] Text from `docs/STORE_LISTING.md`, English and Amharic.
- [ ] Icon 512×512, feature graphic 1024×500, at least 4 phone screenshots (see the screenshot checklist in `docs/STORE_LISTING.md`).

### Testing tracks

- [ ] Internal testing with the team, then closed testing with at least the number of testers and days Play currently requires for new personal developer accounts, then production with a staged rollout (for example 10%, 50%, 100%).
- [ ] Pre-launch report reviewed (crashes, accessibility, permissions).

## 3. Backend production checklist

- [ ] Supabase project on a paid plan (point-in-time recovery, no pausing), `pg_cron` and `pg_net` enabled, migrations pushed.
- [ ] All Edge Function secrets set (`supabase secrets list`), functions deployed.
- [ ] SMS provider verified for +251, email sender (custom SMTP) configured.
- [ ] Agora project in secure mode (App Certificate on), usage alerts set.
- [ ] First superadmin bootstrapped, finance and moderator accounts created, each with TOTP.
- [ ] Admin dashboard hosted on its own HTTPS domain.
- [ ] Legal documents completed and reviewed by a lawyer; operator details filled in.
- [ ] A support inbox and an on-call moderator rota for child-safety reports.
