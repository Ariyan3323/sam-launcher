package de.szalkowski.activitylauncher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamOrchestratorTest {
    private val router = SamOrchestrator()

    @Test
    fun currentQuestionPrefersWebWhenAvailable() {
        val d = router.route("آخرین اخبار هوش مصنوعی چیست؟", true, true, true, true)
        assertEquals(SamRoute.WEB_RESEARCH, d.route)
    }

    @Test
    fun deepResearchUsesFugu() {
        val d = router.route("یک تحقیق عمیق و مقایسه کامل انجام بده", true, true, true, false)
        assertEquals(SamRoute.FUGU, d.route)
    }

    @Test
    fun sensitiveActionsRequireConfirmation() {
        val d = router.route("این پیام را بفرست", true, true, true, false)
        assertTrue(d.requiresConfirmation)
    }

    @Test
    fun offlineFallbackWorks() {
        val d = router.route("سلام", true, false, false, false)
        assertEquals(SamRoute.LOCAL, d.route)
    }
}
