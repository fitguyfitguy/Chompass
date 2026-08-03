// @ts-check
/**
 * In-memory progressive meal draft (Android ProgressiveMealDraft parity).
 * Session-only — survives route changes within the tab, cleared on Log / Discard.
 */

/**
 * @typedef {Object} ProgressiveMealItem
 * @property {string} id
 * @property {import('./chompass-core/models.js').FoodEntry} analysis  reviewed nutrition (scaled)
 * @property {string} [mealType]
 * @property {string} [source]
 * @property {string|null} [selectedServingUnit]
 * @property {number|null} [selectedServingQuantity]
 */

/**
 * @typedef {Object} ProgressiveMealDraft
 * @property {string} name
 * @property {string} mealType
 * @property {ProgressiveMealItem[]} items
 */

/** @type {ProgressiveMealDraft|null} */
let draft = null;

/** When true, home should open the camera/photo flow after closing the meal sheet. */
let resumeCapture = false;

/** When true, home should show the progressive meal sheet once. */
let showSheet = false;

export function getProgressiveMeal() {
  return draft;
}

export function hasProgressiveMealItems() {
  return (draft?.items.length ?? 0) > 0;
}

export function progressiveMealItemCount() {
  return draft?.items.length ?? 0;
}

export function consumeShowProgressiveMealSheet() {
  const show = showSheet && hasProgressiveMealItems();
  showSheet = false;
  return show;
}

export function setShowProgressiveMealSheet(show) {
  showSheet = Boolean(show) && hasProgressiveMealItems();
}

export function consumeResumeProgressiveCapture() {
  const resume = resumeCapture && hasProgressiveMealItems();
  resumeCapture = false;
  return resume;
}

/**
 * @param {Object} opts
 * @param {import('./chompass-core/models.js').FoodEntry} opts.analysis
 * @param {string} [opts.mealType]
 * @param {string} [opts.source]
 * @param {string|null} [opts.selectedServingUnit]
 * @param {number|null} [opts.selectedServingQuantity]
 * @param {boolean} [opts.resumeCapture]
 */
export function addToProgressiveMeal(opts) {
  const analysis = opts.analysis;
  const mealType = opts.mealType || analysis.mealType || "snack";
  /** @type {ProgressiveMealItem} */
  const item = {
    id: crypto.randomUUID(),
    analysis,
    mealType,
    source: opts.source || analysis.source || "ai_estimated",
    selectedServingUnit: opts.selectedServingUnit ?? analysis.selectedServingUnit ?? null,
    selectedServingQuantity: opts.selectedServingQuantity ?? analysis.selectedServingQuantity ?? null,
  };
  const existing = draft;
  draft = {
    name: existing?.name ?? "",
    mealType: existing?.mealType ?? mealType,
    items: [...(existing?.items ?? []), item],
  };
  resumeCapture = Boolean(opts.resumeCapture);
  showSheet = !resumeCapture;
  return draft;
}

/** @param {string} id */
export function removeProgressiveMealItem(id) {
  if (!draft) return;
  const remaining = draft.items.filter((it) => it.id !== id);
  if (remaining.length === 0) {
    draft = null;
    showSheet = false;
    resumeCapture = false;
  } else {
    draft = { ...draft, items: remaining };
  }
}

/**
 * @param {string} name
 * @param {string} mealType
 */
export function updateProgressiveMealMeta(name, mealType) {
  if (!draft) return;
  draft = { ...draft, name: name ?? draft.name, mealType: mealType || draft.mealType };
}

export function discardProgressiveMeal() {
  draft = null;
  showSheet = false;
  resumeCapture = false;
}

/** @param {ProgressiveMealDraft} d */
export function draftTotals(d) {
  return {
    calories: d.items.reduce((s, it) => s + (Number(it.analysis.calories) || 0), 0),
    proteinG: d.items.reduce((s, it) => s + (Number(it.analysis.proteinG) || 0), 0),
    carbsG: d.items.reduce((s, it) => s + (Number(it.analysis.carbsG) || 0), 0),
    fatG: d.items.reduce((s, it) => s + (Number(it.analysis.fatG) || 0), 0),
  };
}

/**
 * Build diary rows sharing one recipeLogId (Android ProgressiveMealDraft.toFoodEntries).
 * @param {ProgressiveMealDraft} d
 * @param {Object} opts
 * @param {string} opts.date
 * @param {string} opts.time
 * @param {string} [opts.recipeLogId]
 * @returns {import('./chompass-core/models.js').FoodEntry[]}
 */
export function progressiveMealToFoodEntries(d, opts) {
  const recipeLogId = opts.recipeLogId || crypto.randomUUID();
  const mealType = /** @type {"breakfast"|"lunch"|"dinner"|"snack"} */ (d.mealType || "snack");
  return d.items.map((item) => {
    const a = item.analysis;
    const options = a.servingUnitOptions ?? [];
    /** @type {import('./chompass-core/models.js').FoodEntry} */
    const entry = {
      id: crypto.randomUUID(),
      name: a.name,
      mealType,
      date: opts.date,
      time: opts.time,
      quantityG: a.quantityG ?? null,
      servingUnitOptions: options,
      selectedServingUnit: options.length === 0 ? null : item.selectedServingUnit ?? a.selectedServingUnit ?? null,
      selectedServingQuantity:
        options.length === 0 ? null : item.selectedServingQuantity ?? a.selectedServingQuantity ?? null,
      constituents: a.constituents ?? [],
      calories: Number(a.calories) || 0,
      proteinG: Number(a.proteinG) || 0,
      carbsG: Number(a.carbsG) || 0,
      fatG: Number(a.fatG) || 0,
      fiberG: a.fiberG ?? null,
      sugarG: a.sugarG ?? null,
      addedSugarG: a.addedSugarG ?? null,
      saturatedFatG: a.saturatedFatG ?? null,
      monounsaturatedFatG: a.monounsaturatedFatG ?? null,
      polyunsaturatedFatG: a.polyunsaturatedFatG ?? null,
      transFatG: a.transFatG ?? null,
      cholesterolMg: a.cholesterolMg ?? null,
      sodiumMg: a.sodiumMg ?? null,
      potassiumMg: a.potassiumMg ?? null,
      calciumMg: a.calciumMg ?? null,
      ironMg: a.ironMg ?? null,
      magnesiumMg: a.magnesiumMg ?? null,
      zincMg: a.zincMg ?? null,
      vitaminAMcg: a.vitaminAMcg ?? null,
      vitaminCMg: a.vitaminCMg ?? null,
      vitaminDMcg: a.vitaminDMcg ?? null,
      vitaminB12Mcg: a.vitaminB12Mcg ?? null,
      vitaminEMg: a.vitaminEMg ?? null,
      vitaminKMcg: a.vitaminKMcg ?? null,
      folateMcg: a.folateMcg ?? null,
      omega3G: a.omega3G ?? null,
      source: item.source || a.source || "ai_estimated",
      note: a.note ?? null,
      emoji: a.emoji ?? null,
      grounding: a.grounding ?? null,
      recipeLogId,
    };
    return entry;
  });
}
