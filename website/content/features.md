---
title: Features
description: Food logging, BYOK AI, keto modes, open export, browser PWA, and Android extras. No ads or analytics.
layout: single
---

Calorie and macro tracking as an [installable PWA](https://fitguy.codeberg.page/NoFUD/app/) in any modern browser and as an Android app. Core logging from Fud AI plus fork-specific additions.

## Food logging

Multi-photo capture (up to 10), share into the Android app, voice, barcode, text, manual entry, and saved meals. Draft recovery if analysis is interrupted.

## Browser PWA

The [NoFUD PWA](https://fitguy.codeberg.page/NoFUD/app/) runs in any modern browser on phone, tablet, or desktop (including desktop webcams for meal photos and barcode). Install to the home screen or dock when offered. Covers diary, progress, BYOK AI entry and Coach, settings, and onboarding. Diary and body-metrics JSON match the Android app. Chromium-based browsers work best for install, camera barcode, and speech; Firefox and Safari work with some feature gaps.

**Android-app extras:** Health Connect, widgets, notifications, on-device Gemma 4, and the full 15-language pack.

## On-device AI (opt-in, Android app)

**On-Device (Private)** in Settings → AI Provider runs Gemma 4 Edge (E2B or E4B) via LiteRT-LM. One-time download (~2.4–3.4 GB). No API key and no server upload for food text or photo analysis. Cloud AI remains more accurate; optional fallback retries in the cloud when on-device fails. The PWA uses BYOK cloud AI only.

## AI Coach

Chat with your own provider key on both clients. Optional fallback provider. Replies follow the app language on Android (PWA is EN-first).

## Diet modes

Including keto carb mode. Goals, meal advice, and Coach stay in sync on both clients.

## Progress & Health Connect

Weight, body fat, calorie history, and goals on both clients. On the Android app: steps/exercise and wellness (sleep, HR, hydration) via Health Connect. Two-way sync; live meal import from other apps; optional background sync (off by default). Works with Gadgetbridge, openScale, Samsung Health, and other Health Connect companions. No vendor SDKs.

## Water, widgets, export

Optional local water log. Android app: reminders and home-screen calorie, protein, metrics, and water widgets. Diary export (JSON / Markdown / CSV), weight & body-metrics import/export, meal sharing, and bulk JSON import on both clients.

## Why this fork

NoFUD started when upstream [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai) briefly shipped AdMob. Huge thanks to **Apoorv Darshan** and Fud&nbsp;AI. This fork keeps that core BYOK food logger and adds an Android app plus browser PWA on Codeberg. Prefer workouts and the full upstream set? Use [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai).

| Feature | [Fud&nbsp;AI](https://github.com/apoorvdarshan/fud-ai) | NoFUD Android | [NoFUD&nbsp;PWA](https://fitguy.codeberg.page/NoFUD/app/) |
|---------|--------|---------------|-----------|
| Banner ads | Brief AdMob; removed in 3.0.3 | **Never shipped** | **Never shipped** |
| On-device AI (Gemma&nbsp;4) | No | **Yes** (opt-in) | No (BYOK cloud) |
| Barcode | ML Kit | **FOSS zxing-cpp** | Browser / OFF |
| Workouts library | Yes (~120&nbsp;MB APK) | **Omitted** ({{< apk_arm64_note >}} arm64) | Omitted |
| Keto / diet modes | No | **Yes** | **Yes** |
| Health Connect | Partial | **Steps, exercise, sleep, HR, hydration** | No |
| Widgets / notifications | Upstream set | **Yes** | No (installable PWA) |
| Languages | Upstream | **15** | EN-first |
| Distribution | Play-focused | **Codeberg** / Obtainium / F-Droid | **PWA** in any modern browser |
| Open diary / body JSON | Upstream formats | **Yes** | **Same contracts as Android** |

- **No ads, no analytics:** never shipped AdMob; no tracking SDKs
- **FOSS barcode:** zxing-cpp on Android; browser / Open Food Facts in the PWA
- **Lean APK:** no workouts tab or bundled exercise library ({{< apk_arm64_note >}} arm64 / {{< apk_universal_note >}} universal in v{{< site_version >}}, vs ~120 MB upstream with workouts)
- **Open data:** export diary and body metrics; import JSON, CSV, openScale, Health Connect (Android app)
- **PWA + Android:** installable PWA in any modern browser; Material 3 Android app; shared JSON contracts

Full comparison also lives in the [project README](https://codeberg.org/fitguy/NoFUD). Upstream: [Fud&nbsp;AI releases](https://github.com/apoorvdarshan/fud-ai/releases).
