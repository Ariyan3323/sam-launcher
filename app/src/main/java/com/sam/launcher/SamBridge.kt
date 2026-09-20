package com.sam.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import android.webkit.JavascriptInterface

class SamBridge(private val context: Context) {

    // ۱. تنظیم میزان صدا (کم / زیاد)
    @JavascriptInterface
    fun adjustVolume(increase: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    // ۲. تنظیم میزان روشنایی صفحه (درصد بین ۰ تا ۱۰۰)
    @JavascriptInterface
    fun setBrightness(percent: Int) {
        try {
            if (Settings.System.canWrite(context)) {
                val brightness = (percent * 255 / 100).coerceIn(0, 255)
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ۳. خواندن دمای واقعی باتری/پردازنده
    @JavascriptInterface
    fun getDeviceTemperature(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temp / 10.0f
    }

    // ۴. باز کردن دقیق برنامهها بر اساس نام (جلوگیری از باز شدن اشتباه تلگرام)
    @JavascriptInterface
    fun launchAppByName(appName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val cleanQuery = appName.trim().lowercase()

        for (appInfo in packages) {
            val appLabel = pm.getApplicationLabel(appInfo).toString().lowercase()
            if (appLabel == cleanQuery || appLabel.contains(cleanQuery)) {
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }
}
