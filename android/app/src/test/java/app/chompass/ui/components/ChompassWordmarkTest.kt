package app.chompass.ui.components

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wordmark is built from inline content, and Compose validates the
 * alternate text of every placeholder at composition time. An empty one threw
 * here once, with no compile-time warning. Compose the wordmark so that class
 * of failure cannot return silently, and keep the spoken name readable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChompassWordmarkTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wordmark_composesAndKeepsTheNameReadable() {
        composeRule.setContent {
            MaterialTheme {
                ChompassWordmark(fontSize = 26.sp)
            }
        }
        composeRule.onNodeWithText("chomp", substring = true).assertExists()
    }
}
