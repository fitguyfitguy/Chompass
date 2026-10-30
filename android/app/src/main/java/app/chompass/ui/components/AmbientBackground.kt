package app.chompass.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.chompass.ui.theme.ChompassTraitsDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * The wax model, motion, AGSL metaball shader and soft fallback below are
 * ported from MorpheApp/morphe-manager (GPL-3), adapted for Chompass.
 */

/**
 * One blob of wax.
 *
 * @param x Resting horizontal position, as a fraction of the width.
 * @param travel How far it climbs above and sinks below the middle, as a fraction of the height.
 * @param radius Size in dp.
 * @param climbPeriod Milliseconds for a full rise and fall, slow and different for every blob.
 */
private class WaxBlob(
    val x: Float,
    val travel: Float,
    val radius: Float,
    val climbPeriod: Float,
    val swayPeriod: Float,
    val breathPeriod: Float,
    val phase: Float,
    val colorIndex: Int,
)

private val WAX_BLOBS = listOf(
    WaxBlob(0.22f, 0.36f, 64f, 34000f, 13000f, 9000f, 0.0f, 0),
    WaxBlob(0.70f, 0.40f, 52f, 41000f, 15000f, 11000f, 2.1f, 2),
    WaxBlob(0.45f, 0.30f, 46f, 29000f, 11000f, 8000f, 4.0f, 1),
    WaxBlob(0.82f, 0.34f, 58f, 37000f, 17000f, 10000f, 1.2f, 0),
    WaxBlob(0.15f, 0.42f, 42f, 45000f, 12000f, 12000f, 3.3f, 2),
    WaxBlob(0.58f, 0.38f, 60f, 32000f, 14000f, 9500f, 5.1f, 1),
    WaxBlob(0.35f, 0.44f, 38f, 39000f, 16000f, 8500f, 0.7f, 0),
    WaxBlob(0.90f, 0.28f, 44f, 43000f, 10000f, 10500f, 2.8f, 1),
)

private const val WAX_BLOB_COUNT = 8

// Metaballs: every blob adds r² / d² to a field, and wherever the field passes 1 is wax. Two blobs
// close enough lift the field between them over that line too, which is what makes them merge.
// Each blob is stretched along y by its w, as wax is while it rises or sinks
private const val WAX_SHADER = """
uniform float4 blobs[8];
uniform float4 colors[8];
uniform float alpha;

half4 main(float2 p) {
    float field = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float4 b = blobs[i];
        float2 d = p - b.xy;
        d.y /= b.w;
        float f = b.z * b.z / max(dot(d, d), 1.0);
        field += f;
        color += colors[i].rgb * f;
    }
    color /= max(field, 0.0001);
    // A crisp body with a faint halo around it, so the wax reads as lit from inside
    float body = smoothstep(0.85, 1.15, field);
    float halo = smoothstep(0.3, 0.9, field) * 0.3;
    float a = max(body, halo) * alpha;
    return half4(color * a, a);
}
"""

/** ~30 fps cap: the clock writes the time state only every ≥33.3 ms, so the
 *  canvas repaints about half as often as the display refreshes. */
private const val MinFrameIntervalNs = 33_333_333L

/**
 * The wax field is rastered at [WaxRasterScale] of screen resolution and
 * stretched back up with bilinear filtering before display. The metaball
 * shader is the app's main idle GPU cost — full-screen, per-pixel, 8-blob
 * loop — and the field is soft by design, so a half-resolution raster upscaled
 * 2× is indistinguishable while cutting shader work to a quarter. Radii are
 * scaled by the same factor so blob sizes stay at their authored dp.
 */
private const val WaxRasterScale = 0.5f

private fun Context.isPowerSaveMode(): Boolean =
    getSystemService(PowerManager::class.java)?.isPowerSaveMode ?: false

private fun Context.isAnimatorScaleOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * Ambient background (Morphe traits): wax blobs rising and sinking lava-lamp
 * style behind every screen — on Android 13+ they merge and part in one AGSL
 * metaball field, older versions draw the same motion as soft stretched ovals.
 * Costs one canvas repaint per ~33 ms while visible and RESUMED; the clock
 * halts when the app is backgrounded and freezes to a single frame in battery
 * saver or when system animations are disabled (duration scale 0). Absent
 * entirely when the Ambient background trait is off.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    if (!ChompassTraitsDefaults.ambientMotion()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Draw-phase reads only: writing timeNs invalidates the draw, never composition.
    // startNs anchors elapsed time like Morphe's frame-based clock, so the wax
    // phases always start at their authored values instead of carrying an
    // arbitrary boot-time offset.
    val startNs = remember { System.nanoTime() }
    val timeNs = remember { mutableLongStateOf(startNs) }
    val lastFrameNs = remember { longArrayOf(0L) }
    val powerSave = remember { MutableStateFlow(context.isPowerSaveMode()) }
    val animatorScaleOff = remember { MutableStateFlow(context.isAnimatorScaleOff()) }

    // Battery saver toggling must not wait for a resume to freeze/resume drift.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                powerSave.value = context.isPowerSaveMode()
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // The clock lives only while RESUMED; each ON_RESUME re-entry re-reads the
    // animator scale, so a system "remove animations" change applies without
    // an app restart.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            animatorScaleOff.value = context.isAnimatorScaleOff()
            combine(powerSave, animatorScaleOff) { save, scaleOff -> save || scaleOff }
                .collectLatest { paused ->
                    if (!paused) {
                        while (true) {
                            withFrameNanos { now ->
                                if (now - lastFrameNs[0] >= MinFrameIntervalNs) {
                                    lastFrameNs[0] = now
                                    timeNs.longValue = now
                                }
                            }
                        }
                    }
                }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    // Accent-derived triad: the theme accent plus two hue-rotated counterpoints,
    // so single-hue themes still read as a multi-color field.
    val waxColors = remember(colorScheme) {
        val base = colorScheme.primary
        listOf(base, base.hueShift(60f, 0.85f), base.hueShift(-75f, 0.9f))
    }
    // Morphe's wax alpha: slightly stronger on dark backgrounds. Saturated color
    // at this alpha reads as a field without washing out content.
    val waxAlpha =
        (if (colorScheme.background.luminance() < 0.5f) 0.14f else 0.11f) *
            ChompassTraitsDefaults.ambientIntensity().alphaScale
    val wax = remember { WaxBlobs() }
    // RuntimeShader compile can throw at runtime on some API 33+ devices; the
    // soft-oval path is the fallback either way (pre-13 has no RuntimeShader).
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { WaxShader() }.getOrNull()
        } else {
            null
        }
    }

    Box(modifier) {
        // Half-resolution raster upscaled by the layer (see WaxRasterScale):
        // blob geometry is authored in screen fractions, so the smaller canvas
        // keeps positions; the density is scaled down by the same factor so
        // radii land at their authored dp after the 2x stretch.
        Canvas(
            Modifier
                .fillMaxSize(WaxRasterScale)
                .graphicsLayer {
                    scaleX = 1f / WaxRasterScale
                    scaleY = 1f / WaxRasterScale
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            // Morphe's clock is milliseconds; the WAX_BLOBS periods are in ms too.
            val tMs = (timeNs.longValue - startNs) / 1_000_000.0
            wax.update(tMs.toFloat(), size, density * WaxRasterScale)
            if (shader != null) {
                drawRect(shader.brushFor(wax, waxColors, waxAlpha))
            } else {
                drawSoftWax(wax, waxColors, waxAlpha)
            }
        }
    }
}

/**
 * The blobs of wax at the current moment, in pixels, recomputed every frame from the clock
 * alone, so the lamp holds no state that could drift.
 */
private class WaxBlobs {
    val x = FloatArray(WAX_BLOB_COUNT)
    val y = FloatArray(WAX_BLOB_COUNT)
    val radius = FloatArray(WAX_BLOB_COUNT)
    val stretch = FloatArray(WAX_BLOB_COUNT)

    fun update(t: Float, size: Size, density: Float) {
        val twoPi = 2f * PI.toFloat()
        WAX_BLOBS.forEachIndexed { i, blob ->
            // Rising and sinking along a sine: fastest mid-lamp, slowing to a pause at either end
            val climb = twoPi * t / blob.climbPeriod + blob.phase
            val sway = sin(twoPi * t / blob.swayPeriod + blob.phase * 1.7f)
            val breath = sin(twoPi * t / blob.breathPeriod + blob.phase * 0.6f)

            x[i] = (blob.x + 0.05f * sway) * size.width
            y[i] = (0.5f - blob.travel * sin(climb)) * size.height
            radius[i] = blob.radius * density * (1f + 0.09f * breath)
            // Stretched tall while it moves, round again where it turns
            stretch[i] = 1f + 0.4f * abs(cos(climb))
        }
    }
}

/** The wax metaball field as a shader brush, uploading per-frame blob uniforms. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class WaxShader {
    private val shader = RuntimeShader(WAX_SHADER)
    private val brush = ShaderBrush(shader)
    private val blobUniform = FloatArray(WAX_BLOB_COUNT * 4)
    private val colorUniform = FloatArray(WAX_BLOB_COUNT * 4)

    fun brushFor(blobs: WaxBlobs, colors: List<Color>, alpha: Float): Brush {
        for (i in 0 until WAX_BLOB_COUNT) {
            blobUniform[i * 4] = blobs.x[i]
            blobUniform[i * 4 + 1] = blobs.y[i]
            blobUniform[i * 4 + 2] = blobs.radius[i]
            blobUniform[i * 4 + 3] = blobs.stretch[i]
            val color = colors[WAX_BLOBS[i].colorIndex]
            colorUniform[i * 4] = color.red
            colorUniform[i * 4 + 1] = color.green
            colorUniform[i * 4 + 2] = color.blue
            colorUniform[i * 4 + 3] = 1f
        }
        shader.setFloatUniform("blobs", blobUniform)
        shader.setFloatUniform("colors", colorUniform)
        shader.setFloatUniform("alpha", alpha)
        return brush
    }
}

/** The same lamp without a shader: soft stretched blobs that overlap instead of merging. */
private fun DrawScope.drawSoftWax(blobs: WaxBlobs, colors: List<Color>, alpha: Float) {
    for (i in 0 until WAX_BLOB_COUNT) {
        val color = colors[WAX_BLOBS[i].colorIndex]
        val radius = blobs.radius[i] * 1.3f
        val height = radius * blobs.stretch[i]
        val center = Offset(blobs.x[i], blobs.y[i])
        drawOval(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = alpha),
                    0.6f to color.copy(alpha = alpha * 0.8f),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            topLeft = Offset(center.x - radius, center.y - height),
            size = Size(radius * 2f, height * 2f),
        )
    }
}

// Accent triad helpers, HSV round-trip kept local to this file (the theme's
// HSL utility stays private to launcher-icon matching).

private fun Color.toHsv(out: FloatArray) {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    out[0] = when {
        delta == 0f -> 0f
        max == red -> 60f * ((green - blue) / delta).mod(6f)
        max == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }.mod(360f)
    out[1] = if (max == 0f) 0f else delta / max
    out[2] = max
}

/** Rotates the hue by [hueDeltaDeg] degrees and scales saturation, keeping the
 *  value channel; gray inputs stay gray. */
private fun Color.hueShift(hueDeltaDeg: Float, saturationScale: Float): Color {
    val hsv = FloatArray(3)
    toHsv(hsv)
    hsv[0] = (hsv[0] + hueDeltaDeg).mod(360f)
    hsv[1] = (hsv[1] * saturationScale).coerceIn(0f, 1f)
    val chroma = hsv[2] * hsv[1]
    val sector = hsv[0] / 60f
    val x = chroma * (1f - abs(sector % 2f - 1f))
    val m = hsv[2] - chroma
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
