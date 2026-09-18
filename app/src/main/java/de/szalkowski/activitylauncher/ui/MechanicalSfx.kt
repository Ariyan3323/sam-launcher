package de.szalkowski.activitylauncher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import de.szalkowski.activitylauncher.R

/** Low-latency, lifecycle-safe mechanical HUD sound effects. */
class MechanicalSfx(context: Context) : AutoCloseable {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val click = pool.load(context, R.raw.sfx_click, 1)
    private val panel = pool.load(context, R.raw.sfx_panel, 1)
    private val beep = pool.load(context, R.raw.sfx_beep, 1)

    fun click() = play(click, .42f)
    fun panelOpen() = play(panel, .48f)
    fun messageSent() = play(beep, .55f)

    private fun play(sound: Int, volume: Float) {
        if (sound != 0) pool.play(sound, volume, volume, 1, 0, 1f)
    }

    override fun close() { pool.release() }
}
