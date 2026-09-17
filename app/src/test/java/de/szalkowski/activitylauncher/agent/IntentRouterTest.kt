package de.szalkowski.activitylauncher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {
    @Test
    fun latestMessengerMessageIsConsentGated() {
        val result = IntentRouter.route("آخرین پیام پیام نگار را بخوان")
        assertEquals(SamIntentType.READ_LATEST_NOTIFICATION, result.type)
        assertEquals("messenger", result.source)
        assertTrue(result.requiresConfirmation)
    }

    @Test
    fun latestGmailMessageIsConsentGated() {
        val result = IntentRouter.route("check my latest Gmail message")
        assertEquals(SamIntentType.READ_LATEST_NOTIFICATION, result.type)
        assertEquals("gmail", result.source)
        assertTrue(result.requiresConfirmation)
    }

    @Test
    fun openingAnAppIsNotTheSameAsReadingMessages() {
        val result = IntentRouter.route("پیام‌رسان را باز کن")
        assertEquals(SamIntentType.OPEN_APP, result.type)
        assertEquals("پیام رسان", result.target)
    }

    @Test
    fun searchAndChatRemainSeparate() {
        assertEquals(SamIntentType.SEARCH_WEB, IntentRouter.route("جستجو کن اخبار امروز").type)
        assertEquals(SamIntentType.CHAT, IntentRouter.route("امروز حالم خوب نیست").type)
    }
}
