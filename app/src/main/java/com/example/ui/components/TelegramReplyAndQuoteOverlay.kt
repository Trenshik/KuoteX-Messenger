package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.Message
import com.example.ui.channel.ChannelBackgroundLayer
import com.example.ui.channel.ChannelCustomization
import com.example.ui.media.GifSearchRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Thumbnail and media summary extracted from a replied/forwarded Message
 * so the unified Telegram composer capsule can render tiny media previews (e.g., GIF, Photo).
 */
data class ReplyMediaMeta(
    val thumbUrl: String?,
    val summaryLabel: String,
    val isGif: Boolean = false
)

fun extractReplyMediaMeta(message: Message): ReplyMediaMeta? {
    val gifItem = GifSearchRepository.parseGifFromMessage(message)
    if (gifItem != null) {
        return ReplyMediaMeta(
            thumbUrl = gifItem.thumbUrl.ifBlank { gifItem.gifUrl },
            summaryLabel = "GIF",
            isGif = true
        )
    }
    if (message.mediaType == "gif") {
        return ReplyMediaMeta(
            thumbUrl = message.mediaPath,
            summaryLabel = "GIF",
            isGif = true
        )
    }
    if (!message.documentData.isNullOrBlank()) {
        try {
            val json = org.json.JSONObject(message.documentData)
            val mime = json.optString("mimeType", "")
            val type = json.optString("type", "")
            val uri = json.optString("uri", "").ifBlank { message.mediaPath.orEmpty() }
            val name = json.optString("name", "")
            if (mime.startsWith("image/") || message.mediaType == "image") {
                return ReplyMediaMeta(
                    thumbUrl = uri.takeIf { it.isNotBlank() },
                    summaryLabel = message.text.takeIf { it.isNotBlank() } ?: "Фото"
                )
            }
            if (type == "video_note" || message.mediaType == "video_note") {
                return ReplyMediaMeta(thumbUrl = null, summaryLabel = "Видеосообщение")
            }
            if (mime.startsWith("video/") || message.mediaType == "video") {
                return ReplyMediaMeta(
                    thumbUrl = uri.takeIf { it.isNotBlank() },
                    summaryLabel = message.text.takeIf { it.isNotBlank() } ?: "Видео"
                )
            }
            if (type == "voice_note" || message.audioPath != null) {
                return ReplyMediaMeta(thumbUrl = null, summaryLabel = "Голосовое сообщение")
            }
            if (name.isNotBlank()) {
                return ReplyMediaMeta(thumbUrl = null, summaryLabel = name)
            }
        } catch (_: Exception) {
        }
    }
    if (!message.mediaPath.isNullOrBlank() && (message.mediaType == "image" || message.mediaPath.startsWith("http"))) {
        return ReplyMediaMeta(
            thumbUrl = message.mediaPath,
            summaryLabel = message.text.takeIf { it.isNotBlank() } ?: "Фото"
        )
    }
    if (message.audioPath != null) {
        return ReplyMediaMeta(thumbUrl = null, summaryLabel = "Голосовое сообщение")
    }
    return null
}

/**
 * Custom crisp vector glyph matching Telegram's purple Reply + Quote / Lines badge.
 */
@Composable
fun TelegramReplyTypeIcon(
    isFragmentQuote: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()

        // Curved reply arrow pointing top-left
        val arrowPath = Path().apply {
            moveTo(w * 0.14f, h * 0.44f)
            lineTo(w * 0.36f, h * 0.22f)
            moveTo(w * 0.14f, h * 0.44f)
            lineTo(w * 0.36f, h * 0.66f)
            moveTo(w * 0.16f, h * 0.44f)
            cubicTo(
                w * 0.48f, h * 0.44f,
                w * 0.66f, h * 0.56f,
                w * 0.68f, h * 0.80f
            )
        }
        drawPath(
            path = arrowPath,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        if (isFragmentQuote) {
            // Double quotation mark badge top-right
            drawCircle(
                color = tint,
                radius = 1.8.dp.toPx(),
                center = Offset(w * 0.64f, h * 0.24f)
            )
            drawCircle(
                color = tint,
                radius = 1.8.dp.toPx(),
                center = Offset(w * 0.82f, h * 0.24f)
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.65f, h * 0.25f),
                end = Offset(w * 0.60f, h * 0.38f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.83f, h * 0.25f),
                end = Offset(w * 0.78f, h * 0.38f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        } else {
            // Two small horizontal lines top-right (standard reply badge)
            drawLine(
                color = tint,
                start = Offset(w * 0.56f, h * 0.24f),
                end = Offset(w * 0.88f, h * 0.24f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.62f, h * 0.38f),
                end = Offset(w * 0.88f, h * 0.38f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Full-screen Telegram-style "Ответ на сообщение" overlay modal with:
 * - Wallpaper message preview window (supports GIF/media & text bubbles)
 * - Floating option card ("Выбрать фрагмент", "Ответить в другом чате", "Применить изменения", "Убрать ответ")
 * - Interactive Fragment Selection mode with draggable purple selection handles & floating [ Копировать | Цитировать ] pill
 */
@Composable
fun TelegramReplyCustomizationOverlay(
    message: Message,
    authorName: String,
    initialCustomQuote: String?,
    channelCustomization: ChannelCustomization,
    onDismiss: () -> Unit,
    onApply: (selectedQuote: String?, isFragmentQuote: Boolean) -> Unit,
    onReplyInAnotherChat: (selectedQuote: String?, isFragmentQuote: Boolean) -> Unit,
    onRemoveReply: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val density = LocalDensity.current

    val fullText = message.text
    val mediaMeta = remember(message) { extractReplyMediaMeta(message) }
    val isMediaOnly = mediaMeta != null && (mediaMeta.isGif || fullText.isBlank() || fullText.startsWith("🎞️"))
    val hasSelectableText = !isMediaOnly && fullText.isNotBlank()

    var isSelectingFragment by remember(message.id) { mutableStateOf(false) }

    // Selection offsets in `fullText`
    val initialRange = remember(message.id, initialCustomQuote, fullText) {
        if (!initialCustomQuote.isNullOrBlank() && fullText.isNotEmpty()) {
            val idx = fullText.indexOf(initialCustomQuote)
            if (idx >= 0) {
                idx to (idx + initialCustomQuote.length).coerceAtMost(fullText.length)
            } else {
                0 to fullText.length
            }
        } else {
            0 to fullText.length
        }
    }
    var selStart by remember(message.id) { mutableIntStateOf(initialRange.first) }
    var selEnd by remember(message.id) { mutableIntStateOf(initialRange.second.coerceAtLeast(initialRange.first)) }

    var pendingQuoteText by remember(message.id) { mutableStateOf(initialCustomQuote) }
    var pendingIsFragment by remember(message.id) { mutableStateOf(!initialCustomQuote.isNullOrBlank()) }

    val activeSelectedSubstring = remember(fullText, selStart, selEnd) {
        if (fullText.isEmpty()) ""
        else {
            val safeStart = selStart.coerceIn(0, fullText.length)
            val safeEnd = selEnd.coerceIn(safeStart, fullText.length)
            fullText.substring(safeStart, safeEnd)
        }
    }

    val accentPurple = Color(0xFFB37BFF)
    val sheetDarkBg = Color(0xFF211E27)
    val menuCardBg = Color(0xFF26222D)
    val bubbleDarkBg = Color(0xFF1A191F)
    val pillFloatingBg = Color(0xFF16151B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                horizontalAlignment = Alignment.End
            ) {
                // Main Header + Wallpaper Preview Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = sheetDarkBg,
                    tonalElevation = 6.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Top Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "Ответ на сообщение",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasSelectableText) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isSelectingFragment) {
                                        "Потяните маркеры для выбора цитаты"
                                    } else {
                                        "Вы можете процитировать фрагмент текста"
                                    },
                                    color = Color(0xFFA7A2B0),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Chat Wallpaper Preview Window
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 190.dp, max = 330.dp)
                                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF421015), Color(0xFF6A1418), Color(0xFF280B10))
                                    )
                                )
                        ) {
                            if (channelCustomization.chatWallpaperId != "none" || !channelCustomization.wallpaperCustomUri.isNullOrBlank()) {
                                ChannelBackgroundLayer(
                                    customization = channelCustomization,
                                    modifier = Modifier.matchParentSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                if (isMediaOnly && mediaMeta != null) {
                                    // GIF / Media Preview Bubble (Screenshot 1)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF111015),
                                        border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.fillMaxWidth(0.86f)
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp)
                                                    .background(Color.Black)
                                            ) {
                                                if (!mediaMeta.thumbUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(mediaMeta.thumbUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = mediaMeta.summaryLabel,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = Color.Black.copy(alpha = 0.62f),
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        if (mediaMeta.isGif) {
                                                            Icon(
                                                                Icons.Filled.GifBox,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(4.dp))
                                                        }
                                                        Text(
                                                            text = mediaMeta.summaryLabel,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                            if (fullText.isNotBlank() && !fullText.startsWith("🎞️")) {
                                                Text(
                                                    text = fullText,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Text Message Bubble with Optional Interactive Fragment Selection (Screenshots 2 & 5)
                                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        // Floating [ Копировать | Цитировать ] Action Pill (Screenshot 5)
                                        if (isSelectingFragment) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = pillFloatingBg,
                                                shadowElevation = 10.dp,
                                                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.1f)),
                                                modifier = Modifier
                                                    .padding(bottom = 8.dp, start = 12.dp)
                                                    .testTag("fragment_floating_toolbar")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.height(44.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .clickable {
                                                                val textToCopy = activeSelectedSubstring.ifBlank { fullText }
                                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                                Toast.makeText(context, "Фрагмент скопирован", Toast.LENGTH_SHORT).show()
                                                            }
                                                            .padding(horizontal = 18.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "Копировать",
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .width(1.dp)
                                                            .height(22.dp)
                                                            .background(Color.White.copy(alpha = 0.12f))
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .clickable {
                                                                val chosen = activeSelectedSubstring.trim().ifBlank { fullText.trim() }
                                                                onApply(chosen, true)
                                                            }
                                                            .padding(horizontal = 18.dp)
                                                            .testTag("fragment_floating_quote_button"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "Цитировать",
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Preview Message Bubble
                                        Box {
                                            Surface(
                                                shape = RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomEnd = 18.dp,
                                                    bottomStart = 6.dp
                                                ),
                                                color = bubbleDarkBg,
                                                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.08f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                    val annotatedPreview = remember(fullText, isSelectingFragment, selStart, selEnd, pendingQuoteText) {
                                                        buildAnnotatedString {
                                                            append(fullText)
                                                            if (isSelectingFragment && fullText.isNotEmpty()) {
                                                                val s = selStart.coerceIn(0, fullText.length)
                                                                val e = selEnd.coerceIn(s, fullText.length)
                                                                if (e > s) {
                                                                    addStyle(
                                                                        style = SpanStyle(
                                                                            background = accentPurple.copy(alpha = 0.38f),
                                                                            color = Color.White
                                                                        ),
                                                                        start = s,
                                                                        end = e
                                                                    )
                                                                }
                                                            } else if (!pendingQuoteText.isNullOrBlank()) {
                                                                val idx = fullText.indexOf(pendingQuoteText!!)
                                                                if (idx >= 0) {
                                                                    addStyle(
                                                                        style = SpanStyle(
                                                                            background = accentPurple.copy(alpha = 0.30f),
                                                                            color = Color.White
                                                                        ),
                                                                        start = idx,
                                                                        end = (idx + pendingQuoteText!!.length).coerceAtMost(fullText.length)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Text(
                                                        text = annotatedPreview,
                                                        color = Color.White.copy(alpha = 0.94f),
                                                        fontSize = 14.5.sp,
                                                        lineHeight = 20.sp,
                                                        onTextLayout = { textLayoutResult = it },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .then(
                                                                if (isSelectingFragment && fullText.isNotEmpty()) {
                                                                    Modifier.pointerInput(fullText) {
                                                                        detectTapGestures { offset ->
                                                                            val layout = textLayoutResult ?: return@detectTapGestures
                                                                            val charIdx = layout.getOffsetForPosition(offset)
                                                                                .coerceIn(0, (fullText.length - 1).coerceAtLeast(0))
                                                                            // Select word at tap or toggle full sentence
                                                                            var wStart = charIdx
                                                                            while (wStart > 0 && !fullText[wStart - 1].isWhitespace()) wStart--
                                                                            var wEnd = charIdx + 1
                                                                            while (wEnd < fullText.length && !fullText[wEnd].isWhitespace()) wEnd++
                                                                            if (wEnd > wStart) {
                                                                                selStart = wStart
                                                                                selEnd = wEnd
                                                                            }
                                                                        }
                                                                    }
                                                                } else Modifier
                                                            )
                                                    )

                                                    Spacer(Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (isSelectingFragment) {
                                                            Text(
                                                                text = "Выбрать всё",
                                                                color = accentPurple,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .clickable {
                                                                        selStart = 0
                                                                        selEnd = fullText.length
                                                                    }
                                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        } else {
                                                            Spacer(Modifier.width(1.dp))
                                                        }
                                                        Text(
                                                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                                            color = Color.White.copy(alpha = 0.45f),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // Interactive Start & End Draggable Selection Handles (Screenshot 5)
                                            val layout = textLayoutResult
                                            if (isSelectingFragment && layout != null && fullText.isNotEmpty()) {
                                                val safeStart = selStart.coerceIn(0, (fullText.length - 1).coerceAtLeast(0))
                                                val safeEndChar = (selEnd - 1).coerceIn(safeStart, (fullText.length - 1).coerceAtLeast(0))
                                                val startBox = layout.getBoundingBox(safeStart)
                                                val endBox = layout.getBoundingBox(safeEndChar)
                                                val padX = with(density) { 14.dp.toPx() }
                                                val padY = with(density) { 10.dp.toPx() }
                                                val handleRadiusPx = with(density) { 9.dp.toPx() }
                                                val touchSlopHitRadiusPx = with(density) { 30.dp.toPx() }

                                                val startCenter = Offset(
                                                    padX + startBox.left - handleRadiusPx * 0.4f,
                                                    padY + startBox.bottom + handleRadiusPx * 0.2f
                                                )
                                                val endCenter = Offset(
                                                    padX + endBox.right + handleRadiusPx * 0.4f,
                                                    padY + endBox.bottom + handleRadiusPx * 0.2f
                                                )

                                                // Start handle visual marker
                                                Box(
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                (startCenter.x - handleRadiusPx).roundToInt(),
                                                                (startCenter.y - handleRadiusPx).roundToInt()
                                                            )
                                                        }
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(accentPurple)
                                                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                        .testTag("fragment_start_handle")
                                                )

                                                // End handle visual marker
                                                Box(
                                                    modifier = Modifier
                                                        .offset {
                                                            IntOffset(
                                                                (endCenter.x - handleRadiusPx).roundToInt(),
                                                                (endCenter.y - handleRadiusPx).roundToInt()
                                                            )
                                                        }
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(accentPurple)
                                                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                                        .testTag("fragment_end_handle")
                                                )

                                                // Fixed parent-space gesture overlay so dragging never suffers offset-feedback jitter
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .pointerInput(layout, fullText) {
                                                            awaitEachGesture {
                                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                                val sIdx = selStart.coerceIn(0, (fullText.length - 1).coerceAtLeast(0))
                                                                val eIdx = (selEnd - 1).coerceIn(sIdx, (fullText.length - 1).coerceAtLeast(0))
                                                                val curStartBox = layout.getBoundingBox(sIdx)
                                                                val curEndBox = layout.getBoundingBox(eIdx)
                                                                val curStartCenter = Offset(
                                                                    padX + curStartBox.left - handleRadiusPx * 0.4f,
                                                                    padY + curStartBox.bottom + handleRadiusPx * 0.2f
                                                                )
                                                                val curEndCenter = Offset(
                                                                    padX + curEndBox.right + handleRadiusPx * 0.4f,
                                                                    padY + curEndBox.bottom + handleRadiusPx * 0.2f
                                                                )
                                                                val distStart = (down.position - curStartCenter).getDistance()
                                                                val distEnd = (down.position - curEndCenter).getDistance()
                                                                val activeHandle = when {
                                                                    distStart <= touchSlopHitRadiusPx && distStart <= distEnd -> 1
                                                                    distEnd <= touchSlopHitRadiusPx -> 2
                                                                    else -> 0
                                                                }
                                                                if (activeHandle == 0) return@awaitEachGesture

                                                                down.consume()
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                                    if (!change.pressed) {
                                                                        change.consume()
                                                                        break
                                                                    }
                                                                    change.consume()
                                                                    val textPos = Offset(
                                                                        (change.position.x - padX).coerceAtLeast(0f),
                                                                        (change.position.y - padY - curStartBox.height * 0.45f).coerceAtLeast(0f)
                                                                    )
                                                                    val charPos = layout.getOffsetForPosition(textPos)
                                                                    if (activeHandle == 1) {
                                                                        selStart = charPos.coerceIn(0, (selEnd - 1).coerceAtLeast(0))
                                                                    } else {
                                                                        selEnd = (charPos + 1).coerceIn((selStart + 1).coerceAtMost(fullText.length), fullText.length)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Floating Options Card below the preview (Screenshots 1, 2, 5)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = menuCardBg,
                    tonalElevation = 8.dp,
                    shadowElevation = 14.dp,
                    border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth(0.84f)
                ) {
                    AnimatedContent(
                        targetState = isSelectingFragment,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "reply_menu_mode"
                    ) { selectingMode ->
                        if (selectingMode) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OverlayMenuRow(
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    },
                                    title = "Назад",
                                    onClick = { isSelectingFragment = false },
                                    testTag = "fragment_mode_back_button"
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                OverlayMenuRow(
                                    icon = {
                                        Icon(
                                            Icons.Filled.FormatQuote,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    },
                                    title = "Цитировать фрагмент",
                                    onClick = {
                                        val chosen = activeSelectedSubstring.trim().ifBlank { fullText.trim() }
                                        onApply(chosen, true)
                                    },
                                    testTag = "fragment_mode_quote_button"
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (hasSelectableText) {
                                    OverlayMenuRow(
                                        icon = {
                                            TelegramReplyTypeIcon(
                                                isFragmentQuote = true,
                                                tint = Color.White
                                            )
                                        },
                                        title = "Выбрать фрагмент",
                                        onClick = { isSelectingFragment = true },
                                        testTag = "reply_overlay_select_fragment"
                                    )
                                }
                                OverlayMenuRow(
                                    icon = {
                                        Icon(
                                            Icons.Filled.SwapHoriz,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    },
                                    title = "Ответить в другом чате",
                                    onClick = {
                                        onReplyInAnotherChat(pendingQuoteText, pendingIsFragment)
                                    },
                                    testTag = "reply_overlay_another_chat"
                                )
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.08f),
                                    thickness = 0.8.dp
                                )
                                OverlayMenuRow(
                                    icon = {
                                        Icon(
                                            Icons.Filled.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    },
                                    title = "Применить изменения",
                                    onClick = {
                                        onApply(pendingQuoteText, pendingIsFragment)
                                    },
                                    testTag = "reply_overlay_apply"
                                )
                                OverlayMenuRow(
                                    icon = {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFEF5350),
                                            modifier = Modifier.size(21.dp)
                                        )
                                    },
                                    title = "Убрать ответ",
                                    titleColor = Color(0xFFEF5350),
                                    onClick = onRemoveReply,
                                    testTag = "reply_overlay_remove"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayMenuRow(
    icon: @Composable () -> Unit,
    title: String,
    titleColor: Color = Color.White,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = title
            }
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = titleColor,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
