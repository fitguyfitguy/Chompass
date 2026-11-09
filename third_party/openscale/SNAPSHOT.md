# Vendored openScale BLE drivers

Chompass vendors a driver-scoped slice of [oliexdev/openScale](https://github.com/oliexdev/openScale)
(GPL-3.0-or-later) for native in-app BLE scale sync. This file is the vendor
manifest: what was copied, from which commit, with which recorded deltas, and the
policies for touching any of it.

## Upstream pin

- Repository: `https://github.com/oliexdev/openScale`
- Commit: `35a67c7eea706d0c0cbd6a48b98f7b352960d0a1` (branch `master`, recorded 2026-11-03)
- Upstream license: GPL-3.0-or-later (same as Chompass; headers kept verbatim)
- BLE stack: [blessed-kotlin](https://github.com/weliem/blessed-kotlin) 3.1.0 (MIT, via JitPack) — NOT vendored, a Gradle dependency.

## Policies

(a) **Zero-delta.** Never reformat, refactor, lint-fix, or "modernize" vendored
files. The only permitted local change is the recorded delta below, applied
mechanically.

(b) **Upstream-first.** Any bug fix to vendored code goes to oliexdev/openScale
as a PR first, then is pulled back here at a new pin (update table + SHA + rerun
tests). Never diverge locally.

(c) **Sync procedure.** On an upstream release worth taking: rerun
`scripts/vendor_openscale_check.sh` at the old pin (sanity), swap the pin in this
file, re-copy changed files verbatim, re-apply the recorded deltas, rerun
`./gradlew :app:testDebugUnitTest` + `scripts/vendor_openscale_check.sh`, update
the table + SHA. Any new unresolved `com.health.openscale.*` import → vendor the
containing upstream file verbatim and add a table row (expansion rule).

(d) **Expansion rule.** If a vendored file imports something under
`com.health.openscale.*` that is not present in the vendored ring, copy the
containing upstream file at the same pin verbatim and add a table row here.
This is mechanical: no judgment, no adaptation.

## Recorded deltas

Only these deviations from upstream bytes are allowed:

1. **R-IMPORT** — `import com.health.openscale.R` → `import app.chompass.R`.
   Rows in the table marked `R-IMPORT` carry exactly this one-line change; the
   check script applies it to the fetched upstream copy before hashing.
2. **String closure** — all `R.string.*` keys referenced by vendored files are
   copied verbatim into `android/app/src/main/res/values/openscale-vendor.xml`
   (English-only). Exception: `gender_male` / `gender_female` reuse
   Chompass's existing keys (identical English text, already translated).
3. **Room annotations** — vendored entities reference `androidx.room.*`
   annotations; Chompass has no Room. Satisfied by the annotation-only artifact
   `androidx.room:room-common` 2.8.5 (no Room runtime, no code generation).
4. **Splits** (below) — verbatim declarations extracted from larger upstream
   files whose full contents would cascade in openScale's app layer.
5. **HOLD** — `deltas/standard-weight-profile-hold.patch`, applied after
   R-IMPORT on `StandardWeightProfileHandler.kt` only. The BF105 repeats the
   weight indication before the body-composition packet. Publishing on that
   repeat clears the buffer and drops fat. The patch holds the weight until
   composition arrives, or for 3 seconds. On a pin bump, re-apply this patch.
   If it fails, the check stops. Do not hand-merge the handler. Upstream the
   patch, then drop this delta.

## Vendored files

Tree path = upstream path with the `android_app/` prefix → `android/`.
Hashes are of the **tree copy** after the recorded deltas. Verified by
`scripts/vendor_openscale_check.sh`.

| Upstream path | Delta | sha256 (tree copy) |
|---|---|---|
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/ScaleCommunicator.kt | - | 56cb09ccfaceadec97949c47c9925ae6336521e449c4eec35cabf65ac4b953b3 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/data/ScaleMeasurement.kt | - | 01ac1bb7b657f4fd1178a12aa5d231f07fa3f0888ce93d35e3eaf50907987b26 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/data/ScaleUser.kt | - | af75d82bb8b60cdb85091d746f4e0f9e4fcbb06a89371db36aeefae8e4513617 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/libs/BeurerBf1000Lib.kt | - | 2e0ab8b5c61a5edae6f06402d689073e0c7634b17719d7c75c241939f55bd058 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/libs/StandardImpedanceLib.kt | - | 9910978dfab37c1bf7aed6b5ba69c007a5e37fc065dce10f27afa4efda8b81ef |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/BeurerBF450Handler.kt | - | 89c4c248c53803e7eace41f770cb99d5ab4b226b06ca97bb21622405be532532 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/BeurerSanitasHandler.kt | R-IMPORT | dede8b6394d5483dbd159976bf27a79a32aa16106aa6d781a0120a0575971487 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/MGBHandler.kt | R-IMPORT | fc90289c776bdfc305a6a81f25a35f770dad15f9c6e015d6b279052cb0cb5ea1 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/MedisanaBs44xHandler.kt | R-IMPORT | 3f8c1bd292c6e886331bd1c6fa00f75ee6ff7c1e7aba7d31bb5c189381b110f9 |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/ScaleDeviceHandler.kt | R-IMPORT | a59908aa742b07f2c0f72ad240edeb9e9d6188497e97d8c923a15a35465572dd |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/StandardBeurerSanitasHandler.kt | R-IMPORT | ba8ffffbd4ecd2b74646fc1db19e4f87b7eab2744e39703aadc0142b8e7df84b |
| android_app/app/src/main/java/com/health/openscale/core/bluetooth/scales/StandardWeightProfileHandler.kt | R-IMPORT+HOLD | ce56b30f4222670461c020e64307f265639cd0a6cdc1f675c651a90a83a68194 |
| android_app/app/src/main/java/com/health/openscale/core/data/Enums.kt | R-IMPORT | 8a47508f2689aa4a8f14e11f2f54d16403778aaea9c8a26be613904eceb6362b |
| android_app/app/src/main/java/com/health/openscale/core/data/Measurement.kt | - | 5e2b52be01d7c83c268574e85ad12f8700a81202e07291a34c4e706321a7af69 |
| android_app/app/src/main/java/com/health/openscale/core/data/MeasurementType.kt | R-IMPORT | 5ff214acc8a4c82778cc688c9ecefb686648eebacd1e29fbeb8939d1875ce217 |
| android_app/app/src/main/java/com/health/openscale/core/data/MeasurementValue.kt | - | 1fcf06dad853caf094aeba406b55dbf0cdb97861b4154bc997beba1fe0180fb7 |
| android_app/app/src/main/java/com/health/openscale/core/data/User.kt | - | b41c55f59f8070e495318d2926c649a93f762d8f68c71afbf3d05955282c9f4e |
| android_app/app/src/main/java/com/health/openscale/core/utils/ConverterUtils.kt | - | 2f60c2c5a8a8588ecb4d1706197ab41654c79a0e1f5509c006d15f0ed42c71ce |
| android_app/app/src/main/java/com/health/openscale/core/utils/LocaleUtils.kt | - | edbffcf5e04eacc7753b0628b793563b4ee71c76a674c35fbffb29e5f349325a |
| android_app/app/src/main/java/com/health/openscale/core/utils/LogManager.kt | - | 6c5bac81dd62ac407d6cfcf6e683ff3d43e3870e934ccfbe54b9d985f03e670d |
| android_app/app/src/main/res/drawable/ic_biceps.xml | - | 5fa3cb53e18266504c84ed8b9bcde20d779f8695cae40567fabc92b998f39904 |
| android_app/app/src/main/res/drawable/ic_bmi.xml | - | 9e8bf4154a79b4a5d9041f501b3e79fe71dd6ee9af561e23dde1e89f30af60da |
| android_app/app/src/main/res/drawable/ic_bmr.xml | - | e8e10a6e5cf657987853345a6a17390819af92369b4dd7231d51290af9657b84 |
| android_app/app/src/main/res/drawable/ic_bone.xml | - | e75da290d82834e4187f7b2e1bd2059bf4b56a20bff1a6e1805050459b8b472b |
| android_app/app/src/main/res/drawable/ic_caliper1.xml | - | f9a46177acd947d01430d17acdef147d4ebf32add6635bb619bfb8e0a3720871 |
| android_app/app/src/main/res/drawable/ic_caliper2.xml | - | dbed7b0dbc96a4bd88249867355cb2324953771136335140ae2f73ebfc04fdc4 |
| android_app/app/src/main/res/drawable/ic_caliper3.xml | - | 85ef0e3120937f0dcea14255a15d820a5a738e0a0ae27cf6025245dae86a511f |
| android_app/app/src/main/res/drawable/ic_calories.xml | - | 6792d95ae1e1ee49123c641fca5d1428c9cfd058eedf1c4a2f92f747e9247073 |
| android_app/app/src/main/res/drawable/ic_chest.xml | - | cd126f0b6931ada295bdc5658d197d8e422b6f545a9b056742a8b8c3749e4eb6 |
| android_app/app/src/main/res/drawable/ic_comment.xml | - | e41b345148af9038d809c2c76ef3f47ffd281028951895df3389ea8276ba7f88 |
| android_app/app/src/main/res/drawable/ic_date.xml | - | 580fc5fe7b3c205fb2d2d5f32879eb668f80df44d1f3993a85a1b8b4a4e75709 |
| android_app/app/src/main/res/drawable/ic_fat.xml | - | 5b19884320299d00d77a8e5e8ae032b21199552beaf37a4c999acda1e1f6bd78 |
| android_app/app/src/main/res/drawable/ic_fat_caliper.xml | - | b0368841a0944442e44633452455be843b4f9041d09a179fc4f6d38bf500c0be |
| android_app/app/src/main/res/drawable/ic_hip.xml | - | 9f5a61320df27305fd6040a4d96cd625488c2e741b23707d0bf4b0321cbeaed4 |
| android_app/app/src/main/res/drawable/ic_lbm.xml | - | 360c9f827af2682418ea6b2279710ea73863c6e7893bb6a94e8fbd8d1765afba |
| android_app/app/src/main/res/drawable/ic_muscle.xml | - | 0fa94744091c562c7c6926db51848e6f8555a3a772ee98528b3600bec29780c2 |
| android_app/app/src/main/res/drawable/ic_neck.xml | - | 96e0b075a1018c5692add29e4fde498cc7608c1c99a601e7c5295430ae32844a |
| android_app/app/src/main/res/drawable/ic_tdee.xml | - | 8577af70af83fddeff44ca362627fd31eeceb6cc9ebdd36c7d30d3505395c7b6 |
| android_app/app/src/main/res/drawable/ic_thigh.xml | - | 73118547d8452c7af916c8fb5a4569e175e4045974b793adb5f3a40a3f70f8ba |
| android_app/app/src/main/res/drawable/ic_time.xml | - | f1bf4f35682357867603f5547349c1e41a7da64e71438fbca62d5c9a84a4385d |
| android_app/app/src/main/res/drawable/ic_user.xml | - | 0849806351877acb60ccd37fc3d2346355191868a25ea2184c2ca0ea0b005b50 |
| android_app/app/src/main/res/drawable/ic_visceral_fat.xml | - | 07c7c4e07d58dc7a70f5a34360e749f9c6a52e3ab276e6c1e1fce4932627c73b |
| android_app/app/src/main/res/drawable/ic_waist.xml | - | 8ff630af8f2c90665e3f955fdca8b82b43160db24b1126bfd7a460d187965eb3 |
| android_app/app/src/main/res/drawable/ic_water.xml | - | 4759ef71c034f620c91c3a5d7a1bc0abf95a7690dfcd044edfd519cbf009508d |
| android_app/app/src/main/res/drawable/ic_weight.xml | - | 446689c00f8d1033680b4953a9aa601503e0c85f2377b7aeda2ddb2d78673125 |
| android_app/app/src/main/res/drawable/ic_whr.xml | - | 52e2e8fc8b6b0b880ca751e6e7be9db63b81ba1882d480b9f0cde39676266a9e |
| android_app/app/src/main/res/drawable/ic_whtr.xml | - | 908647483c93902f3707fa1d8ddf82e8c5054397555d31eb27d4d133c92e31b0 |


## Splits (verbatim declarations, no upstream 1:1 file)

These files are NOT in the checksum table. Their content is upstream-verbatim
declaration(s) extracted from a larger upstream file, plus the GPL header.

| Tree path | Split from (upstream) | Note |
|---|---|---|
| `android/app/src/main/java/com/health/openscale/core/service/ScannedDeviceInfo.kt` | `core/service/BleScanner.kt` | data class + KDoc copied verbatim |
| `android/app/src/main/java/com/health/openscale/core/bluetooth/scales/TuningProfile.kt` | `core/bluetooth/scales/ModernScaleAdapter.kt` | enum declaration verbatim; `forGatt()`/tuning data classes NOT vendored (adapter-layer, unused by Chompass transport) |
| `android/app/src/main/java/com/health/openscale/BuildConfig.kt` | — (Gradle-generated upstream) | Chompass-side shim object bridging `VERSION_NAME`/`VERSION_CODE`/`APPLICATION_ID`/`BUILD_TYPE` to `app.chompass.BuildConfig`; `BUILD_TIME_UTC`/`GIT_SHA` stubbed |

## Deliberately NOT vendored

- `GattScaleAdapter.kt`, `SppScaleAdapter.kt`, `ModernScaleAdapter.kt`,
  `BroadcastScaleAdapter.kt`, `ScaleFactory.kt`, `ScaleCommunicator`'s
  communicator implementations — Chompass implements its own transport
  (`app/chompass/scale/ScaleBleTransport.kt`) over blessed-kotlin.
- `core/facade/*`, `core/model/*`, `core/database/*` — Chompass repositories
  replace these.
- openScale-sync (`openScale-sync` repo) — app-to-app bridging is obsolete once
  the driver is in-app; Chompass never writes composition records to Health
  Connect.
- All other scale handlers (upstream `scales/` dir) — only the family set needed
  for Beurer/Sanitas/Medisana/MGB coverage is vendored. If the device pass finds
  an unclaimed device, vendor the missing handler at the same pin per policy (d).

## Replay fixture

`android/app/src/test/resources/openscale_beurer_frames.json` + `BeurerSanitasProtocolTest`
replay a full BF700/800 handshake through the vendored handler and assert the published
measurement. The fixture is **synthetic** (hand-built from the handler's protocol outline,
`"synthetic": true` in the file) — replace it with real frames captured via the
`ChompassScaleSync` logcat hook after the first live weigh-in, and flip the flag.

## Driver registry ordering

`MGBHandler` MUST be last in `ScaleDriverRegistry`: it claims any device
advertising service `0xFFB0` (upstream `ScaleFactory.createHandlers()` documents
this ordering constraint).
