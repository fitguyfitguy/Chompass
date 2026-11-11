package app.chompass.scale

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import com.health.openscale.core.bluetooth.scales.BeurerBF450Handler
import com.health.openscale.core.bluetooth.scales.BeurerSanitasHandler
import com.health.openscale.core.bluetooth.scales.MGBHandler
import com.health.openscale.core.bluetooth.scales.MedisanaBs44xHandler
import com.health.openscale.core.bluetooth.scales.MiScaleHandler
import com.health.openscale.core.bluetooth.scales.PlainSigScaleHandler
import com.health.openscale.core.bluetooth.scales.RenphoHandler
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.bluetooth.scales.StandardBeurerSanitasHandler
import com.health.openscale.core.bluetooth.scales.DeviceSupport
import com.health.openscale.core.service.ScannedDeviceInfo
import java.util.UUID

/**
 * The vendored openScale driver set. Covers generic SIG-standard WSS/BCS scales
 * (PlainSig: A&D UC-352BLE, Samsung/GP-BELAD001BP0, …), the Beurer/Sanitas
 * family (BF450/700/800/710, Sanitas SBF70/72, SilverCrest SBF75, Crane), the
 * standard-profile Beurer set (BF105/720/500/600/850/950/1000, Sanitas
 * SBF76/77), Renpho, Xiaomi Mi Scale v1/v2, Medisana BS44x, and the MGB
 * family. See third_party/openscale/SNAPSHOT.md for the vendor manifest.
 *
 * Order follows upstream ScaleFactory.createHandlers() precedence for the
 * vendored subset (PlainSig first, Renpho after StandardBeurerSanitas, MiScale
 * after Renpho). One deliberate deviation: MGBHandler stays LAST — it claims
 * any device advertising service 0xFFB0 (upstream documents the same
 * constraint). PlainSig, MiScale and Renpho are fixture-verified only
 * (upstream unit tests, no hardware pass).
 */
object ScaleDriverRegistry {
    val handlers: List<ScaleDeviceHandler> = listOf(
        PlainSigScaleHandler(),
        BeurerBF450Handler(),
        BeurerSanitasHandler(),
        StandardBeurerSanitasHandler(),
        RenphoHandler(),
        MiScaleHandler(),
        MedisanaBs44xHandler(),
        MGBHandler(),
    )

    /**
     * Scan-UUID hint table, one entry per handler: the service UUIDs its device
     * family advertises. Feeds the filtered first scan pass in
     * [ScaleBleTransport]; scales that advertise none of these (or nothing at
     * all) are picked up by the unfiltered fallback pass, because most of these
     * handlers ultimately match on the advertised *name*.
     */
    private val scanServiceHints: Map<Class<out ScaleDeviceHandler>, List<UUID>> = mapOf(
        PlainSigScaleHandler::class.java to listOf(uuid16(0x181D), uuid16(0x181B)),
        BeurerBF450Handler::class.java to listOf(uuid16(0xFFFF)),
        BeurerSanitasHandler::class.java to listOf(uuid16(0xFFE0)),
        StandardBeurerSanitasHandler::class.java to listOf(
            uuid16(0xFFFF), // BF105/BF1000/BF950
            uuid16(0xFFF0), // BF600/BF850
        ),
        RenphoHandler::class.java to listOf(uuid16(0x181B), uuid16(0x181D)),
        MiScaleHandler::class.java to listOf(uuid16(0x181B), uuid16(0x181D)),
        MedisanaBs44xHandler::class.java to listOf(uuid16(0x78B2)),
        MGBHandler::class.java to listOf(uuid16(0xFFB0)),
    )

    /** Deduplicated [ScanFilter]s for the filtered scan pass (one per hinted UUID). */
    val scanFilters: List<ScanFilter> =
        scanServiceHints.values.flatten().distinct().map { uuid ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
        }

    /** First handler (in list order) whose supportFor() claims the device. */
    fun supportFor(device: ScannedDeviceInfo): Pair<ScaleDeviceHandler, DeviceSupport>? {
        for (handler in handlers) {
            val support = handler.supportFor(device) ?: continue
            return handler to support
        }
        return null
    }

    private fun uuid16(short: Int): UUID =
        UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", short))
}
