package app.chompass.ui.home

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import app.chompass.R
import app.chompass.services.BarcodeCodeNormalizer
import app.chompass.ui.theme.AppColors
import zxingcpp.BarcodeReader
import app.chompass.ui.theme.AppRadii

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
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(executor) { imageProxy ->
                        if (hasScanned.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val value = runCatching {
                            imageProxy.use { proxy ->
                                val entries = reader.read(proxy)
                                    .mapNotNull { result ->
                                        result.text?.trim()?.takeIf(String::isNotEmpty)
                                            ?.let { result.format to it }
                                    }
                                // Keep scanning until a frame yields a code that
                                // normalizes to a product code: a junk or half-read
                                // frame (internal factory codes, partial EANs) can
                                // never resolve via OFF, and stopping on one turned
                                // a single bad frame into a hard scan failure.
                                // Mixed frames prefer the retail 1D code (EAN/UPC)
                                // over a 2D code (QR/DataMatrix): a jar's GS1
                                // Digital Link QR usually carries a case-level GTIN
                                // OFF does not index, while the EAN-13 in the same
                                // frame resolves.
                                pickPreferredCode(entries)
                            }
                        }.getOrNull()
                        if (value != null && hasScanned.compareAndSet(false, true)) {
                            onBarcode(value)
                        }
                    }
                }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
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
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.58f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
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
                    color = Color.White.copy(alpha = 0.72f),
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
