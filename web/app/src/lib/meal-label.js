// @ts-check
import { t } from "./i18n/index.js";

/** Built-in meal ids with i18n keys under `meal.*`. */
export const MEAL_ORDER = ["breakfast", "lunch", "dinner", "snack", "other"];

/** Localized meal label; unknown ids fall back to the raw value. */
export function mealLabel(mealType) {
  return MEAL_ORDER.includes(mealType) ? t(`meal.${mealType}`) : mealType;
}
