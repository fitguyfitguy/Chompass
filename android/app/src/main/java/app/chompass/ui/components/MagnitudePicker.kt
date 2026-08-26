package app.chompass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chompass.ChompassApp
import app.chompass.R
import app.chompass.ui.theme.AppTextOpacity
import kotlinx.coroutines.launch

internal const val PICKER_MODE_WHEEL = "wheel"
internal const val PICKER_MODE_TYPED = "typed"

/** Hosts (Save / Next) flush the focused typed draft before reading the value. */
internal object MagnitudeDrafts {
    private val commits = mutableListOf<() -> Unit>()
    fun register(commit: () -> Unit): () -> Unit {
        commits.add(commit)
        return { commits.remove(commit) }
    }
    fun commitAll() {
        commits.toList().forEach { it() }
    }
}

private val MAGNITUDE_ROW_HEIGHT = 44.dp * 5

@Composable
internal fun rememberMagnitudePickerMode(): Pair<Boolean, (Boolean) -> Unit> {
    val app = LocalContext.current.applicationContext as ChompassApp
    val prefs = app.container.prefs
    val stored by prefs.numericPickerEntry.collectAsState(initial = PICKER_MODE_WHEEL)
    var override by remember { mutableStateOf<String?>(null) }
    val typed = (override ?: stored) == PICKER_MODE_TYPED
    val scope = rememberCoroutineScope()
    val setTyped: (Boolean) -> Unit = { wantTyped ->
        val id = if (wantTyped) PICKER_MODE_TYPED else PICKER_MODE_WHEEL
        override = id
        scope.launch { prefs.setNumericPickerEntry(id) }
    }
    return typed to setTyped
}

@Composable
internal fun MagnitudeHintIcon(typedMode: Boolean, modifier: Modifier = Modifier) {
    val cd = stringResource(
        if (typedMode) R.string.picker_use_wheel else R.string.picker_type_value
    )
    Icon(
        imageVector = if (typedMode) Icons.Outlined.SwapVert else Icons.Outlined.Dialpad,
        contentDescription = cd,
        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f),
        modifier = modifier.size(18.dp),
    )
}

@Composable
internal fun MagnitudeUnitLabel(unit: String?, compact: Boolean = false) {
    if (unit == null) return
    val compactUnit = compact || unit.length <= 2
    Spacer(Modifier.width(if (compactUnit) 4.dp else 8.dp))
    Text(
        unit,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = AppTextOpacity.Muted),
        modifier = Modifier.width(if (compactUnit) 30.dp else 48.dp).padding(start = 4.dp),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
internal fun MagnitudeTypeField(
    display: String,
    decimal: Boolean,
    onCommitRaw: (String) -> Boolean,
    onFlipToWheel: () -> Unit,
    unit: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var draft by remember(display) {
        mutableStateOf(TextFieldValue(display, TextRange(display.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val commit = {
        val ok = onCommitRaw(draft.text)
        if (ok) keyboard?.hide()
        ok
    }
    val latestCommit by rememberUpdatedState(commit)
    DisposableEffect(Unit) {
        val unregister = MagnitudeDrafts.register { latestCommit() }
        onDispose { unregister() }
    }
    BackHandler {
        val parsedOk = onCommitRaw(draft.text)
        if (!parsedOk) {
            // Keep previous value; leave typed mode.
        }
        keyboard?.hide()
        onFlipToWheel()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MAGNITUDE_ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            WheelSelectionHighlight(Modifier.align(Alignment.Center))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSecondaryContainer),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
                Box(
                    Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 16.dp),
                            onClick = {
                                onCommitRaw(draft.text)
                                keyboard?.hide()
                                onFlipToWheel()
                            },
                        )
                        .padding(4.dp),
                ) {
                    MagnitudeHintIcon(typedMode = true)
                }
            }
        }
        MagnitudeUnitLabel(unit)
    }
}

@Composable
internal fun MagnitudeWheelChrome(
    showHint: Boolean,
    onType: () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wideEnough = maxWidth >= 96.dp
        Box(Modifier.fillMaxWidth()) {
            content()
            if (showHint && wideEnough) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 18.dp),
                            onClick = onType,
                        )
                        .padding(4.dp),
                ) {
                    MagnitudeHintIcon(typedMode = false)
                }
            }
        }
    }
}
