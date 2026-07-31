// @ts-check
import { formatQuantity } from "../chompass-core/serving-units.js";

/**
 * Before/after field list after an AI correction (Android buildReprocessDiff parity).
 * @param {Record<string, any>} before
 * @param {Record<string, any>} after
 * @returns {{label: string, before: string, after: string}[]}
 */
export function buildCorrectDiff(before, after) {
  /** @type {{label: string, before: string, after: string}[]} */
  const rows = [];
  /** @param {string} label @param {string} a @param {string} b */
  const add = (label, a, b) => {
    if (a !== b) rows.push({ label, before: a, after: b });
  };
  add("Name", String(before.name || ""), String(after.name || ""));
  add("Calories", `${Math.round(Number(before.calories || 0))} kcal`, `${Math.round(Number(after.calories || 0))} kcal`);
  add("Protein", `${formatQuantity(Number(before.proteinG || 0))}g`, `${formatQuantity(Number(after.proteinG || 0))}g`);
  add("Carbs", `${formatQuantity(Number(before.carbsG || 0))}g`, `${formatQuantity(Number(after.carbsG || 0))}g`);
  add("Fat", `${formatQuantity(Number(before.fatG || 0))}g`, `${formatQuantity(Number(after.fatG || 0))}g`);
  const bg = before.quantityG != null ? `${Math.round(Number(before.quantityG))} g` : "—";
  const ag = after.quantityG != null ? `${Math.round(Number(after.quantityG))} g` : "—";
  add("Serving", bg, ag);
  return rows;
}
