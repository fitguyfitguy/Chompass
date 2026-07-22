---
title: Features
description: Food logging, on-device AI, Health Connect, keto modes, and open export — without ads or analytics.
layout: single
---

Android-optimized calorie and macro tracking. Core logging from Fud AI plus fork-specific additions.

## Food logging

Multi-photo capture (up to 10), share into the app, voice, barcode, text, manual entry, and saved meals. Draft recovery if analysis is interrupted.

## On-device AI (opt-in)

**On-Device (Private)** in Settings → AI Provider runs Gemma 4 Edge (E2B or E4B) via LiteRT-LM. One-time download (~2.4–3.4 GB). No API key and no server upload for food text or photo analysis. Cloud AI remains more accurate; optional fallback retries in the cloud when on-device fails.

## AI Coach

Chat with your own provider key. Optional fallback provider. Replies follow the app language.

## Diet modes

Including keto carb mode — goals, meal advice, and Coach stay in sync.

## Progress & Health Connect

Weight, body fat, calorie history, goals, steps/exercise, and wellness (sleep, HR, hydration) via Android Health Connect. Two-way sync; live meal import from other apps; optional background sync (off by default). Works with Gadgetbridge, openScale, Samsung Health, and other Health Connect companions — no vendor SDKs.

## Water, widgets, export

Optional local water log with reminders and a widget. Home-screen calorie, protein, metrics, and water widgets. Diary export (JSON / Markdown / CSV), weight & body-metrics import/export, meal sharing, and bulk JSON import.

## Why this fork

NoFUD started when upstream [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai) briefly shipped AdMob. Huge thanks to **Apoorv Darshan** and Fud&nbsp;AI — this fork keeps that core BYOK food logger and takes a leaner Android path on Codeberg. Prefer workouts and the full upstream set? Use [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai).

| Feature | [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai) | NoFUD |
|---------|--------|-------|
| Banner ads | Shipped briefly; removed in 3.0.3 | **Never shipped** |
| On-device AI (Gemma&nbsp;4) | No | **Yes** (opt-in) |
| Barcode scanner | ML Kit | **FOSS zxing-cpp** |
| Workouts library | Yes (~120&nbsp;MB APK) | **Omitted** (~15&nbsp;MiB arm64) |
| Keto / diet modes | No | **Yes** |
| Health Connect wellness | Partial | **Steps, exercise, sleep, HR, hydration** |
| Distribution | Play-focused | **Codeberg** / Obtainium |

- **No ads, no analytics** — never shipped AdMob; no tracking SDKs
- **FOSS barcode** — on-device scanning via zxing-cpp, not proprietary ML Kit
- **Lean APK** — no workouts tab or bundled exercise library ({{< apk_arm64_note >}} arm64 / {{< apk_universal_note >}} universal in v{{< site_version >}}, vs ~120 MB upstream with workouts)
- **Open data** — export diary and body metrics; import JSON, CSV, openScale, Health Connect
- **Android-first** — Material 3 UI, widgets, customizable home nutrients and meal times

Localization covers 15 languages. Full comparison: [project README](https://codeberg.org/fitguy/NoFUD). Upstream: [Fud&nbsp;AI releases](https://github.com/apoorvdarshan/fud-ai/releases).
