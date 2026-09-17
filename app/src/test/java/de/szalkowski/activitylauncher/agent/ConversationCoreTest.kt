package de.szalkowski.activitylauncher.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCoreTest {
    private val core = ConversationCore()

    @Test
    fun respondsEmpatheticallyInPersian() {
        val answer = core.reply("حالم گرفته")
        assertTrue(answer.contains("متأسفم"))
    }

    @Test
    fun answersAStableKnowledgeQuestionOffline() {
        val answer = core.reply("انبردست چیست؟")
        assertTrue(answer.contains("ابزار"))
    }

    @Test
    fun keepsEnglishConversationAvailableOffline() {
        val answer = core.reply("Who are you?")
        assertTrue(answer.contains("سام"))
    }

    @Test
    fun doesNotGoSilentOnUnknownOfflineQuestion() {
        val answer = core.reply("یک موضوع تازه را توضیح بده")
        assertTrue(answer.isNotBlank())
        assertTrue(answer.contains("آفلاین"))
    }
}
