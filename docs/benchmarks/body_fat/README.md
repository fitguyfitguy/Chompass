# Body-fat estimation: research plan

Status: **WIP** (2026-08-27). Research only. No product UI, no prompt change, no
formula register change until Track A kill criteria are scored.

**Phase 0 neck check:** CDC Examination variable lists for cycle start years
1999–2021 have **no** `BMXNECK` / neck circumference. “Neck” hits are femoral-neck
DXA. **Navy is not scorable on NHANES.** RFM is. Recorded in
`download_nhanes.py`.

Offline harness (to be built here) for comparing **deterministic formulas** and
**BYOK / on-device LLMs** on body-fat percentage. Mirrors
[`docs/benchmarks/food_accuracy/`](../food_accuracy/README.md): JSONL in, MAE /
bands out, pre-registered kill criteria, no shipping from a single lucky run.

**Related:** US Navy already in
[`BodyMeasurement.usNavyBodyFatPercent`](../../../android/app/src/main/java/app/chompass/models/BodyMeasurement.kt)
and [`chompass-core/formulas.js`](../../../web/app/src/lib/chompass-core/formulas.js);
register in [`CALCULATION_METHODS.md`](../../CALCULATION_METHODS.md) (USNAVY).
Logged BF% feeds Katch-McArdle (`BMR-KM`).

## The question

Can Gemini / OpenRouter / on-device Gemma estimate body fat to a *varying
degree* from (a) tape + weight, (b) body-part photos, (c) whole-body photos?

**Working answer:** the degree is almost entirely determined by the **input**,
not the model. A VLM can *use* body-fat signal. It cannot recover tissue density
from a selfie. That is the same information-theoretic limit already measured on
food photos (~32–40% WMAPE): missing bits stay missing.

Navy from tapes is already the honest tabular method in the app (derived
display, not auto-logged). Asking an LLM to re-derive Navy from the same
numbers is strictly worse. The research question is whether extra signal (more
sites, photos, a visual prior) **beats Navy / RFM**, not whether a model can
recite a formula.

## Capability ladder

| Layer | Input | Method | LLM needed? | Open GT? |
|-------|-------|--------|-------------|----------|
| **L0** | height, waist, (neck, hips), sex, age, weight | RFM, Navy, Deurenberg, BMI | No | Yes (NHANES, Penrose) |
| **L1** | same, as text | JSON BF% + band from BYOK / Gemma | Yes | Yes (same tables) |
| **L2** | photo → estimated tapes → **existing Navy** | Two-stage vision | Yes | Tape GT only (no public DXA+photo) |
| **L3** | photo → point BF% | Zero-shot VLM | Yes | **No** public photo+DXA |
| **L4** | photo + anthro | Hybrid | Yes | Needs paired data we do not have |

L0/L1 are the only layers that can be tested thoroughly with accessible data.
L2 is testable on circumference error without DXA. L3/L4 stay parked until GT
exists; literature CCC ≥ 0.96 is from **purpose-trained** CNNs on closed
Fenland / clinic sets, not zero-shot Gemini.

## Non-bets

Same energy as depth/AR on food. Do not build:

- Fine-tuning a DXA-paired CNN (no public data, not F-Droid-shaped).
- Zero-shot “this selfie is 18.2%” written into `bodyFatPercentage`.
- Body-part crops as the primary input (deletes the height scale).
- Sending body photos to BYOK cloud under the food-analysis consent.
- SMPL / mesh volume → BF% with constant density (food volume→kcal trap).
- Scraping Reddit / DEXA printouts (ToS, consent, no license).

## Product constraints (even if numbers look good)

1. **False precision into BMR.** A hallucinated 17% silently switches Mifflin →
   Katch-McArdle (~±90 kcal BMR per ±5 pp at 80 kg). Source-tag + band + user
   confirm, or do not write it.
2. **Privacy.** Full-body photos are not food photos.
   [`PRIVACY.md`](../../PRIVACY.md) on-device is the only default that fits.
   Cloud BYOK needs a **separate** explicit toggle.
3. **Safety filters.** Gemini / OpenAI / Anthropic will refuse or neuter the
   underwear / A-pose shots the visual method wants. **Refusal rate is a
   first-class metric** on any vision cell. On-device Gemma will run and be
   wrong instead of refusing.
4. **Bias.** Slice by sex, BMI tertile, age, ethnicity (NHANES can). Fitness-
   Reddit priors will be optimistic on lean men and poor on older women.

Explore-now **product** follow-ups (not this harness) if Track A says formulas
are the ceiling: RFM as a second derived metric next to Navy; optional “use
tape estimate as my BF%” with a ±3–4 pp caption. Out of scope here.

---

## Tracks

Build **Track A only** until its kill criteria are scored. Do not start vision
because it is more interesting.

### Track A — tabular (open data, week of work)

JSONL subjects. Baselines with no API. LLM cells against the same rows.

**Baselines (deterministic, must match in-app Navy where inputs exist):**

| ID | Formula | Inputs | Notes |
|----|---------|--------|-------|
| `rfm` | Relative Fat Mass (Woolcott & Bergman 2018) | height, waist, sex | Fit on NHANES DXA. `64 − 20×(height/waist) + 12×sex` (sex 0 male / 1 female). |
| `navy` | US Navy metric (already in-app) | height, neck, waist, (hips if female) | Reject ∉ [2, 65]% as production does. |
| `deurenberg` | BMI + age + sex | height, weight, age, sex | Dumb floor. |
| `bmi` | BMI as if it were BF% | height, weight | Control, not a claim. |
| `ridge` | Ridge on whatever sites the row has | available circumferences + age/weight | sklearn control. If this beats the LLM, the feature is a formula. |

**LLM cells:** JSON-only prompt, no CoT in the user-visible channel (same
contract as goal recalc). Models: Gemma 4 E2B (on-device or OpenRouter pin),
Gemini 3.5 Flash-Lite, Gemini 3.6 Flash, one OpenRouter free pin. Variants:

- `raw` — measurements only, no formula hint.
- `navy_anchor` — same + the Navy number labeled “rough estimate, not exact”
  (matches today’s Recalculate prompt).
- `missing_neck` — drop neck so the model cannot Navy; tests whether it beats
  RFM on waist+height.

**Kill criteria (pre-registered):**

| # | Bar | If fail |
|---|-----|---------|
| A1 | LLM MAE beats **RFM** by ≥ 0.5 pp on NHANES adult DXA | No “AI estimate from tapes” product path. |
| A2 | LLM MAE beats **Navy** on Penrose (men, hydrostatic) | Do not replace the derived Navy line with a model number. |
| A3 | `ridge` MAE ≤ LLM MAE on the same site set | Ship a formula, not an API call. |
| A4 | Parse rate ≥ 95% JSON `{bf_percent, lo, hi}` | Prompt is broken; fix before comparing MAE. |

A miss on A1–A3 is a **result**, not a failed project: it tells us to show
Navy/RFM and stop.

### Track B — photo → tapes → Navy (later)

Only after Track A exists so there is a formula floor.

VLM returns estimated neck / waist / hip cm (optional tape-in-frame vs
no-tape split). App runs **existing** `usNavyBodyFatPercent`. Score **cm
error** against tape GT, then induced Navy pp error vs tape-Navy (not vs
DXA). Refusal rate on cloud providers is a column.

No public photo+DXA set. Do not scrape. UniqueData-style photo+tape sets are
commercial; check license before any download; never commit images. A
maintainer / volunteer tape set (n≈10–30) is a smoke test, not a published
accuracy claim.

**Kill (when we get here):** waist MAE > 4 cm, or cloud refusal > 20% on
fitted-clothing A-pose, or induced Navy error worse than “user typed waist
wrong by 2 cm.” Fall back to “measure your waist.”

### Track C — zero-shot visual BF% (parked)

No JFB equivalent. Closed clinic sets (Majmudar VBC n≈134; Cambridge /
Fenland ~12k; Alves 912) are literature ceiling only. Aldajani 2025 (arxiv
2511.17576) trained a ResNet on 282 scraped self-report photos (43% claimed
DEXA); RMSE 4.44 pp is **not** a VLM number and the set is not a licensed
benchmark.

A volunteer BIA + photo set may smoke-test the pipeline. Label it `bia`,
never `dxa`. Pre-register if we ever run it: visual **band** only if ±5 pp
coverage ≥ 70% vs BIA; never auto-write Katch-McArdle.

---

## Datasets

There is **no** large, open, licensed photo + clinical BF% benchmark. HIPAA /
GDPR and medical-photo ownership make that the expected state, not a temporary
gap.

| Dataset | Modality | GT | n | License | Track |
|---------|----------|-----|---|---------|-------|
| [NHANES](https://wwwn.cdc.gov/nchs/nhanes/) DXA + body measures | tabular | DXA %fat (`DXDTOPF` / cycle equivalent), fat mass, lean | thousands of adults | US gov public | **A (primary)** |
| [Penrose / StatLib bodyfat](https://jse.amstat.org/v4n1/datasets.johnson.html) | tabular | hydrostatic + Siri %fat; 10 circumferences incl. neck, abdomen, hip, wrist | 252 **men** | public | **A (Navy + multi-site)** |
| UniqueData / TrainingDataPro body-measurements | photo + tapes | tape cm, **not** DXA | small | commercial HF; redistribution unclear | B **if** license allows; never in git |
| SMPL / SMPL-X / BEDLAM renders | synthetic 2D + mesh | volume, not BF% | large | open | geometry probe only; not a score |
| WayBED / Digital Scale (2025) | real full-body photos | **BMI**, not BF% | large | check paper release | adiposity-ranking proxy only |
| Majmudar 2022, Fenland 3D BodyShape, Alves 2023 | photo / 3D + DXA | DXA | 134 / ~12k / 912 | **closed** | literature ceiling |
| Reddit / self-report DEXA scrapes | photo | noisy self-report | ~300 | no license | **do not use** |

### NHANES caveat (phase 0 must verify)

RFM exists because NHANES has **waist + DXA** and typically **not** Navy’s
neck. Do **not** claim “Navy validated on NHANES” until a downloader confirms
a cycle with neck circumference overlapping whole-body DXA.

Known shape (verify in code, do not trust this table blindly):

| Cycles | DXA | Waist | Hip | Neck | Arm | Navy scorable? | RFM scorable? |
|--------|-----|-------|-----|------|-----|----------------|---------------|
| 1999–2006 | yes | yes | no | **no** | yes | **no** | yes |
| 2011–2016 | yes | yes | **no** in BMX_G/H/I | **no** | yes | **no** | yes |
| 2017–2018 | yes | yes | yes (`BMXHIP`) | **no** | yes | **no** | yes |

Adults only (age ≥ 18). Slice sex, ethnicity, age band, BMI tertile. Use the
CDC-recommended DXA multiple-imputation files where the cycle requires them;
do not drop incomplete scans without recording the filter.

Penrose abdomen ≈ Navy waist (umbilicus). Document the site mismatch in the
scorer notes; do not “correct” the formula.

---

## Metrics

Score **percentage points of body fat**, not relative %. A 2 pp miss at 12%
is not the same story as 20% WMAPE on a plate, and we do not reuse food
WMAPE here.

| Metric | Why |
|--------|-----|
| MAE, RMSE | headline vs DXA / hydrostatic |
| signed bias | systematic over/under (the food-photo failure mode) |
| Lin’s CCC | comparability with VBC / 3D BodyShape papers |
| % within ±2 / ±3 / ±5 pp | product bands; ±3 pp is a typical Navy SEE |
| parse rate | JSON contract |
| refusal rate | vision / safety (Track B+) |
| slices | sex, BMI tertile, age, ethnicity |

BMR translation (for write-ups, not a harness column): ±5 pp BF at 80 kg ≈
±90 kcal/day via Katch-McArdle. State that next to any “good enough for
onboarding” claim.

---

## Manifest (Track A)

JSONL, one subject per line. Paths relative to repo root. No images in Track A.

```json
{
  "id": "nhanes-2017-123456",
  "source": "nhanes_2017_2018",
  "sex": "female",
  "age": 42,
  "height_cm": 164.0,
  "weight_kg": 68.2,
  "neck_cm": null,
  "waist_cm": 81.5,
  "hips_cm": 98.0,
  "chest_cm": null,
  "upper_arm_cm": 30.1,
  "thigh_cm": null,
  "calf_cm": null,
  "wrist_cm": null,
  "bf_percent": 32.4,
  "bf_method": "dxa"
}
```

| Field | Required | Notes |
|-------|----------|-------|
| `id` | yes | stable, source-prefixed |
| `source` | yes | `nhanes_<cycle>` / `penrose` |
| `sex` | yes | `male` / `female` (NHANES other not used) |
| `age` | yes | integer years |
| `height_cm`, `weight_kg` | yes | |
| `*_cm` sites | no | null if missing; Navy needs neck+waist (+hips female) |
| `bf_percent` | yes | 0–100, not a 0–1 fraction |
| `bf_method` | yes | `dxa` / `hydrostatic` / `bia` (bia never mixed into A1) |
| `ethnicity` | no | NHANES race/hispanic code, for slices only |

Checked-in seed: a tiny Penrose-derived `manifest/eval_tabular_seed.jsonl`
(≤ 20 rows) so `uv run` smoke works without CDC downloads, analogous to
FNDDS seed. Full NHANES / Penrose live under `data/` (gitignored).

---

## Layout

```
docs/benchmarks/body_fat/
  README.md                 # this plan
  manifest/                 # checked-in seed JSONL
  data/                     # downloads (gitignored)
  results/                  # run outputs (gitignored)
  download_penrose.py
  download_nhanes.py        # DXA + BMX join; records which sites exist
  baselines.py              # rfm, navy (parity with Kotlin/JS), deurenberg, bmi, ridge
  prompts.py
  run_eval.py               # --baseline / --provider (reuse food_accuracy providers if practical)
  score.py
  compare_runs.py
```

Python: **uv ephemeral only** (no `pyproject.toml`). Reuse
`food_accuracy/providers.py` / OpenRouter pins if the import path stays
clean; do not fork a second free-router pool.

Navy in `baselines.py` must match production coefficients (see
`CALCULATION_METHODS.md` USNAVY). Add a few golden rows against
`usNavyBodyFatPercent` (Kotlin unit test already exists; JS too) so the
harness cannot drift.

## Phases

| Phase | Work | Done when |
|-------|------|-----------|
| **0** | This plan. Verify NHANES neck/hip/DXA overlap from CDC codebooks (write the finding into the downloader docstring). | **Done:** no neck any cycle. `BMXHIP` present in 2017–2018 BMX_J only among 2011–2018 XPTs we joined. |
| **1** | Penrose downloader + seed manifest + L0 baselines + scorer. Smoke on seed. | **Done** (L0). |
| **2** | NHANES downloader (adult DXA join) + slices. Score L0 on NHANES. | **Done** (L0 table below). Navy n_pred=0. |
| **3** | LLM cells (raw / navy_anchor / missing_neck) on a **fixed** NHANES subset + full Penrose. Gemma, Flash-Lite, 3.6 Flash, one free pin. | **Flash-Lite `raw` scored** (below). Other models / variants not run. |
| **4** | Product decision: formula-only vs LLM path vs “use tape as BF%” UX. | Explicit go/no-go in STATUS. Track B only on go for L2. |

No Android / PWA code in phases 0–3. If phase 4 is “ship RFM,” that is a
formula-register change (`CALCULATION_METHODS.md` checklist, Kotlin +
`chompass-core`, goldens). Separate commit from the harness.

### L0 numbers (not A1–A4; no LLM yet)

MAE in body-fat **percentage points**. NHANES adults 2011–2018 with DXA
`DXDTOPF` + waist (n=11707). Penrose n=252 men, abdomen as Navy waist.

| Method | NHANES MAE | NHANES CCC | Penrose MAE | Penrose CCC | n_pred NHANES / Penrose |
|--------|------------|------------|-------------|-------------|-------------------------|
| `rfm` | 3.17 | 0.90 | 6.49 | 0.50 | 11707 / 252 |
| `navy` | — | — | 4.19 | 0.76 | **0** / 252 |
| `deurenberg` | 4.66 | 0.82 | 7.11 | 0.34 | 11707 / 252 |
| `bmi` (control) | 6.70 | 0.51 | 7.70 | 0.29 | 11707 / 252 |
| `ridge` OOF | 2.89 | 0.90 | 5.10* | 0.20* | 11707 / 252 |

\*Penrose ridge RMSE is inflated by the known Johnson height outlier; MAE is
the headline. Full JSON under gitignored `results/`.

### Flash-Lite `raw` (A1–A4)

`google/gemini-3.5-flash-lite` via OpenRouter. NHANES: stratified n=200
(`sample_subset.py --n 200 --seed 0`). Penrose: full n=252. Prompt variant `raw`.

| Cell | LLM MAE | Floor | Kill |
|------|---------|-------|------|
| A1 NHANES vs RFM (≥0.5 pp better) | 3.67 | RFM 3.00 | **fail** (worse by 0.67) |
| A2 Penrose vs Navy | 4.65 | Navy 4.19 | **fail** |
| A3 ridge ≤ LLM (same rows) | NHANES ridge 2.84 | LLM 3.67 | **A3 trips** (formula wins) |
| A4 parse `{bf_percent,lo,hi}` ≥95% | 200/200 and 252/252 | | **pass** |

A miss on A1–A3 is the planned result: show Navy/RFM, do not ship an “AI
estimate from tapes.” Track B stays parked. Gemma / 3.6 Flash not required to
re-litigate A1 unless someone expects a different model to beat RFM by half a
point on the same 200 rows.

## Literature ceiling (do not treat as harness targets)

| Paper | What they did | Claimed error | Data |
|-------|---------------|---------------|------|
| Woolcott & Bergman 2018 | RFM vs NHANES DXA | better than BMI; adult FM% | **open** (NHANES) |
| Hodgdon & Beckett 1984 | Navy circumferences vs hydrostatic | SEE ~3–4 pp | military, not public microdata |
| Majmudar et al. 2022 npj | trained VBC, front+back smartphone | CCC ~0.96 vs DXA; independent 4C follow-ups less kind | closed, n≈134 |
| Cambridge / Fenland 2024 npj | 3D surface → DXA | RMSE ≲ 3.5 pp; change r=0.92 | closed, ~12k |
| Alves 2023 Measurement | 4 photos + anthro → DXA | sex-split DL | closed, 912 |
| Aldajani 2025 arxiv:2511.17576 | ResNet on 282 scraped photos; separate Penrose regression | CNN RMSE 4.44 pp on self-report | scrape, not a benchmark |

Zero-shot Gemini / Gemma 4 E2B are not those trained models. Food-photo
Gemma already sits at ~32–40% WMAPE; do not expect Fenland-level BF%.

## Privacy / license rules for this directory

- No body photographs in git, ever (including `manifest/` and debug assets).
- No diary / DEXA PDF / Reddit dumps.
- NHANES and Penrose are tabular public data; OK to cache under `data/`.
- CC BY-NC photo sets, if any later, are research-only numbers: not product
  claims, same rule as ACETADA.
- Cloud vision cells (Track B+) must not use production user photos.
