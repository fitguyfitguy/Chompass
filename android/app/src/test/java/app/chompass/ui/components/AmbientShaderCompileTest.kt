package app.chompass.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wax field's AGSL source is compiled at runtime, so a typo in it does not
 * break the build — [AmbientBackground] catches the throw and silently falls
 * back to the soft-oval path, losing the merge, the dither and the palette
 * weight with no error anywhere. Robolectric's native graphics run the real
 * Skia compiler, which turns that into a red test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AmbientShaderCompileTest {
    @Test
    fun shader_compiles() {
        RuntimeShader(WAX_SHADER)
    }

    @Test
    fun uniformsUsedByTheBrush_allExistWithTheUploadedSize() {
        val shader = RuntimeShader(WAX_SHADER)
        // Sizes must match WaxShader.brushFor: 8 float4 lanes per array.
        shader.setFloatUniform("blobs", FloatArray(8 * 4))
        shader.setFloatUniform("colors", FloatArray(8 * 4))
        shader.setFloatUniform("alpha", 0.14f)
        shader.setFloatUniform("resolution", floatArrayOf(540f, 1200f))
        shader.setFloatUniform("touch", floatArrayOf(1f, 2f, 3f, 0f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aMisspelledUniform_isRejected() {
        // Guards the test itself: setFloatUniform must be a real validation,
        // otherwise the check above would pass on a shader that lights nothing.
        RuntimeShader(WAX_SHADER).setFloatUniform("blobz", FloatArray(8 * 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun brokenSource_isRejected() {
        // Same guard for the constructor: if Robolectric ever degrades
        // RuntimeShader to a shadow that accepts any string, `shader_compiles`
        // would pass on a shader no device can compile.
        RuntimeShader("half4 main(float2 p) { return notAThing(p); }")
    }
}
