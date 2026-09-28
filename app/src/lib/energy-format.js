// @ts-check
/**
 * Energy display formatting (Codeberg #100): storage and formulas stay kcal;
 * conversion happens only at render / picker value. Local display pref, never
 * synced — mirrors the Android EnergyFormat helper.
 */
import { t, formatNumber } from "./i18n/index.js";

export const KJ_PER_KCAL = 4.184;

/**
 * @param {Awaited<ReturnType<typeof import('./db.js').prefs.load>> | null | undefined} prefs
 * @returns {"kcal"|"kj"}
 */
export function energyUnitFromPrefs(prefs) {
  return prefs?.energyUnit === "kj" ? "kj" : "kcal";
}

/**
 * @param {number} kcal
 * @param {"kcal"|"kj"} unit
 * @returns {number}
 */
export function energyQuantity(kcal, unit) {
  return unit === "kj" ? Math.round(kcal * KJ_PER_KCAL) : kcal;
}

/**
 * @param {number} display display-unit value (kJ when unit is "kj")
 * @param {"kcal"|"kj"} unit
 * @returns {number}
 */
export function energyToKcal(display, unit) {
  return unit === "kj" ? Math.round(display / KJ_PER_KCAL) : display;
}

/**
 * @param {"kcal"|"kj"} unit
 * @returns {string} localized unit label ("kcal" / "kJ")
 */
export function energyUnitLabel(unit) {
  return unit === "kj" ? t("unit.kj") : t("unit.kcal");
}

/**
 * @param {number} kcal
 * @param {"kcal"|"kj"} [unit]
 * @returns {string} "2,000 kcal" / "8,368 kJ"
 */
export function formatEnergy(kcal, unit = "kcal") {
  return `${formatNumber(energyQuantity(kcal, unit))} ${energyUnitLabel(unit)}`;
}
