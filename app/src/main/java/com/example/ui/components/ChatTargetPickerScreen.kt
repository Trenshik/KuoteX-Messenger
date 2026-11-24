package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.folders.ChatFolder
import com.example.data.folders.matches
import com.example.ui.AppViewModel
import com.example.ui.Chat
import com.example.ui.ForwardDraft
import com.example.ui.LocalActiveAccount
import com.example.ui.UserPresence
import com.example.ui.botapi.BotRegistry
import com.example.ui.botapi.CustomBot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class PickerFolderTab(val id: String, val label: String) {
    object All : PickerFolderTab("all", "Все чаты")
    object Personal : PickerFolderTab("type_personal", "Личные")
    object Groups : PickerFolderTab("type_groups", "Группы")
    object Channels : PickerFolderTab("type_channels", "Каналы")
    object Bots : PickerFolderTab("type_bots", "Боты")
    data class Custom(val folder: ChatFolder) : PickerFolderTab("folder_${folder.id}", folder.name)
}

/**
 * Full-screen Telegram-style chat picker for:
 * - "Ответить в другом чате" ("Отправить ответ...")
 * - "Переслать" ("Переслать...")
 * Includes search pill, chat folders tabs, "Автор сообщения" quick row, "Избранное", and full chat list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTargetPickerScreen(
    viewModel: AppViewModel,
    navController: NavController,
    mode: String
) {
    val session by viewModel.targetChatPickerSession.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val chatFolders by viewModel.chatFolders.collectAsStateWithLifecycle()
    val userPresences by viewModel.userPresences.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val activeAccount = LocalActiveAccount.current

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTabId by remember { mutableStateOf("all") }

    val isReplyMode = (session?.mode ?: mode) == "reply"

    val tabs = remember(chatFolders) {
        buildList {
            add(PickerFolderTab.All)
            if (chatFolders.isNotEmpty()) {
                chatFolders.forEach { add(PickerFolderTab.Custom(it)) }
            } else {
                add(PickerFolderTab.Personal)
                add(PickerFolderTab.Groups)
                add(PickerFolderTab.Channels)
                add(PickerFolderTab.Bots)
            }
        }
    }

    val activeTab = tabs.find { it.id == selectedTabId } ?: PickerFolderTab.All

    val filteredChats = remember(chats, searchQuery, activeTab) {
        chats
            .filter { !it.isBlocked && !it.isArchived && it.id != "saved_messages" }
            .filter { chat ->
                when (activeTab) {
                    is PickerFolderTab.All -> true
                    is PickerFolderTab.Personal -> !chat.isGroup && !chat.isChannel && !chat.isBot
                    is PickerFolderTab.Groups -> chat.isGroup
                    is PickerFolderTab.Channels -> chat.isChannel
                    is PickerFolderTab.Bots -> chat.isBot
                    is PickerFolderTab.Custom -> activeTab.folder.matches(chat)
                }
            }
            .filter { chat ->
                searchQuery.isBlank() ||
                    chat.title.contains(searchQuery, ignoreCase = true) ||
                    chat.lastMessage.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<Chat> { it.isPinned }
                    .thenByDescending { it.pinnedTimestamp }
                    .thenByDescending { it.lastMessageTimestamp }
            )
    }

    val navigateBack: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.clearTargetChatPicker()
        navController.popBackStack()
    }

    BackHandler(onBack = navigateBack)

    val handleTargetSelected: (targetChatId: String, targetTitle: String, ensureDirect: Boolean) -> Unit = { targetChatId, targetTitle, ensureDirect ->
        focusManager.clearFocus()
        keyboardController?.hide()

        val activeSession = session
        if (targetChatId == "saved_messages") {
            viewModel.ensureSavedMessagesChat()
        } else if (ensureDirect) {
            viewModel.ensureDirectChatExists(targetChatId, targetTitle)
        }

        if (activeSession != null) {
            if (isReplyMode && activeSession.replyDraft != null) {
                val updatedReply = activeSession.replyDraft.copy(targetChatId = targetChatId)
                viewModel.setReplyDraft(targetChatId, updatedReply)
            } else if (!isReplyMode && activeSession.forwardMessages.isNotEmpty()) {
                val draft = ForwardDraft(
                    targetChatId = targetChatId,
                    sourceChatId = activeSession.sourceChatId,
                    messages = activeSession.forwardMessages,
                    originalSenderAvatarUrl = activeSession.forwardPrimarySenderAvatarUrl,
                    originalSenderName = activeSession.forwardPrimarySenderName,
                    originalSenderUsername = activeSession.forwardPrimarySenderUsername,
                    originalSenderId = activeSession.forwardPrimarySenderId,
                    hideSender = activeSession.forwardHideSender
                )
                viewModel.setForwardDraft(draft)
            }
        }
        viewModel.clearTargetChatPicker()

        navController.navigate("chat/$targetChatId") {
            popUpTo(com.example.ui.navigation.AppDestinations.CHAT_TARGET_PICKER) { inclusive = true }
            launchSingleTop = true
        }
    }

    val surfaceDark = MaterialTheme.colorScheme.background
    val searchPillBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val accentPurple = Color(0xFFB37BFF)
    val subtleHeaderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)

    Scaffold(
        containerColor = surfaceDark,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceDark)
                    .statusBarsPadding()
            ) {
                // Header Row: Back arrow + "Отправить ответ..." / "Переслать..."
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = navigateBack,
                        modifier = Modifier.testTag("picker_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isReplyMode) "Отправить ответ..." else "Переслать...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Rounded Search Bar ("Поиск чатов")
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = searchPillBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Поиск",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Поиск чатов",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("picker_search_input")
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Очистить поиск",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Folder Filter Pills ("Все чаты", "работа", "фурри чаты", ...)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(tabs, key = { it.id }) { tab ->
                        val isSelected = tab.id == activeTab.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) accentPurple.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable { selectedTabId = tab.id }
                                .semantics {
                                    role = Role.Tab
                                    contentDescription = tab.label
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("picker_folder_tab_${tab.id}")
                        ) {
                            Text(
                                text = tab.label,
                                color = if (isSelected) Color(0xFFD0AEFF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
            }
        }
    ) { padding ->
        val replyAuthorName = session?.replyDraft?.authorName?.takeIf { it.isNotBlank() }
        val replyAuthorId = session?.replyDraft?.authorId?.takeIf {
            it.isNotBlank() && it != activeAccount?.id && it != session?.sourceChatId
        }
        val showOriginalAuthorSection = isReplyMode &&
            replyAuthorName != null &&
            replyAuthorId != null &&
            activeTab is PickerFolderTab.All &&
            (searchQuery.isBlank() || replyAuthorName.contains(searchQuery, ignoreCase = true))

        val showSavedMessagesRow = activeTab is PickerFolderTab.All &&
            (searchQuery.isBlank() || "избранное".contains(searchQuery, ignoreCase = true) || "saved".contains(searchQuery, ignoreCase = true))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showOriginalAuthorSection && replyAuthorName != null && replyAuthorId != null) {
                item(key = "section_author_header") {
                    Text(
                        text = "Автор сообщения",
                        color = subtleHeaderColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                item(key = "section_author_row") {
                    val presence = userPresences[replyAuthorId]
                    val statusSubtitle = formatPickerPresence(presence)
                    val matchingExistingChat = chats.find {
                        !it.isGroup && !it.isChannel && (it.id == replyAuthorId || it.title.equals(replyAuthorName, ignoreCase = true))
                    }
                    val targetId = matchingExistingChat?.id ?: replyAuthorId
                    val avatarUrl = session?.replyDraft?.authorAvatarUrl?.takeIf { it.isNotBlank() }
                        ?: "https://picsum.photos/seed/$targetId/200"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable {
                                handleTargetSelected(targetId, replyAuthorName, matchingExistingChat == null)
                            }
                            .testTag("picker_original_author_row")
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PickerCircleAvatar(
                            avatarUrl = avatarUrl,
                            title = replyAuthorName,
                            isBot = false,
                            size = 48.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = replyAuthorName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = statusSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (presence?.isOnline == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "section_chats_header") {
                Text(
                    text = "Ваши чаты",
                    color = subtleHeaderColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (showOriginalAuthorSection) 12.dp else 10.dp,
                        bottom = 6.dp
                    )
                )
            }

            if (showSavedMessagesRow) {
                item(key = "saved_messages_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable {
                                handleTargetSelected("saved_messages", "Избранное", false)
                            }
                            .testTag("picker_saved_messages_row")
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(accentPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Избранное",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Избранное",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Сохранить на память.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (filteredChats.isEmpty() && !showSavedMessagesRow) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Чаты не найдены",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredChats, key = { it.id }) { targetChat ->
                    PickerChatRow(
                        chat = targetChat,
                        draftText = drafts[targetChat.id],
                        accentPurple = accentPurple,
                        onClick = {
                            handleTargetSelected(targetChat.id, targetChat.title, false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerChatRow(
    chat: Chat,
    draftText: String?,
    accentPurple: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val botObj = BotRegistry.getBot(chat.id) ?: BotRegistry.getCustomBots().find { it.id == chat.id }
    val customBot = botObj as? CustomBot
    val avatarUrl = customBot?.botPicUri?.takeIf { it.isNotBlank() } ?: "https://picsum.photos/seed/${chat.id}/200"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Выбрать чат ${chat.title}"
            }
            .testTag("picker_chat_row_${chat.id}")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PickerCircleAvatar(
            avatarUrl = avatarUrl,
            title = chat.title,
            isBot = chat.isBot,
            size = 48.dp
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.isChannel) {
                    Icon(
                        Icons.Filled.Campaign,
                        contentDescription = null,
                        tint = accentPurple,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isGroup) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Color(0xFF4DD0E1),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                } else if (chat.isBot) {
                    Icon(
                        Icons.Filled.SmartToy,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isMuted) {
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Без звука",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            when {
                !draftText.isNullOrBlank() -> {
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFFEF5350), fontWeight = FontWeight.Medium)) {
                            append("Черновик: ")
                        }
                        append(draftText)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                !chat.lastMessageSenderName.isNullOrBlank() && (chat.isGroup || chat.lastMessageSenderName == "You") -> {
                    val senderPrefix = if (chat.lastMessageSenderName == "You") "Вы" else chat.lastMessageSenderName
                    val annotated = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)) {
                            append("$senderPrefix: ")
                        }
                        append(chat.lastMessage)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                chat.lastMessage.isNotBlank() -> {
                    Text(
                        text = chat.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = if (chat.isChannel) "Канал" else if (chat.isGroup) "Группа" else "Начать общение",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.lastMessageSenderName == "You") {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = null,
                        tint = accentPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (chat.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Закреплённый чат",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer(rotationZ = 45f)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (chat.lastMessageTimestamp > 0L) {
                    val timeLabel = remember(chat.lastMessageTimestamp) {
                        formatPickerTimestamp(chat.lastMessageTimestamp)
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.5.sp
                    )
                }
            }

            if (chat.unreadCount > 0) {
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (chat.isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else accentPurple)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chat.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCircleAvatar(
    avatarUrl: String,
    title: String,
    isBot: Boolean,
    size: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val letter = title.trim().take(1).ifBlank { "U" }.uppercase()
        Text(
            text = letter,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(avatarUrl)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    }
}

private fun formatPickerPresence(presence: UserPresence?): String {
    if (presence?.isOnline == true) return "в сети"
    val lastSeen = presence?.lastSeen ?: 0L
    if (lastSeen <= 0L) return "был(а) недавно"
    val diff = System.currentTimeMillis() - lastSeen
    return when {
        diff < 60_000L -> "был(а) только что"
        diff < 3600_000L -> "был(а) ${diff / 60_000L} мин. назад"
        diff < 86_400_000L -> "был(а) ${diff / 3600_000L} ч. назад"
        else -> "был(а) недавно"
    }
}

private fun formatPickerTimestamp(ts: Long): String {
    val date = Date(ts)
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    return when {
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        }
        System.currentTimeMillis() - ts < 7L * 86_400_000L -> {
            SimpleDateFormat("EEE", Locale("ru")).format(date).lowercase()
        }
        else -> {
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(date)
        }
    }
}
