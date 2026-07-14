package org.codeberg.fitguy.nofud.ui.coach

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.ByteArrayOutputStream
import org.codeberg.fitguy.nofud.AppContainer
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.SpeechLanguage
import org.codeberg.fitguy.nofud.models.SpeechProvider
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialog
import org.codeberg.fitguy.nofud.ui.components.FudGlassDialogActions
import org.codeberg.fitguy.nofud.ui.components.InAppCameraCaptureDialog
import org.codeberg.fitguy.nofud.ui.navigation.BottomNavDockedControlPadding
import java.util.Locale

/**
 * Verbatim port of struct ChatView in
 * ios/calorietracker/Views/ChatView.swift.
 *
 * Layout (top to bottom):
 *   - TopAppBar with "Coach" title + reset icon (disabled when empty)
 *   - empty state OR message list (weight 1f)
 *   - horizontal scrolling promptChips (always visible)
 *   - capsule input bar with gradient send button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(container: AppContainer) {
    val vm: CoachViewModel = viewModel(factory = CoachViewModel.Factory(container))
    val ui by vm.ui.collectAsState()
    var input by remember { mutableStateOf("") }
    var attachedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showCameraCapture by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showResetConfirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    // Dismiss the keyboard when the USER drags the chat (DragInteraction only —
    // the auto-scroll after sending a message must not steal focus).
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                keyboard?.hide()
                focusManager.clearFocus()
            }
        }
    }

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) attachedImageBytes = resizedJpeg(bytes, maxDimension = 1800, quality = 86) ?: bytes
        }
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showCameraCapture = true
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            showCameraCapture = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun sendCurrentDraft(textOverride: String? = null) {
        val image = attachedImageBytes
        val trimmed = (textOverride ?: input).trim()
        if (trimmed.isEmpty() && image == null) return
        if (ui.sending) return
        val imageForAi = image?.let { resizedJpeg(it, maxDimension = 1600, quality = 78) ?: it }
        val thumbnail = image?.let { resizedJpeg(it, maxDimension = 700, quality = 68) ?: it }
        hideKeyboard()
        input = ""
        attachedImageBytes = null
        vm.send(trimmed, imageBytes = imageForAi, thumbnailBytes = thumbnail)
    }

    // Inline (WhatsApp-style) voice recorder — records with whatever STT provider
    // the user has configured and drops the transcript straight into the send path.
    val voiceScope = rememberCoroutineScope()
    val voiceProvider by container.prefs.selectedSpeechProvider
        .collectAsState(initial = SpeechProvider.NATIVE)
    val voiceLanguage by container.prefs.selectedSpeechLanguage(voiceProvider)
        .collectAsState(initial = SpeechLanguage.defaultFor(voiceProvider))
    val voice = remember { CoachVoiceController(ctx, container, voiceScope) { text -> sendCurrentDraft(text) } }
    LaunchedEffect(voiceProvider, voiceLanguage) {
        voice.provider = voiceProvider
        voice.nativeLocale = voiceLanguage.nativeLocaleTag()
    }

    LaunchedEffect(ui.messages.size, ui.sending) {
        if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.size - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // iOS Coach: centered "Coach" title, with a small circular dark
            // chip on the right wrapping a counterclockwise arrow reset icon.
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.coach_title), fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    val canReset = ui.messages.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f))
                            .clickable(enabled = canReset) { showResetConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Replay,
                            contentDescription = stringResource(R.string.coach_reset_chat_a11y),
                            tint = if (canReset)
                                MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        // The app is edge-to-edge, so the IME would otherwise overlay the input bar.
        // Lift the whole column above the keyboard (imePadding) with a small gap; when
        // the keyboard is down, keep the docked-nav clearance instead.
        // Keyboard-down clearance = the nav-bar system inset (from the Scaffold) plus the
        // docked-control padding, so the bar clears the floating bottom nav.
        val restClearance = padding.calculateBottomPadding() + BottomNavDockedControlPadding
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                // Track the keyboard rigidly: bottom inset = max(ime, rest clearance).
                // windowInsetsPadding animates it in the layout phase, so the bar sits
                // tight on the keyboard with no bounce and no floaty gap (a plain
                // conditional pad jumps discretely against the smooth IME animation).
                .windowInsetsPadding(
                    WindowInsets.ime
                        .union(WindowInsets(bottom = restClearance))
                        .only(WindowInsetsSides.Bottom)
                )
        ) {
            // Top region — empty state OR message list
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { hideKeyboard() })
                    }
            ) {
                if (ui.messages.isEmpty()) {
                    EmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    val resolvedError = ui.error ?: ui.errorRes?.let { stringResource(it) }
                    MessageList(
                        messages = ui.messages,
                        sending = ui.sending,
                        error = resolvedError,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // promptChips — horizontal scrolling, ALWAYS visible (matches iOS)
            val resolvedChips = ui.suggestions.map { stringResource(it) }
            PromptChipRow(
                chips = resolvedChips,
                enabled = !ui.sending,
                onTap = { chip ->
                    hideKeyboard()
                    input = ""
                    attachedImageBytes = null
                    vm.send(chip)
                }
            )

            // input bar — capsule with gradient send button
            InputBar(
                value = input,
                onValueChange = { input = it },
                attachedImageBytes = attachedImageBytes,
                sending = ui.sending,
                onPickImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCaptureImage = { openCamera() },
                voice = voice,
                onRemoveImage = { attachedImageBytes = null },
                onSend = { sendCurrentDraft() }
            )
        }
    }

    if (showCameraCapture) {
        InAppCameraCaptureDialog(
            onCapture = { bytes ->
                showCameraCapture = false
                attachedImageBytes = resizedJpeg(bytes, maxDimension = 1800, quality = 86) ?: bytes
            },
            onDismiss = { showCameraCapture = false }
        )
    }

    ui.pendingFood?.let { entry ->
        FudGlassDialog(onDismissRequest = { vm.discardPending() }) {
            Text(stringResource(R.string.coach_confirm_log_food_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${entry.name} — ${entry.calories} kcal (${String.format(Locale.US, "%.0f", entry.protein)}g protein, " +
                    "${String.format(Locale.US, "%.0f", entry.carbs)}g carbs, ${String.format(Locale.US, "%.0f", entry.fat)}g fat)",
                style = MaterialTheme.typography.bodyMedium
            )
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_log),
                onPrimary = { vm.confirmPendingFood() },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { vm.discardPending() }
            )
        }
    }

    ui.pendingWeight?.let { entry ->
        FudGlassDialog(onDismissRequest = { vm.discardPending() }) {
            Text(stringResource(R.string.coach_confirm_log_weight_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${String.format(Locale.US, "%.1f", entry.weightKg)} kg", style = MaterialTheme.typography.bodyMedium)
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_log),
                onPrimary = { vm.confirmPendingWeight() },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { vm.discardPending() }
            )
        }
    }

    ui.pendingWater?.let { entry ->
        FudGlassDialog(onDismissRequest = { vm.discardPending() }) {
            Text(stringResource(R.string.coach_confirm_log_water_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${entry.milliliters} ml", style = MaterialTheme.typography.bodyMedium)
            FudGlassDialogActions(
                primaryText = stringResource(R.string.action_log),
                onPrimary = { vm.confirmPendingWater() },
                dismissText = stringResource(R.string.action_cancel),
                onDismiss = { vm.discardPending() }
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.coach_reset_dialog_title)) },
            text = { Text(stringResource(R.string.coach_reset_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetConversation()
                    showResetConfirm = false
                }) { Text(stringResource(R.string.coach_reset_confirm), color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

}

private fun resizedJpeg(bytes: ByteArray, maxDimension: Int, quality: Int): ByteArray? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val longest = maxOf(bitmap.width, bitmap.height)
    val scaled = if (longest > maxDimension) {
        val ratio = maxDimension.toFloat() / longest.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        out.toByteArray()
    }
}
