---
title: Download
description: Open the Chompass browser PWA in any modern browser, or install the Android app from Obtainium or Codeberg Releases.
layout: single
---

Two ways to use Chompass: the **installable browser PWA**, or the **Android app**. Same diary and body-metrics JSON contracts; no cloud sync between clients.

## Web app (PWA)

Open the [Chompass web app](https://chompass.app/app/) in any modern browser on phone, tablet, or desktop. Install it to the home screen or dock for quicker access. Diary logging, progress charts, AI food entry and Coach with your own key, and shared JSON export/import with the Android app.

Works in Firefox, Safari, and other engines. **Chromium-based browsers** (Chrome, Edge, Brave, Cromite, and similar) generally have the smoothest install prompt, camera barcode, and Web Speech support.

**Android-app features not on the PWA:** Health Connect, home-screen widgets, notifications, on-device Gemma 4, and the full 15-language pack.

### How to install

#### iPhone / iPad (Safari)

1. Open Chompass in **Safari** (required for a true home-screen app).
2. Tap **Share** (square with an upward arrow).
3. Choose **Add to Home Screen**, then Add.
4. Open from the **home-screen icon** (not a Safari tab) for the full-screen app.

Chrome or Firefox on iOS still use Safari’s share sheet for home-screen install. Diary and body-metrics exports use the system Share sheet when available (Save to Files / AirDrop).

#### Android (Chrome, Edge, Brave)

1. Open the browser menu (⋮).
2. Tap **Install app** or **Add to Home screen**.
3. Confirm, then open Chompass from the new icon.

Many Chromium browsers do not show an automatic install popup. Use the menu. In the PWA itself you may also see an Install banner when the browser allows it.

#### Desktop (Chrome / Edge)

1. Look for the install icon in the address bar, or open the browser menu.
2. Choose **Install Chompass** (or Install app).
3. Launch from your dock, taskbar, or app launcher.

#### Firefox (Android)

1. Tap the Firefox menu (⋮).
2. Tap **Add to Home screen** or **Add app to Home screen**.
3. Confirm, then open from the new icon.

Firefox has no in-page install popup. If the menu item does nothing, set a Home app under Android Settings → Apps → Default apps (it must not be “None”). Desktop Firefox: bookmark the page; full PWA install is limited.

#### DuckDuckGo (Android)

DuckDuckGo does not fully support PWA install. Menu → **Add to Home** may create a shortcut only, and on some launchers it does nothing. For a full-screen installed app, open [the web app](https://chompass.app/app/) in Chrome, Edge, or Brave instead.

Already installed? Open Chompass from the home-screen or dock icon (not a normal browser tab) for the full-screen shell. In the app: **Settings → Install app**.

## Android: Obtainium (recommended)

[Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fcodeberg.org%2Ffitguy%2Fchompass). That confirms the Codeberg app source, then keeps you updated from releases.

Or paste `https://codeberg.org/fitguy/chompass` into Obtainium’s **Add App** screen.

## Android: Direct APK

Latest **arm64** (most phones): [Chompass-fdroid-{{< site_version >}}-arm64-v8a.apk]({{< arm64_apk_url >}}) ({{< apk_arm64_note >}}).

All builds, checksums, and release notes: [Codeberg Releases](https://codeberg.org/fitguy/chompass/releases) (v{{< site_version >}}).

| APK             | Use when                 | Size (v{{< site_version >}}) |
| --------------- | ------------------------ | ---------------------------- |
| **arm64-v8a**   | Most modern phones       | {{< apk_arm64_note >}}       |
| **armeabi-v7a** | Older 32-bit devices     | ~6 MiB                       |
| **x86_64**      | Emulators or Chromebooks | ~16 MiB                      |
| **universal**   | Only when unsure         | {{< apk_universal_note >}}   |

Package ID: `app.chompass`

## F-Droid

The listing is in preparation. Package `app.chompass` ([expected listing](https://f-droid.org/packages/app.chompass/)). Not on the Play Store.

## After install

1. Complete onboarding (profile and goals).
2. For cloud AI: add a provider key under **Settings → AI Access** (a free [Google AI Studio](https://aistudio.google.com/apikey) key works for casual use).
3. On the Android app, for private on-device analysis: **Settings → AI Provider → On-Device (Private)** and download Gemma 4 once.

## Migrate from Fud AI

- **File export:** In Fud AI, export your food diary as JSON. In Chompass (PWA or Android), open **Settings → Import Food Diary JSON**.
- **Health Connect:** Enable Health Connect in both apps and grant read permissions (Android app).

Weight and body data also import via **Settings → Import Weight & Body Data** (Chompass JSON/CSV, openScale CSV, and common weight CSVs).

Source and issues: [codeberg.org/fitguy/chompass](https://codeberg.org/fitguy/chompass).
