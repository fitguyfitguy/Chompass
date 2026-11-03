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
package com.health.openscale

import app.chompass.BuildConfig as ChompassBuildConfig

/**
 * Vendored split: upstream generates this class via Gradle `buildConfig`; Chompass
 * vendors openScale sources verbatim without upstream's Gradle wiring, so this shim
 * bridges the fields referenced by vendored code (LogManager bugreport header) to
 * Chompass's own build metadata. See third_party/openscale/SNAPSHOT.md.
 */
object BuildConfig {
    val VERSION_NAME: String = ChompassBuildConfig.VERSION_NAME
    val VERSION_CODE: Int = ChompassBuildConfig.VERSION_CODE
    val APPLICATION_ID: String = ChompassBuildConfig.APPLICATION_ID
    val BUILD_TYPE: String = ChompassBuildConfig.BUILD_TYPE

    /** Upstream stamps these from CI at build time; not tracked by Chompass builds. */
    const val BUILD_TIME_UTC: Long = 0L
    const val GIT_SHA: String = ""
}
