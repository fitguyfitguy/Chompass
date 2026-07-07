package org.codeberg.fitguy.nofud.models

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

object MacroValueFormatter {
    fun string(value: Double): String {
        val rounded = round(value)
        return if (abs(rounded - value) < 0.0001) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    fun withUnit(value: Double): String = "${string(value)}g"
}
