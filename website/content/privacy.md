---
title: Privacy
description: No ads, no analytics, no accounts. Local-first food logs with BYOK cloud AI or opt-in on-device inference.
layout: single
---

NoFUD is a privacy-first, ad-free calorie tracker forked from [Fud AI](https://github.com/apoorvdarshan/fud-ai).

## Summary

- **No ads** — No AdMob or other advertising SDKs.
- **No analytics** — No usage tracking, crash analytics, or telemetry SDKs.
- **No accounts** — No sign-in, cloud sync, or central user database.
- **Local-first** — Food logs, weight history, profile, and Coach chat stay on your device.
- **Bring your own AI key** — Keys are encrypted on-device. Cloud requests go directly from your phone to the provider you choose.
- **On-Device (Private)** — Opt-in Gemma 4 keeps food text and photo analysis on the device; nothing is uploaded for that path.

## Data on your device

The app stores locally: food entries and photos, weight and body-fat history, optional water log, profile and goals, Coach history, and widget snapshots.

**Delete All Data** (Settings) wipes app storage only. It does not remove records previously synced to Health Connect — manage those in the Health Connect app.

## Network requests

NoFUD contacts external services only when you use a feature that needs them:

| Feature | What is sent | Where |
|---------|----------------|-------|
| AI food analysis / Coach | Meal text, images, or chat context you submit | Your configured AI provider |
| Barcode scan | Scanned barcode | Open Food Facts public API |
| Health Connect | Nutrition, weight, body fat, height (write); sleep, HR, hydration, steps, energy (read) if enabled | Google Health Connect on-device |
| App updates | — | Manual via Codeberg releases |

Optional **background sync** (off by default) only reads Health Connect on-device; it sends nothing off the device.

NoFUD does not sell or share your health data for advertising.

## Meal sharing

Shared meals use a `nofud://` deep link with base64-encoded nutrition JSON. Links leave your device through the app or messenger you choose.

## Contact

Source and issues: [codeberg.org/fitguy/NoFUD](https://codeberg.org/fitguy/NoFUD).

Canonical copy also lives in the repository as [PRIVACY.md](https://codeberg.org/fitguy/NoFUD/src/branch/main/PRIVACY.md).
