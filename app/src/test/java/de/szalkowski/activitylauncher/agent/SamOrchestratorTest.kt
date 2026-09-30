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
        assertTrue(d.specialists.any { it.id == "research" })
    }

    @Test
    fun codingQuestionSelectsHandCoder() {
        val d = router.route("برای من یک برنامه کدنویسی کن", true, true, false, false)
        assertTrue(d.specialists.any { it.id == "coder" && it.organ == SamOrgan.HANDS })
    }

    @Test
    fun artQuestionSelectsHeartArtist() {
        val d = router.route("این عکس را هنری کن", true, true, false, false)
        assertTrue(d.specialists.any { it.id == "artist" && it.organ == SamOrgan.HEART })
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
        assertTrue(d.specialists.any { it.id == "reasoning" })
    }
}
