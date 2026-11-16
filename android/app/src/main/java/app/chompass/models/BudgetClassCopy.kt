package app.chompass.models

import app.chompass.R

/** Which string the class line and the budget sheet use. Shared by the hero and the widget. */
object BudgetClassCopy {
    fun labelRes(step: Int, kind: ActiveDayClassifier.Kind): Int = when {
        step <= 0 || kind == ActiveDayClassifier.Kind.NONE -> R.string.home_budget_class_usual
        kind == ActiveDayClassifier.Kind.DAY_TYPE && step == 1 -> R.string.home_budget_class_lean_type
        kind == ActiveDayClassifier.Kind.DAY_TYPE -> R.string.home_budget_class_type
        kind == ActiveDayClassifier.Kind.LIGHTER && step == 1 -> R.string.home_budget_class_lean_lighter
        kind == ActiveDayClassifier.Kind.HARDER && step == 1 -> R.string.home_budget_class_lean_harder
        kind == ActiveDayClassifier.Kind.LIGHTER -> R.string.home_budget_class_lighter
        else -> R.string.home_budget_class_harder
    }

    fun labelArg(step: Int, kind: ActiveDayClassifier.Kind, typeName: String?): String? =
        if (kind == ActiveDayClassifier.Kind.DAY_TYPE && step >= 1) typeName else null

    fun sheetRes(step: Int, hourlyReady: Boolean): Int = when {
        !hourlyReady -> R.string.home_budget_class_sheet_hourly
        step <= 0 -> R.string.home_budget_class_sheet_usual
        else -> R.string.home_budget_class_sheet_moved
    }

    fun kindFromStorage(raw: String?): ActiveDayClassifier.Kind =
        ActiveDayClassifier.Kind.entries.firstOrNull { it.name == raw } ?: ActiveDayClassifier.Kind.NONE
}
