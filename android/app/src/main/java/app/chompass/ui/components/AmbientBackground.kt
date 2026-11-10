package app.chompass.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.chompass.ui.theme.AmbientContrast
import app.chompass.ui.theme.AmbientPalette
import app.chompass.ui.theme.AmbientSaturation
import app.chompass.ui.theme.AppColors
import app.chompass.ui.theme.ChompassNavTransitions
import app.chompass.ui.theme.ChompassTraitsDefaults
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

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

// Travels of 0.46–0.50 put the outermost blobs against the screen edges at the
// ends of their arcs, so the field fills the top and bottom bands instead of
// leaving them permanently empty.
private val WAX_BLOBS = listOf(
    WaxBlob(0.22f, 0.46f, 64f, 34000f, 13000f, 9000f, 0.0f, 0),
    WaxBlob(0.70f, 0.40f, 52f, 41000f, 15000f, 11000f, 2.1f, 2),
    WaxBlob(0.45f, 0.30f, 46f, 29000f, 11000f, 8000f, 4.0f, 1),
    WaxBlob(0.82f, 0.34f, 58f, 37000f, 17000f, 10000f, 1.2f, 0),
    WaxBlob(0.15f, 0.48f, 42f, 45000f, 12000f, 12000f, 3.3f, 2),
    WaxBlob(0.58f, 0.38f, 60f, 32000f, 14000f, 9500f, 5.1f, 1),
    WaxBlob(0.35f, 0.50f, 38f, 39000f, 16000f, 8500f, 0.7f, 0),
    WaxBlob(0.90f, 0.28f, 44f, 43000f, 10000f, 10500f, 2.8f, 1),
)

private const val WAX_BLOB_COUNT = 8

// Metaballs: every blob adds r² / d² to a field, and wherever the field passes 1 is wax. Two blobs
// close enough lift the field between them over that line too, which is what makes them merge.
// Each blob is stretched along y by its w, as wax is while it rises or sinks.
//
// Three things the field does beyond the plain merge:
//  - color is weighted as c² and square-rooted back, which averages the
//    overlapping blobs in roughly linear light: two hues crossing keep their
//    own color instead of desaturating toward gray;
//  - a vertical mask (midFloor) thins the field behind the dense middle band
//    (the primary card) so the strongest wax sits in the breathing room around
//    it; Strong contrast raises that floor so OLED black does not swallow it;
//  - a hash offset of ±½ 8-bit step dithers the alpha ramp, which otherwise
//    bands visibly at 0.11–0.14.
internal const val WAX_SHADER = """
uniform float4 blobs[8];
uniform float4 colors[8];
uniform float alpha;
uniform float2 resolution;
uniform float4 touch;   // x, y, radius in canvas px; w = heat 0..1
uniform float bodyLo;
uniform float bodyHi;
uniform float haloLo;
uniform float haloHi;
uniform float haloGain;
uniform float midFloor;

half4 main(float2 p) {
    float field = 0.0;
    float3 color = float3(0.0);
    for (int i = 0; i < 8; i++) {
        float4 b = blobs[i];
        float2 d = p - b.xy;
        d.y /= b.w;
        float r = b.z;
        if (touch.w > 0.0) {
            float td = distance(b.xy, touch.xy);
            r *= 1.0 + 0.45 * touch.w * (1.0 - smoothstep(0.0, touch.z, td));
        }
        float f = r * r / max(dot(d, d), 1.0);
        field += f;
        float3 c = colors[i].rgb;
        color += c * c * f;
    }
    color = sqrt(color / max(field, 0.0001));
    float body = smoothstep(bodyLo, bodyHi, field);
    float halo = smoothstep(haloLo, haloHi, field) * haloGain;
    float a = max(body, halo) * alpha;
    float vy = abs(p.y / max(resolution.y, 1.0) - 0.5) * 2.0;
    a *= mix(midFloor, 1.0, smoothstep(0.15, 0.85, vy));
    float n = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
    a += (n - 0.5) * (1.0 / 255.0);
    a = clamp(a, 0.0, 1.0);
    return half4(color * a, a);
}
"""

/** ~30 fps cap: the clock publishes the time state only every ≥33.3 ms, so the
 *  canvas repaints about half as often as the display refreshes. */
private const val MinFrameIntervalNs = 33_333_333L

/** After this long without a touch the clock drops to [IdleFrameIntervalMs]
 *  instead of the display cadence: the wax still drifts, but at fewer wakeups.
 *  Any touch restores vsync pacing on the next iteration. 42 ms ≈ 24 fps; the
 *  60 Hz surface the app requests quantizes published frames to 2–3 vsyncs, so
 *  it lands around 20–24 fps (measured ~22 fps on the Nord 2, up from ~15). */
private const val IdleAfterNs = 6_000_000_000L
private const val IdleFrameIntervalMs = 42L

/**
 * The wax field is rastered at [WaxRasterScale] of screen resolution and
 * stretched back up with bilinear filtering before display. The metaball
 * shader is the app's main idle GPU cost — full-screen, per-pixel, 8-blob
 * loop — and the field is soft by design, so a half-resolution raster upscaled
 * 2× is indistinguishable while cutting shader work to a quarter. Radii are
 * scaled by the same factor so blob sizes stay at their authored dp.
 */
private const val WaxRasterScale = 0.5f

/** How long a finger keeps the wax around it swelled, and how fast that decays. */
private const val TouchHeatWindowNs = 1_500_000_000L
private const val TouchDecayNs = 4.0e8

/** Radius of the touch influence as a fraction of the shorter screen side. */
private const val TouchRadiusFraction = 0.22f

/** Beam of the tilt parallax, as a screen fractions travel at full gravity. */
private const val AmbientTiltFraction = 0.03f
private const val AmbientTiltDeadzone = 0.05f

/**
 * Where the finger is, for the wax to swell around. Screen fractions, with
 * [x] < 0 meaning "no touch yet"; [lastTouchNs] also drives the idle throttle,
 * so the field goes quiet exactly when the user has stopped interacting.
 */
@Stable
class AmbientTouchState {
    var x by mutableFloatStateOf(-1f)
    var y by mutableFloatStateOf(-1f)

    /**
     * Wall clock of the last touch. Deliberately not snapshot state: the clock
     * reads it for idle detection and the draw reads it for heat decay, both on
     * the main thread, and a write must not invalidate the draw by itself — the
     * clock already publishes frames while the field is active.
     */
    var lastTouchNs: Long = System.nanoTime()
        private set

    private var lastPublishNs = 0L

    fun record(fx: Float, fy: Float) {
        val now = System.nanoTime()
        lastTouchNs = now
        // The field repaints at ~30 fps, so publishing the position at pointer
        // rate (up to 120 Hz) would only buy redundant shader draws.
        if (x >= 0f && now - lastPublishNs < MinFrameIntervalNs) return
        lastPublishNs = now
        x = fx
        y = fy
    }
}

/**
 * Reports touches to [state] from the Initial pass and never consumes, so taps,
 * scrolls and swipes still reach the content underneath. Belongs on the root
 * container: a sibling behind the content never sees a touch that lands on it.
 */
fun Modifier.ambientTouchObserver(state: AmbientTouchState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (size.width <= 0 || size.height <= 0) continue
            state.record(
                (change.position.x / size.width).coerceIn(0f, 1f),
                (change.position.y / size.height).coerceIn(0f, 1f),
            )
        }
    }
}

/**
 * Device tilt as a screenshot-space pan, in screen fractions. Registered only
 * while the app is RESUMED (the listener is unregistered in the `finally`) and
 * at [SensorManager.SENSOR_DELAY_UI]; absent sensor or a disabled trait leaves
 * the state at zero with no listener at all.
 */
@Composable
fun rememberAmbientTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val sensor = remember(context) {
        context.getSystemService(SensorManager::class.java)
            ?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    }
    LaunchedEffect(enabled, sensor, lifecycleOwner) {
        tilt.value = Offset.Zero
        if (!enabled || sensor == null) return@LaunchedEffect
        val manager = context.getSystemService(SensorManager::class.java) ?: return@LaunchedEffect
        val filtered = FloatArray(3)
        var primed = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                if (!primed) {
                    filtered[0] = v[0]
                    filtered[1] = v[1]
                    primed = true
                }
                // Low-pass at 0.12 so a step (pick up the phone) glides instead of snapping
                filtered[0] += 0.12f * (v[0] - filtered[0])
                filtered[1] += 0.12f * (v[1] - filtered[1])
                tilt.value = Offset(
                    normalized(filtered[0]) * AmbientTiltFraction,
                    normalized(filtered[1]) * AmbientTiltFraction,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            try {
                awaitCancellation()
            } finally {
                manager.unregisterListener(listener)
            }
        }
    }
    return tilt
}

/** Gravity in m/s² to ±1, with a dead zone so a phone flat on a table holds still. */
private fun normalized(accel: Float): Float {
    val n = (accel / 9.81f).coerceIn(-1f, 1f)
    return if (abs(n) < AmbientTiltDeadzone) 0f
    else (n - sign(n) * AmbientTiltDeadzone) / (1f - AmbientTiltDeadzone)
}

/** A finger, in canvas pixels. Radius is the influence bowl; heat fades with time. */
private data class TouchInfluence(
    val x: Float,
    val y: Float,
    val radius: Float,
    val heat: Float,
)

/**
 * Ambient background (Morphe traits): wax blobs rising and sinking lava-lamp
 * style behind every screen — on Android 13+ they merge and part in one AGSL
 * metaball field, older versions draw the same motion as soft stretched ovals.
 * Publishes ~30 fps while RESUMED and untouched for less than [IdleAfterNs],
 * ~15 fps after that; halts when backgrounded and freezes to a single frame in
 * battery saver or when system animations are disabled. Finger proximity
 * swells the wax around it and device tilt pans the field. Balanced contrast
 * dims the field on OLED so black stays nearly off; Strong eases that dim.
 * Absent entirely when the Ambient background trait is off.
 *
 * @param panFraction Tab parallax offset, in screen fractions. Applied here
 *  rather than by the caller so the tilt pan and the tab camera share one layer.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier,
    panFraction: Float = 0f,
    touch: AmbientTouchState = remember { AmbientTouchState() },
) {
    if (!ChompassTraitsDefaults.ambientMotion()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Draw-phase reads only: writing phaseNs invalidates the draw, never composition.
    // Accumulated phase instead of `t * scale`, so changing the speed mid-drag
    // continues from the current angle instead of jumping.
    val phaseNs = remember { mutableLongStateOf(0L) }
    val lastFrameNs = remember { longArrayOf(0L) }
    val speed = ChompassTraitsDefaults.ambientSpeed()
    val currentSpeed = rememberUpdatedState(speed)
    val powerSave = remember { MutableStateFlow(context.isPowerSaveMode()) }
    val animatorScaleOff = remember { MutableStateFlow(context.isAnimatorScaleOff()) }

    // Battery saver toggling must not wait for a resume to freeze/resume drift,
    // and neither must "remove animations" (Codeberg: settings toggled while the
    // app is in the foreground used to apply only on the next resume).
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
        val animatorObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animatorScaleOff.value = context.isAnimatorScaleOff()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            animatorObserver,
        )
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(animatorObserver)
        }
    }

    // The clock lives only while RESUMED. Each re-entry zeroes the frame anchor
    // so a resume never scores a multi-minute phase jump.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            animatorScaleOff.value = context.isAnimatorScaleOff()
            lastFrameNs[0] = 0L
            combine(powerSave, animatorScaleOff) { save, scaleOff -> save || scaleOff }
                .collectLatest { paused ->
                    if (paused) return@collectLatest
                    while (true) {
                        val scale = currentSpeed.value.timeScale
                        if (System.nanoTime() - touch.lastTouchNs > IdleAfterNs) {
                            // Idle: advance on wall time at a quarter of the wakeups
                            val now = System.nanoTime()
                            val last = lastFrameNs[0]
                            lastFrameNs[0] = now
                            if (last != 0L) {
                                phaseNs.longValue += ((now - last) * scale).toLong()
                            }
                            delay(IdleFrameIntervalMs)
                        } else {
                            withFrameNanos { now ->
                                if (now - lastFrameNs[0] >= MinFrameIntervalNs) {
                                    val last = lastFrameNs[0]
                                    lastFrameNs[0] = now
                                    if (last != 0L) {
                                        phaseNs.longValue += ((now - last) * scale).toLong()
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val palette = ChompassTraitsDefaults.ambientPalette()
    val contrast = ChompassTraitsDefaults.ambientContrast()
    val saturation = ChompassTraitsDefaults.ambientSaturation()
    // The tilt listener samples gravity at UI rate; a tilted phone never
    // produces two identical low-passed offsets, so ungated it redraws the
    // layer forever and the UI never goes idle (uiautomator "could not get
    // idle state" on a cradled phone, 2026-11-10). Stand still together with
    // the wax clock: battery saver and remove-animations pause the parallax.
    val motionPaused = rememberSystemMotionPaused()
    val darkBackground = colorScheme.background.luminance() < 0.5f
    // Accent-derived triad: the theme accent plus two hue-rotated counterpoints,
    // so single-hue themes still read as a multi-color field. The macro palette
    // drops the accent for the three semantic hues instead. Natural saturation
    // and Balanced contrast leave those colors untouched.
    val waxColors = remember(colorScheme, palette, saturation, contrast, darkBackground) {
        val base = when (palette) {
            AmbientPalette.MACRO -> listOf(AppColors.Protein, AppColors.Carbs, AppColors.Fat)
            AmbientPalette.ACCENT -> {
                val accent = colorScheme.primary
                listOf(accent, accent.hueShift(60f, 0.85f), accent.hueShift(-75f, 0.9f))
            }
        }
        base.map { waxDisplayColor(it, saturation, contrast, darkBackground) }
    }
    // Morphe's wax alpha: slightly stronger on dark backgrounds. Saturated color
    // at this alpha reads as a field without washing out content. Balanced
    // contrast keeps the OLED dim; Strong is the opt-in that lets the field
    // read on true black.
    val waxAlpha = waxFieldAlpha(
        backgroundLuminance = colorScheme.background.luminance(),
        intensityScale = ChompassTraitsDefaults.ambientIntensity().alphaScale,
        contrast = contrast,
        oled = ChompassTraitsDefaults.oled(),
    )
    val wax = remember { WaxBlobs() }
    val softBrushes = remember(waxColors) { SoftWaxBrushes(waxColors) }
    // RuntimeShader compile can throw at runtime on some API 33+ devices; the
    // soft-oval path is the fallback either way (pre-13 has no RuntimeShader).
    // The log line is the only trace: a silent fallback loses the merge, the
    // dither and the palette weight with nothing in logcat to say why.
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { WaxShader() }
                .onSuccess { Log.d("AmbientWax", "AGSL wax shader compiled; shader path active") }
                .onFailure { Log.w("AmbientWax", "AGSL wax shader unavailable; soft path in use", it) }
                .getOrNull()
        } else {
            null
        }
    }
    val tilt by rememberAmbientTilt(
        enabled = ChompassTraitsDefaults.ambientTilt() && !motionPaused.value,
    )

    Box(
        modifier.graphicsLayer {
            scaleX = ChompassNavTransitions.TabParallaxOverscan
            scaleY = ChompassNavTransitions.TabParallaxOverscan
            translationX = size.width * (panFraction + tilt.x)
            translationY = size.height * tilt.y
        },
    ) {
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
            val tMs = phaseNs.longValue / 1_000_000.0
            wax.update(tMs.toFloat(), size, density * WaxRasterScale)
            val sinceTouch = (System.nanoTime() - touch.lastTouchNs).coerceAtLeast(0L)
            val heat = if (touch.x < 0f || sinceTouch > TouchHeatWindowNs) {
                0f
            } else {
                exp(-sinceTouch / TouchDecayNs).toFloat()
            }
            val influence = TouchInfluence(
                x = touch.x * size.width,
                y = touch.y * size.height,
                radius = TouchRadiusFraction * min(size.width, size.height),
                heat = heat,
            )
            if (shader != null) {
                drawRect(shader.brushFor(wax, waxColors, waxAlpha, size, influence, contrast))
            } else {
                drawSoftWax(
                    wax, waxColors, waxAlpha, softBrushes, influence, contrast.softRadiusScale,
                )
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
    private val touchUniform = FloatArray(4)

    fun brushFor(
        blobs: WaxBlobs,
        colors: List<Color>,
        alpha: Float,
        size: Size,
        touch: TouchInfluence,
        contrast: AmbientContrast,
    ): Brush {
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
        touchUniform[0] = touch.x
        touchUniform[1] = touch.y
        touchUniform[2] = touch.radius
        touchUniform[3] = touch.heat
        shader.setFloatUniform("blobs", blobUniform)
        shader.setFloatUniform("colors", colorUniform)
        shader.setFloatUniform("alpha", alpha)
        shader.setFloatUniform("resolution", floatArrayOf(size.width, size.height))
        shader.setFloatUniform("touch", touchUniform)
        shader.setFloatUniform("bodyLo", contrast.bodyLo)
        shader.setFloatUniform("bodyHi", contrast.bodyHi)
        shader.setFloatUniform("haloLo", contrast.haloLo)
        shader.setFloatUniform("haloHi", contrast.haloHi)
        shader.setFloatUniform("haloGain", contrast.haloGain)
        shader.setFloatUniform("midFloor", contrast.midFloor)
        return brush
    }
}

/**
 * One gradient per palette color, built once and reused through a draw
 * transform: the pre-13 path used to allocate a brush per blob per frame.
 */
private class SoftWaxBrushes(colors: List<Color>) {
    val byColor: Map<Color, Brush> = colors.distinct().associateWith { c ->
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to c,
                0.6f to c.copy(alpha = 0.8f),
                1f to Color.Transparent,
            ),
            center = Offset.Zero,
            radius = 1f,
        )
    }
}

/** How much a touch swells blob [i]: full at the finger, gone at the bowl edge. */
private fun touchInflate(blobs: WaxBlobs, i: Int, touch: TouchInfluence): Float {
    if (touch.heat <= 0f) return 1f
    val dx = blobs.x[i] - touch.x
    val dy = blobs.y[i] - touch.y
    val d = sqrt(dx * dx + dy * dy)
    val t = ((touch.radius - d) / touch.radius).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    return 1f + 0.45f * touch.heat * smooth
}

/** The same lamp without a shader: soft stretched blobs that overlap instead of merging. */
private fun DrawScope.drawSoftWax(
    blobs: WaxBlobs,
    colors: List<Color>,
    alpha: Float,
    brushes: SoftWaxBrushes,
    touch: TouchInfluence,
    radiusScale: Float,
) {
    for (i in 0 until WAX_BLOB_COUNT) {
        val color = colors[WAX_BLOBS[i].colorIndex]
        val radius = blobs.radius[i] * 1.3f * radiusScale * touchInflate(blobs, i, touch)
        val height = radius * blobs.stretch[i]
        withTransform({
            translate(blobs.x[i], blobs.y[i])
            scale(radius, height, pivot = Offset.Zero)
        }) {
            drawCircle(
                brush = brushes.byColor.getValue(color),
                radius = 1f,
                center = Offset.Zero,
                alpha = alpha,
            )
        }
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
    return hsvToColor(
        (hsv[0] + hueDeltaDeg).mod(360f),
        (hsv[1] * saturationScale).coerceIn(0f, 1f),
        hsv[2],
    )
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val chroma = v * s
    val sector = h / 60f
    val x = chroma * (1f - abs(sector % 2f - 1f))
    val m = v - chroma
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

/** Alpha of the wax field. Balanced contrast on OLED is the shipped 0.55 dim. */
internal fun waxFieldAlpha(
    backgroundLuminance: Float,
    intensityScale: Float,
    contrast: AmbientContrast,
    oled: Boolean,
): Float {
    val base = if (backgroundLuminance < 0.5f) 0.14f else 0.11f
    val oledScale = if (oled) contrast.oledAlphaScale else 1f
    return base * intensityScale * contrast.alphaScale * oledScale
}

/**
 * Chroma and dark-background value adjustment. Natural saturation with no
 * value floor returns [color] unchanged, so the shipped look stays bit-identical.
 * Gray inputs stay gray.
 */
internal fun waxDisplayColor(
    color: Color,
    saturation: AmbientSaturation,
    contrast: AmbientContrast,
    darkBackground: Boolean,
): Color {
    val floor = if (darkBackground) {
        maxOf(saturation.darkValueFloor, contrast.darkValueFloor)
    } else {
        0f
    }
    if (saturation.chromaScale == 1f && floor <= 0f) return color
    val hsv = FloatArray(3)
    color.toHsv(hsv)
    val nextSat = (hsv[1] * saturation.chromaScale).coerceIn(0f, 1f)
    val nextVal = if (floor > 0f) maxOf(hsv[2], floor) else hsv[2]
    if (nextSat == hsv[1] && nextVal == hsv[2]) return color
    return hsvToColor(hsv[0], nextSat, nextVal)
}
