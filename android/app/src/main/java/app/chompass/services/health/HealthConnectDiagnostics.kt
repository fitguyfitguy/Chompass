package app.chompass.services.health

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures

/**
 * Health Connect availability dump for device diagnosis.
 * Trigger (debug): `adb shell am start -n app.chompass.debug/app.chompass.MainActivity --ez diagnose_health_connect true`
 * Filter: `adb logcat -s ChompassHealthConnect`
 */
object HealthConnectDiagnostics {
    const val TAG = "ChompassHealthConnect"

    suspend fun log(context: Context, health: HealthConnectManager) {
        val sdkInt = HealthConnectClient.getSdkStatus(context)
        val sdkLabel = when (sdkInt) {
            HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"
            HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE"
            else -> "UNKNOWN($sdkInt)"
        }
        val mapped = health.sdkStatus()
        val um = context.getSystemService(UserManager::class.java)
        val isProfile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            um?.isProfile == true
        } else {
            false
        }
        val hcService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(Context.HEALTHCONNECT_SERVICE) != null
        } else {
            null
        }

        Log.i(
            TAG,
            "op=hc_diag sdkStatus=$sdkLabel mapped=$mapped " +
                "api=${Build.VERSION.SDK_INT} isProfile=$isProfile " +
                "healthConnectServicePresent=$hcService " +
                "isAvailable=${health.isAvailable()}"
        )

        if (!health.isAvailable()) {
            Log.i(
                TAG,
                "op=hc_diag phase=unavailable msgRes=${
                    context.resources.getResourceEntryName(health.unavailableMessageRes())
                }"
            )
            return
        }

        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrElse {
            Log.e(TAG, "op=hc_diag phase=getOrCreateFail err=${it.message}", it)
            return
        }
        val bg = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
        )
        val hist = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
        )
        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrElse {
            Log.e(TAG, "op=hc_diag phase=getGrantedFail err=${it.message}", it)
            emptySet()
        }
        Log.i(
            TAG,
            "op=hc_diag phase=features " +
                "backgroundRead=${featureLabel(bg)} historyRead=${featureLabel(hist)} " +
                "managerBackground=${health.isBackgroundReadAvailable()} " +
                "managerHistory=${health.isHistoryReadAvailable()} " +
                "hasAnyPermission=${health.hasAnyPermission()} " +
                "hasBackground=${health.hasBackgroundRead()} " +
                "hasHistory=${health.hasHistoryRead()} " +
                "grantedCount=${granted.size}"
        )
        Log.i(TAG, "op=hc_diag phase=granted ${granted.sorted().joinToString(",")}")
    }

    private fun featureLabel(status: Int): String =
        when (status) {
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE -> "AVAILABLE"
            HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE -> "UNAVAILABLE"
            else -> "UNKNOWN($status)"
        }
}
