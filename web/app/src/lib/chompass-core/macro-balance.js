// @ts-check

/**
 * Pure macro rebalance shared by every write path that persists a calorie
 * target — JS mirror of Android `MacroBalance.kt` (Settings calorie edit, AI
 * goal snapshots, day-type/phase rescales). Golden vectors:
 * `testdata/parity/macro-balance-expected.json`.
 *
 * Invariants (see the Kotlin doc for the full contract):
 *  - locked macros are never changed;
 *  - STANDARD keeps unlocked protein and fat, carbs absorb the remainder;
 *    fat yields first but never below `fatFloorG` while protein can give way;
 *  - KETO keeps unlocked protein and carbs, fat fills the target floored at
 *    `fatFloorG`; protein yields, then carbs, when the target cannot fit;
 *  - when everything relevant is locked and the target cannot fit, the locked
 *    values come back unchanged.
 *
 * All division is `Math.trunc` on non-negative values, matching Kotlin `Int`
 * division bit-for-bit. 4/4/9 kcal per gram.
 */

/**
 * @param {number} calories
 * @param {number} protein
 * @param {number} carbs
 * @param {number} fat
 * @param {"standard"|"keto"} mode
 * @param {Iterable<string>} locked macro ids ("protein"|"carbs"|"fat")
 * @param {number} fatFloorG
 * @returns {{protein: number, carbs: number, fat: number}}
 */
export function rebalanceGoalMacros(calories, protein, carbs, fat, mode, locked, fatFloorG) {
  const lockedSet = new Set(locked ?? []);
  return mode === "keto"
    ? rebalanceKeto(calories, protein, carbs, fat, lockedSet, fatFloorG)
    : rebalanceStandard(calories, protein, carbs, fat, lockedSet, fatFloorG);
}

/**
 * @param {number} calories
 * @param {number} protein
 * @param {number} carbs
 * @param {number} fat
 * @param {Set<string>} locked
 * @param {number} floor
 * @returns {{protein: number, carbs: number, fat: number}}
 */
function rebalanceStandard(calories, protein, carbs, fat, locked, floor) {
  const t = Math.max(0, Math.trunc(calories));
  let p = Math.max(0, Math.trunc(protein));
  const c = Math.max(0, Math.trunc(carbs));
  let f = Math.max(0, Math.trunc(fat));
  const proteinLocked = locked.has("protein");
  const carbsLocked = locked.has("carbs");
  const fatLocked = locked.has("fat");
  if (!fatLocked) f = Math.max(f, floor);
  if (t >= 4 * p + 9 * f) {
    return { protein: p, carbs: carbsLocked ? c : Math.max(0, Math.trunc((t - 4 * p - 9 * f) / 4)), fat: f };
  }

  if (proteinLocked) {
    if (!fatLocked) f = Math.max(0, Math.trunc((t - 4 * p) / 9)); // floor may break
  } else if (t >= 4 * p + 9 * floor) {
    // Protein can stay; reduce fat to fit, never below the floor.
    if (!fatLocked) f = Math.min(f, Math.trunc((t - 4 * p) / 9));
  } else {
    // The floor cannot fit with this protein; reduce protein to preserve it.
    p = Math.max(0, Math.trunc((t - 9 * floor) / 4));
    if (t < 4 * p + 9 * floor) {
      if (!fatLocked) f = Math.max(0, Math.trunc((t - 4 * p) / 9)); // last resort: break the floor
    } else if (!fatLocked) {
      f = floor;
    }
  }
  // Last resort if a locked fat (or rounding) left the target over: protein yields.
  if (t < 4 * p + 9 * f && !proteinLocked) p = Math.max(0, Math.trunc((t - 9 * f) / 4));
  if (t < 4 * p + 9 * f && !fatLocked) f = Math.max(0, Math.trunc((t - 4 * p) / 9));
  const finalCarbs = carbsLocked ? c : Math.max(0, Math.trunc((t - 4 * p - 9 * f) / 4));
  return { protein: p, carbs: finalCarbs, fat: f };
}

/**
 * @param {number} calories
 * @param {number} protein
 * @param {number} carbs
 * @param {number} fat
 * @param {Set<string>} locked
 * @param {number} floor
 * @returns {{protein: number, carbs: number, fat: number}}
 */
function rebalanceKeto(calories, protein, carbs, fat, locked, floor) {
  const t = Math.max(0, Math.trunc(calories));
  let p = Math.max(0, Math.trunc(protein));
  const c = Math.max(0, Math.trunc(carbs));
  let f = Math.max(0, Math.trunc(fat));
  const proteinLocked = locked.has("protein");
  const carbsLocked = locked.has("carbs");
  const fatLocked = locked.has("fat");
  if (!fatLocked) f = Math.max(Math.max(0, Math.trunc((t - 4 * p - 4 * c) / 9)), floor);
  if (t - 4 * p - 4 * c - 9 * f >= 0) return { protein: p, carbs: c, fat: f };
  if (!proteinLocked) p = Math.min(p, Math.max(0, Math.trunc((t - 4 * c - 9 * floor) / 4)));
  if (!fatLocked) f = Math.max(0, Math.trunc((t - 4 * p - 4 * c) / 9));
  if (t - 4 * p - 4 * c - 9 * f < 0 && !proteinLocked) p = Math.max(0, Math.trunc((t - 4 * c - 9 * f) / 4));
  if (t - 4 * p - 4 * c - 9 * f < 0 && !carbsLocked) {
    const finalCarbs = Math.max(0, Math.trunc((t - 4 * p - 9 * f) / 4));
    return { protein: p, carbs: finalCarbs, fat: f };
  }
  return { protein: p, carbs: c, fat: f };
}
