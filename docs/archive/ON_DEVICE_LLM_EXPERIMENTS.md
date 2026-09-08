# On-device LLM: archived experiment records (rotated 2026-09-08)

Experiment examples, the filled-in experiment log, the production decision
gate, and the closed what-next list, rotated out of
[`docs/ON_DEVICE_LLM.md`](../ON_DEVICE_LLM.md). Production integration,
intent extras, smoke-test reference, and validated results stay in the live
doc. Content below is verbatim.

### Experiment examples

```powershell
# Exp 1a: few-shot unit_options (Tier A only)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units

# Exp 1b: two-pass unit inference (Tier A only)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt twopass

# Exp 2a: compact prompt latency
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact

# Exp 2b: warm repeat (second pass uses GPU cache)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_tier a --es ondevice_llm_prompt compact --ei ondevice_llm_repeat 2

# Exp 2c: MTP cold init + warm repeat (allow 3–5 min for first engineInit)
adb shell am force-stop app.chompass.debug
adb logcat -c
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --ez ondevice_llm_mtp true --es ondevice_llm_tier a --es ondevice_llm_prompt fewshot_units --ei ondevice_llm_repeat 2
adb logcat -s FudOnDeviceLlm

# Exp 3: FunctionGemma: SKIPPED (no suitable artifact for this app: see experiment log)

# Exp 4: Gemma 4 E4B full run (optional quality comparison)
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez run_ondevice_llm_test true --es ondevice_llm_model gemma-4-E4B-it.litertlm
```

## Experiment log

Record results from `adb logcat -s FudOnDeviceLlm` after each run. Seed test data first if Tier C needs logged entries:

```powershell
adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez seed_test_data true
```

| Experiment | Command flags | Sample 0 `unitOptions` | Tier A ms (GPU) | Tier C grounding | Notes |
|------------|---------------|------------------------|-----------------|------------------|-------|
| **Baseline** (2026-07-14 AM) | default (`full`, E2B) | 0/3 | ~23–26 s | 4/4 good post-JsonElement fix | See validated results above |
| **Exp 1a** `fewshot_units` (2026-07-14 PM) | `tier=a prompt=fewshot_units` | **2** (pizza+coke) | ~30 / ~25 / ~28 s (samples 0–2) | n/a | **PASS:** 3/3 parse ok; unitOptions 2/1/2; promptChars≈1494; engineInit ~29 s |
| **Exp 1b** `twopass` | - | - | - | **Skip:** Exp 1a + MTP covers units |
| **Exp 2a** `compact` (2026-07-14 PM) | `tier=a prompt=compact` | 0/3 | **~14 / ~7 / ~7 s** (samples 0–2) | n/a | promptChars≈653; ~2× faster than `fewshot_units`; loses `unit_options`; engineInit ~29 s |
| **Exp 2b** warm repeat (2026-07-14 PM) | `tier=a prompt=compact repeat=2` | 0/3 | pass0: ~13/7/7 s; pass1: ~12/7/7 s | n/a | ~4% faster pass1 on sample 0 only; samples 1–2 already warm; single engineInit ~26 s |
| **Exp 2c** MTP + fewshot (2026-07-14 PM) | `mtp=true tier=a prompt=fewshot_units repeat=2` | **2** (all passes) | pass0: **~18 / ~15 / ~17 s**; pass1: ~17 / ~15 / ~17 s | n/a | **PASS:** ~**1.6×** vs non-MTP fewshot; 6/6 parse ok; unitOptions 2/1/2; engineInit ~30 s; no truncation |
| **Exp 3** FunctionGemma | - | - | - | **Skip:** Tensor G5: GPU init fail; `mobile_actions`: wrong fine-tune (Mobile Actions demo, not Coach) |
| **Exp 4** E4B | - | - | - | Skipped in this smoke-test harness (E2B adequate for the harness). E4B *is* offered in production (`ModelCatalog.E4B`) and was later found to OOM-kill the app on Pixel 9a when combined with photo analysis; see note 9 above for the CPU/GPU-split + preflight-memory-check mitigation now in place. Still not run through this harness. |
| **Exp 5** `six_round_chain` + Tier C (2026-07-14 PM) | `tier=c` (E2B, no MTP) | n/a | n/a | **4/5 good** | See Tier C breakdown below; `six_round_chain` partial; wrong counts, missed propose_log_* |
| **Tier B** vision (2026-07-14 PM) | `tier=b`, real photo fixtures | n/a | n/a (4× ~21–34 s) | n/a | **PASS:** 4/4 json ok; multi-turn ok; no MTP |
| **Daily run 1** | `tier=daily`, cold cache, no MTP | 2/1/2 (text) | 4/4 (photo) | 2/2 Coach | tierA 89 s / tierB 102 s / tierC 20 s / total 211 s | **PASS:** no-MTP baseline |
| **Daily run 4** | `preset=daily`, warm MTP cache | 2/1/2 (text) | 4/4 (photo) | 2/2 Coach | tierA 60 s / tierB 69 s / tierC 18 s / total 148 s | **PASS:** recommended daily-driver preset |

---

## Decision gate (production integration)

Fill in after experiment log runs on Pixel 9a:

| Use case | Go if… | Hybrid if… | No-go if… |
|----------|--------|------------|-----------|
| **Tier A** text food log | `fewshot_units` + MTP ≤ ~20 s GPU **and** JSON 3/3 with units | compact ~7 s without units; fewshot ~25–30 s without MTP | parse failures or >45 s after best prompt combo |
| **Tier B** photo food log | MTP ~15–19 s/fixture **and** JSON 4/4 with units | no MTP ~21–34 s; disclose latency | parse failures or multi-turn crash |
| **Tier C** Coach | E2B passes 4/5 scenarios (Exp 5) | Tier C **cloud default**; optional on-device E2B for simple offline queries | tool results ignored, or long chains consistently fail |
| **`unit_options`** | Exp 1a `fewshot_units` fixes all samples (2026-07-14) | two-pass if few-shot too token-heavy for production | both fewshot and twopass fail on pizza sample |

**Final assessment (experiments complete, 2026-07-14):**

| Area | Verdict | Production note |
|------|---------|-----------------|
| **Tier A** | **Go (hybrid UX)** | `fewshot_units` + MTP ~16–26 s GPU; units ok. Disclose latency in Settings. |
| **Tier B** | **Go (hybrid UX)** | `analyzeFood`/`analyzeAuto` prompts + MTP ~15–19 s/photo when warm; 4/4 json ok on real fixtures. |
| **Tier C** | **Hybrid** | E2B 4/5 on harness; cloud Coach for compound requests. On-device E2B optional for offline simple queries (~9 s). |
| **`unit_options`** | **Go** | Add few-shot block to production `analyzeText` prompt (mirror harness). |
| **MTP** | **Go for Tier A + Tier B** | ~30% faster vision with MTP when warm; enable after first init or keep process warm |
| **Model** | **Gemma 4 E2B-it** | Single on-device model for text + vision + simple Coach offline |

**Next milestone (separate from experiments):** production wiring: `ApiFormat.ON_DEVICE`, Settings provider + model-file check, `FoodAnalysisService` dispatch (Tier A first), optional Coach branch later.

**Tier C scenario results (Exp 5, E2B GPU, no MTP):**

| Scenario | ms | Tool calls | Grounding | Verdict |
|----------|-----|------------|-----------|---------|
| `single_tool` | ~10.7 s | `get_food_entries` | Listed all 4 foods + kcal | **Pass** |
| `ambiguous` | ~9.2 s | `get_data_summary` | Cited real counts; reasonable follow-up | **Pass** |
| `multi_round_chain` | ~10.2 s | `get_calorie_totals` + `propose_log_water` | ~1750 kcal avg + water proposal | **Pass** |
| `malformed_recovery` | ~15.0 s | summary + truncated weight JSON | Answered from summary + partial weights | **Pass** (intentional corrupt) |
| `six_round_chain` | ~31.0 s | 4 tools (summary, weight, calories, food) | Summary partly wrong; no propose_log calls | **Partial** |

**Suggested production prompt strategy:** `fewshot_units` + **MTP** for Tier A and Tier B on-device; disclose ~15–35 s latency in Settings; Tier C stays **cloud default**, on-device E2B for offline simple Coach only.

---

## What next

**Tier A/C/B experiments + daily matrix: complete** (2026-07-14).

| Step | Status |
|------|--------|
| Exp 1a, 2a/2b/2c, 5 | **Done** |
| Exp 1b, 3, 4 | **Skipped** |
| **Tier B vision harness** | **Done:** 4/4 fixtures, multi-turn OK |
| **Daily matrix runs 1 + 4** | **Done:** baseline + daily-driver preset PASS |
| **Production integration** | **Next:** Tier A + B on-device provider behind Settings; Tier C stays cloud-first |

See **Final assessment** under Decision gate above. Run Tier B + daily matrix before production wiring; re-test on litertlm upgrades.

---
