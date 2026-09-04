package de.szalkowski.activitylauncher

import android.content.Context
import android.os.Environment
import java.io.File

/** Exports a small, reusable clock example to the app's private documents folder. */
class CodeWriter(private val context: Context) {
    fun writeClockCode(): File {
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "ActivityLauncher_Code"
        ).apply { mkdirs() }
        return File(directory, "AnalogClockView.kt").apply { writeText(CLOCK_EXAMPLE) }
    }

    companion object {
        private const val CLOCK_EXAMPLE = """package de.szalkowski.activitylauncher

// See AnalogClockView.kt in the Activity Launcher source for the complete implementation.
// Use AnalogClockView(context).setTime(hour, minute, second) to update the display.
"""
    }
}
