package app.chompass.scale

import com.health.openscale.core.bluetooth.scales.BeurerBF450Handler
import com.health.openscale.core.bluetooth.scales.BeurerSanitasHandler
import com.health.openscale.core.bluetooth.scales.MGBHandler
import com.health.openscale.core.bluetooth.scales.MedisanaBs44xHandler
import com.health.openscale.core.bluetooth.scales.ScaleDeviceHandler
import com.health.openscale.core.bluetooth.scales.StandardBeurerSanitasHandler
import com.health.openscale.core.bluetooth.scales.DeviceSupport
import com.health.openscale.core.service.ScannedDeviceInfo

/**
 * The vendored openScale driver set. Covers the Beurer/Sanitas family (BF450/700/
 * 800/710, Sanitas SBF70/72, SilverCrest SBF75, Crane), Medisana BS44x, and the
 * MGB family. See third_party/openscale/SNAPSHOT.md for the vendor manifest.
 *
 * MGBHandler MUST stay last: it claims any device advertising service 0xFFB0
 * (same ordering constraint as upstream ScaleFactory.createHandlers()).
 */
object ScaleDriverRegistry {

    val handlers: List<ScaleDeviceHandler> = listOf(
        BeurerBF450Handler(),
        BeurerSanitasHandler(),
        StandardBeurerSanitasHandler(),
        MedisanaBs44xHandler(),
        MGBHandler(),
    )

    /** First handler (in list order) whose supportFor() claims the device. */
    fun supportFor(device: ScannedDeviceInfo): Pair<ScaleDeviceHandler, DeviceSupport>? {
        for (handler in handlers) {
            val support = handler.supportFor(device) ?: continue
            return handler to support
        }
        return null
    }
}
