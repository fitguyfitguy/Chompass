---
title: Download
description: Open the NoFUD browser PWA in any modern browser, or install the Android app from Obtainium or Codeberg Releases.
layout: single
---

Two ways to use NoFUD: the **installable browser PWA**, or the **Android app**. Same diary and body-metrics JSON contracts; no cloud sync between clients.

## Web app (PWA)

Open the [NoFUD web app](https://fitguy.codeberg.page/NoFUD/app/) in any modern browser on phone, tablet, or desktop. Install it to the home screen or dock when your browser offers that. Diary logging, progress charts, BYOK AI food entry and Coach, and shared JSON export/import with the Android app.

Works in Firefox, Safari, and other engines. **Chromium-based browsers** (Chrome, Edge, Brave, Cromite, and similar) generally have the smoothest install prompt, camera barcode, and Web Speech support.

**Android-app features not on the PWA:** Health Connect, home-screen widgets, notifications, on-device Gemma 4, and the full 15-language pack.

## Android: Obtainium (recommended)

[Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fcodeberg.org%2Ffitguy%2Fnofud). That confirms the Codeberg app source, then keeps you updated from releases.

Or paste `https://codeberg.org/fitguy/nofud` into Obtainium’s **Add App** screen.

## Android: Direct APK

Latest **arm64** (most phones): [NoFUD-fdroid-{{< site_version >}}-arm64-v8a.apk]({{< arm64_apk_url >}}) ({{< apk_arm64_note >}}).

All builds, checksums, and release notes: [Codeberg Releases](https://codeberg.org/fitguy/nofud/releases) (v{{< site_version >}}).

| APK | Use when | Size (v{{< site_version >}}) |
|-----|----------|----------------|
| **arm64-v8a** | Most modern phones | {{< apk_arm64_note >}} |
| **armeabi-v7a** | Older 32-bit devices | ~6 MiB |
| **x86_64** | Emulators or Chromebooks | ~16 MiB |
| **universal** | Only when unsure | {{< apk_universal_note >}} |

Package ID: `org.codeberg.fitguy.nofud`

## F-Droid

Package `org.codeberg.fitguy.nofud` ([expected listing](https://f-droid.org/packages/org.codeberg.fitguy.nofud/) once indexed). Not on the Play Store.

## After install

1. Complete onboarding (profile and goals).
2. For cloud AI: add a provider key under **Settings → AI Access** (a free [Google AI Studio](https://aistudio.google.com/apikey) key works for casual use).
3. On the Android app, for private on-device analysis: **Settings → AI Provider → On-Device (Private)** and download Gemma 4 once.

## Migrate from Fud AI

- **File export:** In Fud AI, export your food diary as JSON. In NoFUD (PWA or Android), open **Settings → Import Food Diary JSON**.
- **Health Connect:** Enable Health Connect in both apps and grant read permissions (Android app).

Weight and body data also import via **Settings → Import Weight & Body Data** (NoFUD JSON/CSV, openScale CSV, and common weight CSVs).

Source and issues: [codeberg.org/fitguy/NoFUD](https://codeberg.org/fitguy/NoFUD).
