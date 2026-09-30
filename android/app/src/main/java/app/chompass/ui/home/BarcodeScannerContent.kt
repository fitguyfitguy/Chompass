package app.chompass.ui.home

import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import app.chompass.R
import app.chompass.services.BarcodeCodeNormalizer
import app.chompass.ui.theme.AppColors
import zxingcpp.BarcodeReader
import app.chompass.ui.theme.AppRadii
import app.chompass.ui.theme.AppTextOpacity

private const val FOCUS_SETTLE_DEADLINE_MS = 800L
private val ANALYSIS_TARGET_SIZE = Size(1280, 720)

@Composable
internal fun BarcodeScannerContent(
    onBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val hasScanned = remember { AtomicBoolean(false) }
    val scanLock = remember { BarcodeScanLock() }
    val accepting = remember { AtomicBoolean(false) }
    val focusDeadlineMs = remember { AtomicLong(Long.MAX_VALUE) }
    val reader = remember {
        BarcodeReader(
            BarcodeReader.Options().apply {
                formats = setOf(
                    BarcodeReader.Format.EAN_UPC,
                    BarcodeReader.Format.QR_CODE,
                    BarcodeReader.Format.DATA_MATRIX,
                )
                tryHarder = true
                tryRotate = true
                textMode = BarcodeReader.TextMode.PLAIN
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analyzer = ImageAnalysis.Analyzer { imageProxy ->
                if (hasScanned.get()) {
                    imageProxy.close()
                    return@Analyzer
                }
                // Opening frames are the glare misreads. Do not count them until
                // the center focus request has completed, or the settle deadline
                // has elapsed.
                if (!accepting.get() && SystemClock.elapsedRealtime() < focusDeadlineMs.get()) {
                    imageProxy.close()
                    return@Analyzer
                }
                accepting.set(true)
                val preferred = runCatching {
                    imageProxy.use { proxy ->
                        val entries = reader.read(proxy)
                            .mapNotNull { result ->
                                result.text?.trim()?.takeIf(String::isNotEmpty)
                                    ?.let { result.format to it }
                            }
                        pickPreferredCode(entries)
                    }
                }.getOrNull()
                // A junk frame does not end the scan, and neither does a single
                // normalizable frame.
                val accepted = scanLock.observe(preferred)
                if (accepted != null && hasScanned.compareAndSet(false, true)) {
                    onBarcode(accepted)
                }
            }

            fun buildAnalysis(targeted: Boolean): ImageAnalysis {
                val builder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                if (targeted) {
                    builder.setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    ANALYSIS_TARGET_SIZE,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                }
                return builder.build().also { it.setAnalyzer(executor, analyzer) }
            }

            fun bindAnalysis(analysis: ImageAnalysis) =
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )

            val camera = runCatching {
                cameraProvider.unbindAll()
                bindAnalysis(buildAnalysis(targeted = true))
            }.getOrElse { error ->
                Log.w("Chompass", "barcode analysis resolution fallback", error)
                runCatching {
                    cameraProvider.unbindAll()
                    bindAnalysis(buildAnalysis(targeted = false))
                }.getOrNull()
            }
            if (camera != null) {
                previewView.doOnLayout {
                    val point = previewView.meteringPointFactory.createPoint(
                        previewView.width / 2f,
                        previewView.height / 2f,
                    )
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                    ).disableAutoCancel().build()
                    focusDeadlineMs.set(SystemClock.elapsedRealtime() + FOCUS_SETTLE_DEADLINE_MS)
                    camera.cameraControl.startFocusAndMetering(action)
                        .addListener({ accepting.set(true) }, mainExecutor)
                }
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .minimumInteractiveComponentSize()
                    .padding(18.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppRadii.SectionCard))
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 36.dp)
                    .clip(RoundedCornerShape(AppRadii.PillCard))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(AppRadii.PillCard))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = AppColors.Calorie,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.barcode_hint),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.barcode_off_note),
                    color = Color.White.copy(alpha = AppTextOpacity.Secondary),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

/**
 * First decoded text that normalizes to a product code (GTIN/EAN family),
 * else null. `null` means "no lookable code in this frame" — the live scanner
 * must keep scanning and never hand a non-normalizable text to the OFF lookup
 * (it would fail with "could not be read" and end the scan).
 *
 * Thin wrapper over [pickPreferredCode] with no format info (tier 2 only).
 * Mirrored in the PWA (`web/app/src/lib/barcode-detect.js` `pickNormalizable`).
 */
internal fun pickFirstNormalizable(texts: List<String>): String? =
    pickPreferredCode(texts.map { BarcodeReader.Format.NONE to it })

/** Retail 1D formats OFF indexes by default; preferred over 2D in a mixed frame. */
private val RETAIL_1D_FORMATS = setOf(
    BarcodeReader.Format.EAN_UPC,
    BarcodeReader.Format.EAN_13,
    BarcodeReader.Format.EAN_8,
    BarcodeReader.Format.UPC_A,
    BarcodeReader.Format.UPC_E,
)

/**
 * Format-aware frame picking: prefer the retail 1D family (EAN-8/13, UPC-A/E)
 * over 2D (QR/DataMatrix) when a frame yields several normalizable codes;
 * otherwise keep today's behavior (first normalizable code). The 1D retail
 * code is the canonical product identifier OFF indexes; a 2D code in the same
 * frame is usually a GS1 Digital Link / case-level GTIN that OFF may not index.
 * Pure-local heuristic — no network in the scan loop.
 *
 * Mirrored in the PWA (`web/app/src/lib/barcode-detect.js` `pickPreferredCode`).
 */
internal fun pickPreferredCode(entries: List<Pair<BarcodeReader.Format, String>>): String? {
    entries.firstOrNull { (format, text) ->
        format in RETAIL_1D_FORMATS && BarcodeCodeNormalizer.normalize(text) != null
    }?.let { return it.second }
    return entries.firstOrNull { (_, text) ->
        BarcodeCodeNormalizer.normalize(text) != null
    }?.second
}

/**
 * Android live-scan only. A still image
 * ([app.chompass.services.BarcodeImageDecoder]) and the PWA have no second
 * frame, so they must not use this class and it is not mirrored.
 *
 * Not thread-safe. Only the camera analyzer executor calls [observe].
 */
internal class BarcodeScanLock {
    private var candidate: String? = null
    private var confirmingRaw: String? = null
    private var matchCount = 0

    /**
     * Returns the confirming frame's raw text once the same normalized product
     * code has been read twice. Otherwise null.
     * Null, blank, and non-product text do not change the candidate or the count.
     */
    fun observe(rawText: String?): String? {
        val normalized = rawText?.let(BarcodeCodeNormalizer::normalize) ?: return null
        if (normalized == candidate) {
            matchCount += 1
            confirmingRaw = rawText
        } else {
            candidate = normalized
            confirmingRaw = rawText
            matchCount = 1
        }
        return if (matchCount >= REQUIRED_MATCHES) confirmingRaw else null
    }

    private companion object {
        const val REQUIRED_MATCHES = 2
    }
}
