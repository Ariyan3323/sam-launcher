package de.szalkowski.activitylauncher.agent

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyGuardTest {

    private lateinit var context: Application
    private lateinit var guard: PrivacyGuard

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("sam_privacy", 0).edit().clear().commit()
        guard = PrivacyGuard(context)
    }

    @Test
    fun defaultStateIsNormalAndAllowsEverything() {
        assertFalse(guard.privacyMode)
        assertFalse(guard.guestMode)
        assertEquals("عادی", guard.status())
        assertTrue(guard.canPersistPersonalMemory())
        assertTrue(guard.canOpenApp("instagram"))
    }

    @Test
    fun privacyModeBlocksMemoryButDoesNotRestrictWhichAppsOpen() {
        guard.setPrivacyMode(true)

        assertTrue(guard.privacyMode)
        assertFalse(guard.canPersistPersonalMemory())
        assertTrue(guard.canOpenApp("any random app"))
        assertEquals("حریم خصوصی", guard.status())
    }

    @Test
    fun guestModeRestrictsAppLaunchesToTheAllowlist() {
        guard.setGuestMode(true)

        assertFalse(guard.canPersistPersonalMemory())
        assertTrue(guard.canOpenApp("تنظیمات"))
        assertTrue(guard.canOpenApp("Chrome browser"))
        assertTrue(guard.canOpenApp("ماشین حساب"))
        assertFalse(guard.canOpenApp("instagram"))
        assertFalse(guard.canOpenApp("بازی جدید"))
        assertEquals("مهمان", guard.status())
    }

    @Test
    fun guestAllowlistMatchIsCaseInsensitive() {
        guard.setGuestMode(true)

        assertTrue(guard.canOpenApp("CHROME"))
        assertTrue(guard.canOpenApp("Camera app"))
    }

    @Test
    fun statusPrefersGuestModeWhenBothModesAreOn() {
        guard.setPrivacyMode(true)
        guard.setGuestMode(true)

        assertEquals("مهمان", guard.status())
        assertFalse(guard.canPersistPersonalMemory())
    }

    @Test
    fun turningModesOffRestoresNormalBehaviour() {
        guard.setGuestMode(true)
        guard.setPrivacyMode(true)

        guard.setGuestMode(false)
        guard.setPrivacyMode(false)

        assertEquals("عادی", guard.status())
        assertTrue(guard.canPersistPersonalMemory())
        assertTrue(guard.canOpenApp("instagram"))
    }

    @Test
    fun settingsPersistAcrossNewGuardInstancesOnTheSameContext() {
        guard.setGuestMode(true)

        val secondGuard = PrivacyGuard(context)

        assertTrue(secondGuard.guestMode)
        assertEquals("مهمان", secondGuard.status())
    }
}
