# Third-party services and cost considerations

Prices change; check each provider's current pricing page before budgeting. Figures here are rough orders of magnitude to show what drives cost, not quotes.

| Service | Used for | Required? | What drives cost |
|---|---|---|---|
| **Supabase** | Postgres, Auth, Realtime, Storage, Edge Functions | Yes | Plan tier (Pro for production: backups, no pausing), database size, egress, Realtime concurrent connections and messages, function invocations. Realtime is the main driver for busy rooms (chat and presence). |
| **Agora** | Real-time audio and video | Yes, for live | Billed per minute of each user in a channel, by resolution tier (audio, HD, Full HD). A 30-minute HD live with 200 viewers is 6,000 HD-minutes. This is usually the largest cost. Media relay for PK and beauty extensions are billed separately. Agora gives a free monthly allowance. |
| **Google Play** | Distribution, billing | Yes, for Play | One-time developer registration fee. Service fee on in-app purchases (15% for the first USD 1M per year for enrolled developers, higher above). |
| **Chapa** | Telebirr, CBE Birr, M-Pesa and cards in ETB | Only outside Play | Per-transaction fee; requires a merchant account and business documents. |
| **Firebase Cloud Messaging** | Push notifications | Optional | Free. |
| **SMS provider** (Twilio, MessageBird, Vonage…) | Phone OTP | If phone sign-in is used | Per SMS to +251. Ethiopian SMS is relatively expensive and delivery varies by provider; test before launch. Email and Google sign-in avoid this cost. |
| **Payout channels** (telebirr, CBE Birr, M-Pesa, banks) | Paying creators | For payouts | Transfer fees per payout, plus any business account fees. The 2% payout fee in config is meant to cover these. |
| **Static hosting** (Netlify, Vercel, Cloudflare Pages…) | Admin dashboard | Yes | Free tiers are enough. |
| **Content moderation** (e.g. Hive, Amazon Rekognition, PhotoDNA) | Automated detection | Recommended | Per image or per minute of video sampled. Not integrated yet. |

## Unit economics example (default config)

- A fan pays about ETB 50 for 100 coins (before the Play fee, Play keeps 15 to 30%).
- If those 100 coins are sent as gifts, the creator gets 50 beans, worth ETB 20.00 at 100 beans = ETB 40.00, minus the 2% payout fee.
- The operator keeps the rest to cover store fees, Agora minutes, Supabase, SMS, moderation and payout costs.

Finance staff can change the creator share, bean value, fees, minimum payout and settlement hold in the dashboard; every change is audited.

## Keeping costs down

- Default viewer video quality is adaptive; the data-saver setting caps it, which also reduces Agora minutes billed at HD.
- Audio rooms cost far less per minute than video.
- Realtime presence uses periodic heartbeats rather than per-second updates.
- Images are resized on the device before upload.
