# Local Business Assistant (Ethiopia)

An Android app that helps Ethiopian microbusiness owners with everyday admin in **English, Amharic (አማርኛ) and Afaan Oromo**, with a small backend that keeps the AI provider key off the phone.

```
biz-assistant/
  android/core      Pure-Kotlin domain logic + unit tests (money, invoices, summaries, strings, AI rules)
  android/app       Jetpack Compose app (Room database, screens, voice, PDF export)
  backend           Node.js AI gateway (Anthropic SDK), consent + quota enforcement
  tools             check-no-secrets.sh
```

## Features

| Feature | Where | AI? |
|---|---|---|
| Guided sale and expense entry with validation | Records tab | No |
| Invoices and quotations (line items, discount, user-entered taxes, recorded payments) | Home → Create invoice / quotation | No, calculated |
| Daily and monthly summaries | Home → Summarize today's sales / Monthly summary | No, calculated |
| Customer messages, product descriptions, business letters | Home quick actions | Yes, with template fallback |
| Translation between the three languages | Home → Translate this text | Yes |
| Guided business report (sends computed totals only) | Home → Guided business report | Yes, with template fallback |
| Edit, preview, save, export PDF / share text for every result | Result screen | |
| Voice input and read-aloud | "Speak" on text fields, "Read aloud" on results | Uses the phone's speech services |
| Searchable library of saved outputs, saved templates, conversation history | Library tab | |
| Usage allowance, plans and bundles | Home card, Settings → Plans | |
| Delete conversations, documents, records, or everything; record retention period | Settings → Privacy | |

The home screen shows quick actions such as "Create invoice", "Summarize today's sales", "Draft a customer message" and "Translate this text", so users pick a task instead of starting from a blank chat box. AI tasks are guided forms. Missing required information is requested before anything is produced (`TaskType.missingFields`).

## Safety rules, and where they are enforced

- **Deterministic arithmetic.** All money is `Long` santim, quantities are `BigDecimal`, and rounding is half-up (`core/Money.kt`, `core/Calculations.kt`, `core/Records.kt`). Invoices, quotations and summaries are never sent to the AI.
- **Nothing invented.** The app has no default tax rate: the user types any rate that applies to them. An invoice shows as "Paid" only when the user records payments that cover the total. A period with no records says so, and nothing is estimated. `NumberGuard` (on the app and on the server) rejects AI drafts that contain numbers not present in the user's inputs or the app's computed figures, and falls back to a template built from the user's own figures.
- **Consent.** No content leaves the phone until the user allows AI on the explanation screen. The app also asks for confirmation each time, showing the text it will send. The backend refuses requests without both the `X-AI-Consent: granted` header and `"consent": true` in the body. Consent can be withdrawn in Settings at any time.
- **Data minimisation.** Only the current task's fields, the business name and the language are sent. Reports send aggregated totals, never individual records, customers or notes. The backend stores token hashes and usage counters only. It never stores or logs user content.
- **No secrets in the client.** The app knows only `BACKEND_URL`. `tools/check-no-secrets.sh` scans the sources, and CI also scans the built APK.
- **Fallbacks.** When AI is off, over quota, unreachable, refused or untrustworthy, the user gets a manual template in their language and a message explaining why. Record keeping, invoices and summaries work fully offline.
- **Privacy.** Cloud backup is disabled for app data. Records older than the chosen retention period (default 12 months) are purged on launch. Every saved item can be deleted individually or all at once. "Delete all my data" also deletes the server-side counters.

## Data model

The Room entities are in `android/app/.../data/Entities.kt`: `UserEntity`, `BusinessProfileEntity`, `TaskTemplateEntity`, `ConversationEntity`, `MessageEntity`, `GeneratedDocumentEntity`, `BusinessRecordEntity`, `LanguagePreferenceEntity`, `UsageQuotaEntity` and `SubscriptionEntity`.

## Acceptance tests

| Requirement | Test |
|---|---|
| Language selection works | `core/.../LanguageTest.kt`: every string exists in all 3 languages with matching placeholders, Amharic uses Ethiopic script, Oromo uses Qubee, and documents render in the chosen language with identical totals |
| Generated documents remain editable | Every result opens in the editor (`EditorScreen`), with edit/preview tabs, save, update and delete |
| Calculations match independently worked results | `CalculationsTest.kt`, `SummaryTest.kt`: hand-computed expected values |
| AI failure produces a helpful fallback | `AssistantEngineTest.kt` (outage, exception, quota, refusal) and backend `test/app.test.js` |
| Secrets never in client code | `tools/check-no-secrets.sh` on sources and APK (CI), backend test asserting the key never appears in responses |
| No external sending without authorization | `AssistantEngineTest.neverSendsContentWithoutConsent` and backend consent tests |

Run them:

```bash
cd biz-assistant/android && ./gradlew :core:test        # add -PcoreOnly=true where Google Maven is unreachable
cd biz-assistant/backend && npm ci && npm test
biz-assistant/tools/check-no-secrets.sh
```

## Building the APK

Requires JDK 17+ and the Android SDK (API 35).

```bash
cd biz-assistant/android
./gradlew :app:assembleDebug -PbackendUrl=https://your-backend.example.com
# → app/build/outputs/apk/debug/app-debug.apk
```

The default `backendUrl` is `http://10.0.2.2:8080`, which is a backend running on the same computer as the Android emulator. The GitHub Actions workflow `.github/workflows/biz-assistant.yml` builds the debug APK and uploads it as an artifact. Set the repository variable `BIZ_BACKEND_URL` to bake in your deployed backend.

## Running the backend

```bash
cd biz-assistant/backend
npm ci
ANTHROPIC_API_KEY=... npm start        # listens on :8080
```

Environment variables: `ANTHROPIC_API_KEY` (required for AI; without it the app uses templates), `AI_MODEL` (default `claude-opus-5-5`), `PORT`, `DATA_FILE`, `PAYMENT_PROVIDER`. Keep the key in your host's secret manager, never in the repository.

Endpoints: `POST /v1/devices`, `GET /v1/usage`, `POST /v1/ai/generate`, `GET /v1/plans`, `POST /v1/subscriptions/checkout`, `DELETE /v1/devices/me`, `GET /health`.

## Still needs configuration before launch

- **Hosting** the backend over HTTPS, then building the app with `-PbackendUrl=...`. The JSON-file store suits a single instance; use a database for more than one.
- **Payments.** Checkout returns `501 payments_not_configured` until a provider such as Telebirr or Chapa is integrated. Plans should be activated only from the provider's verified webhook. Plan and bundle prices in `backend/src/plans.js` and `core/Plans.kt` are placeholders.
- **Native-speaker review** of the Amharic and Afaan Oromo wording in `core/Strings.kt` and `core/Templates.kt`.
- **Release signing** (a keystore) for a Play Store or sideload release build.
- Voice input and read-aloud depend on the speech engines installed on each phone. Amharic and Oromo voices are not available on every device, and the app says so when they are missing.
