package com.example.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.extractReplyMediaMeta
import com.example.ui.group.resolveReplyQuoteAuthorName
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReplyQuoteAndForwardTest {

    @Test
    fun resolveReplyQuoteAuthorName_prefersPersistedAuthorAndCrossChatSuffix() {
        val groupChat = Chat(id = "group_1", title = "Work Chat", isGroup = true, lastMessage = "")
        val targetChat = Chat(id = "saved_messages", title = "Избранное", lastMessage = "")

        val crossChatReply = Message(
            id = "m1",
            chatId = targetChat.id,
            senderId = "u_me",
            text = "Check this out",
            replyToMessageId = "orig_1",
            replyToMessageText = "quoted text",
            replyAuthorName = "ТэМ_О5",
            replySourceChatId = groupChat.id,
            replySourceChatTitle = groupChat.title,
            replyIsFragmentQuote = true
        )

        assertEquals(
            "ТэМ_О5 (Work Chat)",
            resolveReplyQuoteAuthorName(
                message = crossChatReply,
                repliedMessage = null,
                chat = targetChat
            )
        )

        val sameChatReply = crossChatReply.copy(
            chatId = groupChat.id,
            replySourceChatId = groupChat.id,
            replySourceChatTitle = groupChat.title
        )
        assertEquals(
            "ТэМ_О5",
            resolveReplyQuoteAuthorName(
                message = sameChatReply,
                repliedMessage = null,
                chat = groupChat
            )
        )

        val selfTitleReply = crossChatReply.copy(
            replyAuthorName = "KuoteX Officiall",
            replySourceChatTitle = "KuoteX Officiall"
        )
        assertEquals(
            "KuoteX Officiall",
            resolveReplyQuoteAuthorName(
                message = selfTitleReply,
                repliedMessage = null,
                chat = targetChat
            )
        )
    }

    @Test
    fun replyDraftAndMessageMetadataPreserveFragmentQuoteAndSourceChat() {
        val sourceMessage = Message(
            id = "msg_42",
            chatId = "group_src",
            senderId = "user_99",
            text = "Но если что то вкусное делают, то сначала едят они",
            senderDisplayName = "ТэМ_О5"
        )
        val draft = ReplyDraft(
            targetChatId = "saved_messages",
            message = sourceMessage,
            customQuoteText = "сначала едят они",
            isFragmentQuote = true,
            authorName = "ТэМ_О5",
            sourceChatId = "group_src",
            sourceChatTitle = "Фурри чат"
        )

        assertTrue(draft.isFragmentQuote)
        assertEquals("сначала едят они", draft.customQuoteText)
        assertEquals("ТэМ_О5", draft.authorName)
        assertEquals("group_src", draft.sourceChatId)
        assertEquals("Фурри чат", draft.sourceChatTitle)

        val session = TargetChatPickerSession(
            mode = "reply",
            sourceChatId = draft.sourceChatId,
            sourceChatTitle = draft.sourceChatTitle,
            replyDraft = draft
        )
        assertEquals("reply", session.mode)
        assertEquals("msg_42", session.replyDraft?.message?.id)
        assertTrue(session.replyDraft?.isFragmentQuote == true)
    }

    @Test
    fun extractReplyMediaMeta_handlesGifImageVideoNoteAndPlainText() {
        val gifPayload = JSONObject().apply {
            put("type", "gif")
            put("uri", "https://example.com/anim.gif")
            put("title", "Test GIF")
        }.toString()

        val gifMsg = Message(
            id = "m_gif",
            chatId = "c1",
            senderId = "u1",
            text = "",
            documentData = gifPayload
        )
        val gifMeta = extractReplyMediaMeta(gifMsg)
        assertNotNull(gifMeta)
        assertTrue(gifMeta!!.isGif)
        assertEquals("GIF", gifMeta.summaryLabel)
        assertEquals("https://example.com/anim.gif", gifMeta.thumbUrl)

        val imagePayload = JSONObject().apply {
            put("mimeType", "image/jpeg")
            put("uri", "file:///photo.jpg")
        }.toString()
        val photoMsg = Message(
            id = "m_photo",
            chatId = "c1",
            senderId = "u1",
            text = "Look here",
            mediaType = "image",
            documentData = imagePayload
        )
        val photoMeta = extractReplyMediaMeta(photoMsg)
        assertNotNull(photoMeta)
        assertFalse(photoMeta!!.isGif)
        assertEquals("Look here", photoMeta.summaryLabel)
        assertEquals("file:///photo.jpg", photoMeta.thumbUrl)

        val plainMsg = Message(
            id = "m_text",
            chatId = "c1",
            senderId = "u1",
            text = "Simple message"
        )
        assertNull(extractReplyMediaMeta(plainMsg))
    }
}
