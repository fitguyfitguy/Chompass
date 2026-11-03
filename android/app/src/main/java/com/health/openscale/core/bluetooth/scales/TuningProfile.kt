/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.bluetooth.scales

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.outlined.SignalCellularAlt2Bar
import androidx.compose.ui.graphics.vector.ImageVector
import app.chompass.R

/**
 * Vendored split: the enum declaration is copied verbatim from upstream
 * core/bluetooth/scales/ModernScaleAdapter.kt at the pinned commit. The rest of
 * that file (adapter + tuning data classes) is deliberately NOT vendored — it
 * would cascade in openScale's facade/model layer, which Chompass replaces with
 * its own repositories. See third_party/openscale/SNAPSHOT.md.
 */
enum class TuningProfile(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Conservative(
        labelRes = R.string.tuning_conservative,
        icon = Icons.Filled.SignalCellularAlt1Bar
    ),
    Balanced(
        labelRes = R.string.tuning_balanced,
        icon = Icons.Outlined.SignalCellularAlt2Bar
    ),
    Aggressive(
        labelRes = R.string.tuning_aggressive,
        icon = Icons.Filled.SignalCellularAlt
    )
}
