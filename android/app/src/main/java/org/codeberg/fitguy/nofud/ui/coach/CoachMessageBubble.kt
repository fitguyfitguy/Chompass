package org.codeberg.fitguy.nofud.ui.coach

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.codeberg.fitguy.nofud.R
import org.codeberg.fitguy.nofud.models.ChatMessage
import org.codeberg.fitguy.nofud.ui.theme.AppColors

@Composable
internal fun EmptyState(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(108.dp)
                .shadow(
                    elevation = if (isDark) 10.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.08f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.08f)
                )
                .clip(CircleShape)
                .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
                .border(0.5.dp, if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Forum,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = AppColors.Calorie
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.coach_empty_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.coach_empty_subtitle),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    sending: Boolean,
    error: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(messages, key = { it.id }) { MessageBubble(it) }

        if (sending) {
            item("typing") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
                            .border(0.5.dp, if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { TypingIndicator() }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        if (error != null) {
            item("error") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .border(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 3-dot animated typing indicator. Cycles a "phase" 0 -> 1 -> 2 every 350ms;
 * the dot whose index == phase scales to 1.15 and goes opaque.
 * Verbatim port of struct TypingIndicator in ChatView.swift.
 */
@Composable
internal fun TypingIndicator() {
    var phase by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            phase = (phase + 1) % 3
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val active = i == phase
            val scale by animateFloatAsState(
                targetValue = if (active) 1.15f else 1.0f,
                animationSpec = tween(durationMillis = 350),
                label = "typingScale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (active) 1.0f else 0.3f,
                animationSpec = tween(durationMillis = 350),
                label = "typingAlpha"
            )
            // iOS uses `.opacity(phase == i ? 1 : 0.3)` which dims the *whole* dot.
            // Use Modifier.alpha so the gradient fades uniformly instead of getting
            // a white overlay (the previous attempt actually brightened inactive dots).
            Box(
                Modifier
                    .size(7.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(AppColors.CalorieGradient)
            )
        }
    }
}

@Composable
internal fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AssistantBadge()
            Spacer(Modifier.width(8.dp))
            Bubble(content = msg.content, isUser = false)
            Spacer(Modifier.width(48.dp))
        } else {
            Spacer(Modifier.width(48.dp))
            Bubble(content = msg.content, isUser = true, attachmentImageBase64 = msg.attachmentImageBase64)
        }
    }
}

/** 26dp translucent disc with gradient sparkles icon. Verbatim port of `assistantBadge`. */
@Composable
private fun AssistantBadge() {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        Modifier
            .padding(top = 8.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(if (isDark) AppColors.TranslucentSurfaceDark else AppColors.TranslucentSurfaceLight)
            .border(0.5.dp, if (isDark) AppColors.HairlineBorderDark else AppColors.HairlineBorderLight, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = AppColors.Calorie
        )
    }
}

/**
 * Verbatim port of `bubble`.
 *   .font(.system(.body, design: .rounded))            -> 17sp
 *   .padding(.horizontal, 16).padding(.vertical, 11)    -> same
 *   user background = LinearGradient(calorieGradient)
 *   assistant background = translucent surface + subtle Calorie tint
 *   stroke = LinearGradient white 0.45->0.05 user / 0.22->0.04 assistant
 *   user has top white 0.35->0 highlight (fakes .blendMode(.plusLighter))
 *   shadow user: Calorie 0.28, radius 10, y 6
 *   shadow asst: Black 0.12, radius 6, y 3
 */
@Composable
private fun Bubble(content: String, isUser: Boolean, attachmentImageBase64: String? = null) {
    val shape = MaterialTheme.shapes.large
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(shape)
            .background(backgroundColor),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            attachmentImageBase64?.let { encoded ->
                val bitmap = rememberDecodedBitmap(encoded) {
                    runCatching {
                        val bytes = Base64.getDecoder().decode(encoded)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (isUser) {
                Text(
                    content,
                    fontSize = 17.sp,
                    color = textColor,
                    lineHeight = 22.sp,
                    style = TextStyle(fontWeight = FontWeight.Normal),
                )
            } else {
                // Coach replies often use markdown — render it.
                MarkdownText(content = content, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun <K> rememberDecodedBitmap(key: K, decode: () -> Bitmap?): Bitmap? {
    val state = produceState<Bitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.Default) { decode() }
    }
    return state.value
}

// ── Markdown rendering for Coach replies ────────────────────────────────
// Lightweight renderer for the formatting the Coach actually emits: #/##/### headings,
// "- / * / 1." lists, ``` code fences ```, `inline code`, **bold**, *italic*, [links](url).
// Block layout here; inline styling via AnnotatedString. No third-party dependency.

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String) : MdBlock()
    data class Numbered(val number: String, val text: String) : MdBlock()
    data class Code(val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.replace("\r\n", "\n").split("\n")
    var i = 0
    while (i < lines.size) {
        val trimmed = lines[i].trim()
        when {
            trimmed.startsWith("```") -> {
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.add(lines[i]); i++
                }
                i++ // skip closing fence
                blocks.add(MdBlock.Code(code.joinToString("\n")))
            }
            trimmed.isEmpty() -> i++
            headingLevel(trimmed) != null -> {
                val level = headingLevel(trimmed)!!
                blocks.add(MdBlock.Heading(level, trimmed.trimStart('#').trim()))
                i++
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                blocks.add(MdBlock.Bullet(trimmed.drop(2).trim())); i++
            }
            numberedItem(trimmed) != null -> {
                val (num, rest) = numberedItem(trimmed)!!
                blocks.add(MdBlock.Numbered(num, rest)); i++
            }
            else -> { blocks.add(MdBlock.Paragraph(trimmed)); i++ }
        }
    }
    return blocks
}

private fun headingLevel(s: String): Int? {
    val hashes = s.takeWhile { it == '#' }.length
    if (hashes in 1..3 && s.getOrNull(hashes) == ' ') return hashes
    return null
}

private fun numberedItem(s: String): Pair<String, String>? {
    val dot = s.indexOf('.')
    if (dot <= 0) return null
    val num = s.substring(0, dot)
    if (!num.all { it.isDigit() } || s.getOrNull(dot + 1) != ' ') return null
    return num to s.substring(dot + 1).trim()
}

/** Inline markdown → AnnotatedString: **bold**, *italic* / _italic_, `code`, [text](url). */
private fun inlineMarkdown(text: String, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    while (i < n) {
        val c = text[i]
        when {
            c == '*' && i + 1 < n && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(c); i++ }
            }
            (c == '*' || c == '_') -> {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(c); i++ }
            }
            c == '[' -> {
                val close = text.indexOf(']', i + 1)
                val open = if (close != -1) close + 1 else -1
                if (close != -1 && text.getOrNull(open) == '(') {
                    val urlEnd = text.indexOf(')', open + 1)
                    if (urlEnd != -1) {
                        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                            append(text.substring(i + 1, close))
                        }
                        i = urlEnd + 1
                    } else { append(c); i++ }
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}

@Composable
internal fun MarkdownText(content: String, color: Color) {
    val linkColor = AppColors.Calorie
    val codeBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inlineMarkdown(block.text, linkColor, codeBg),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = when (block.level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp },
                    lineHeight = 24.sp
                )
                is MdBlock.Bullet -> Row {
                    Text("•", color = color, fontSize = 17.sp, lineHeight = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(inlineMarkdown(block.text, linkColor, codeBg), color = color, fontSize = 17.sp, lineHeight = 22.sp)
                }
                is MdBlock.Numbered -> Row {
                    Text("${block.number}.", color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(inlineMarkdown(block.text, linkColor, codeBg), color = color, fontSize = 17.sp, lineHeight = 22.sp)
                }
                is MdBlock.Code -> Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(codeBg).padding(10.dp)
                ) {
                    Text(block.text, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
                }
                is MdBlock.Paragraph -> Text(
                    inlineMarkdown(block.text, linkColor, codeBg),
                    color = color, fontSize = 17.sp, lineHeight = 22.sp
                )
            }
        }
    }
}
