---
title: Download
description: Install NoFUD from Obtainium or Codeberg Releases. Android only — not on the Play Store. F-Droid metadata submitted.
layout: single
---

Not on the Play Store. F-Droid metadata is submitted; until the package appears in the F-Droid client, install from Obtainium or Codeberg Releases.

## Obtainium (recommended)

[Get it on Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fadd%2Fhttps%3A%2F%2Fcodeberg.org%2Ffitguy%2Fnofud) — confirms the Codeberg app source, then keeps you updated from releases.

Or paste `https://codeberg.org/fitguy/nofud` into Obtainium’s **Add App** screen.

## Direct APK

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

Package `org.codeberg.fitguy.nofud` — [expected listing](https://f-droid.org/packages/org.codeberg.fitguy.nofud/) once indexed.

## After install

1. Complete onboarding (profile and goals).
2. For cloud AI: add a provider key under **Settings → AI Access** (a free [Google AI Studio](https://aistudio.google.com/apikey) key works for casual use).
3. For private on-device analysis: **Settings → AI Provider → On-Device (Private)** and download Gemma 4 once.

## Migrate from Fud AI

- **File export:** In Fud AI, export your food diary as JSON. In NoFUD, open **Settings → Import Food Diary JSON**.
- **Health Connect:** Enable Health Connect in both apps and grant read permissions.

Weight and body data also import via **Settings → Import Weight & Body Data** (NoFUD JSON/CSV, openScale CSV, and common weight CSVs).

Source and issues: [codeberg.org/fitguy/NoFUD](https://codeberg.org/fitguy/NoFUD).
