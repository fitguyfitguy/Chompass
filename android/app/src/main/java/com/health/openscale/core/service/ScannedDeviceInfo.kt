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
package com.health.openscale.core.service

import android.util.SparseArray
import java.util.UUID

/**
 * Data class to hold information about a scanned Bluetooth LE device.
 *
 * @property name The advertised name of the device. Can be null.
 * @property address The MAC address of the device.
 * @property rssi The received signal strength indicator (RSSI) in dBm.
 * @property serviceUuids A list of service UUIDs advertised by the device.
 * @property manufacturerData Manufacturer-specific data advertised by the device.
 * @property serviceData Service-data payloads advertised by the device.
 * @property isSupported Flag indicating whether openScale has a handler for this device.
 * @property determinedHandlerDisplayName The display name of the handler determined for this device, if any.
 */
data class ScannedDeviceInfo(
    var name: String,
    val address: String,
    val rssi: Int,
    val serviceUuids: List<UUID>,
    val manufacturerData: SparseArray<ByteArray>?,
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    var isSupported: Boolean = false,
    var determinedHandlerDisplayName: String? = null
)
