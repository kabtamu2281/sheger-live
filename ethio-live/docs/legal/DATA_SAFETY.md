# Google Play Data safety answers

Prepared from the code in this repository (version 2026-10-17). Re-check before each release, especially if push notifications, Chapa or analytics are added or removed.

## Overview

- Data is encrypted in transit: **Yes** (HTTPS/TLS to Supabase, Agora tokens over HTTPS; cleartext traffic is disabled in `network_security_config.xml`).
- Users can request that data is deleted: **Yes**, in-app (Settings, Delete account) and through support. Provide the web deletion URL required by Play: [ACCOUNT DELETION URL].
- Data is sold: **No**.
- Independent security review: **No** (not performed yet).

## Data types

| Category | Type | Collected | Shared | Optional | Purpose |
|---|---|---|---|---|---|
| Personal info | Name (display name) | Yes | No | No | App functionality, account management |
| Personal info | Email address | Yes, if email sign-in | No | Yes | Account management |
| Personal info | Phone number | Yes, if phone sign-in, and payout account | Yes, payout partner | Yes | Account management, payouts |
| Personal info | User IDs | Yes | No | No | App functionality, fraud prevention |
| Personal info | Other info (date of birth) | Yes | No | No | Age checks, safety |
| Financial info | Purchase history | Yes | No | No | App functionality, fraud prevention |
| Financial info | Other financial info (payout bank or wallet account) | Yes, creators only | Yes, payout partner | Yes | Payouts |
| Location | Approximate location (user-chosen city) | Yes, only if Nearby is on | No | Yes | App functionality |
| Messages | Other in-app messages (chat and DMs) | Yes | No | No | App functionality, safety |
| Photos and videos | Photos (avatar, cover) | Yes | No | Yes | App functionality |
| Audio | Voice or sound recordings | No (live audio is streamed, not stored) | — | — | — |
| App activity | App interactions (follows, gifts, rooms joined) | Yes | No | No | App functionality, personalization |
| App activity | Other user-generated content (bios, reports, appeals) | Yes | No | Yes | App functionality, safety |
| App info and performance | Crash logs, diagnostics | No (no crash reporting SDK is included) | — | — | — |
| Device or other IDs | Device or other IDs (push token) | Yes, only if push is configured | No | Yes | Notifications |

Notes:

- "Shared" excludes service providers that process data on our behalf (Supabase, Agora, Google, Chapa), which Play does not count as sharing.
- Payment card and wallet details are entered in Google Play or Chapa and are never received by Ethio Live.

## Permissions declared

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Talk to the backend and streaming service, adapt video quality to the network |
| `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT` | Route live audio to the speaker or a Bluetooth headset (required by the Agora SDK) |
| `CAMERA`, `RECORD_AUDIO` | Requested only when going live or joining a guest seat |
| `POST_NOTIFICATIONS` | Message, follow and live notifications (Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`, `FOREGROUND_SERVICE_MICROPHONE` | Keep a live broadcast running when the screen is off |
| `com.android.vending.BILLING` | Google Play coin purchases |

No location, contacts, storage or phone-state permissions are requested.
