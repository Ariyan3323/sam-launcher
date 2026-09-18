package de.szalkowski.activitylauncher.agent.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.os.UserHandle
import android.provider.Settings
import java.util.Calendar

/** Read-only app inventory and usage insights for the local Sam assistant. */
class AppManagerRepository(private val context: Context) {
    private val packageManager = context.packageManager

    fun getInstalledApps(): List<InstalledApp> {
        val usage = usageStatsByPackage()
        return packageManager.getInstalledApplications(android.content.pm.PackageManager.MATCH_ALL)
            .mapNotNull { app ->
                runCatching {
                    InstalledApp(
                        name = app.loadLabel(packageManager).toString(),
                        packageName = app.packageName,
                        icon = app.loadIcon(packageManager),
                        sizeBytes = appSizeBytes(app),
                        lastUsedAt = usage[app.packageName] ?: 0L,
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }

    fun launchAppByName(appName: String): LaunchResult {
        val query = appName.trim()
        if (query.isBlank()) return LaunchResult(false, "نام برنامه خالی است.")
        val match = getInstalledApps().firstOrNull {
            it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        } ?: return LaunchResult(false, "برنامه‌ای با نام «$query» پیدا نشد.")
        val intent = packageManager.getLaunchIntentForPackage(match.packageName)
            ?: return LaunchResult(false, "برای «${match.name}» مسیر اجرا پیدا نشد.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            LaunchResult(true, "${match.name} باز شد.")
        }.getOrElse { LaunchResult(false, "باز کردن ${match.name} ممکن نشد.") }
    }

    fun getUnusedOrHeavyApps(
        unusedAfterDays: Int = 30,
        heavyThresholdBytes: Long = 500L * 1024L * 1024L,
    ): List<InstalledApp> {
        val cutoff = System.currentTimeMillis() - unusedAfterDays * DAY_MILLIS
        return getInstalledApps().filter { app ->
            (app.lastUsedAt > 0L && app.lastUsedAt < cutoff) || app.sizeBytes >= heavyThresholdBytes
        }.sortedWith(compareByDescending<InstalledApp> { it.sizeBytes }.thenBy { it.name.lowercase() })
    }

    fun hasUsageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private fun usageStatsByPackage(): Map<String, Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !hasUsageAccess()) return emptyMap()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val start = System.currentTimeMillis() - 365L * DAY_MILLIS
        return runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_YEARLY, start, System.currentTimeMillis())
                .associate { it.packageName to it.lastTimeUsed }
        }.getOrDefault(emptyMap())
    }

    private fun appSizeBytes(app: ApplicationInfo): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return 0L
        return runCatching {
            val storage = context.getSystemService(StorageStatsManager::class.java)
            val stats = storage.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                app.packageName,
                UserHandle.getUserHandleForUid(app.uid),
            )
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        }.getOrDefault(0L)
    }

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val sizeBytes: Long,
    val lastUsedAt: Long,
    val isSystemApp: Boolean,
) {
    fun sizeInMegabytes(): Long = sizeBytes / (1024L * 1024L)

    fun lastUsedLabel(): String = if (lastUsedAt <= 0L) "آمار استفاده در دسترس نیست" else {
        val calendar = Calendar.getInstance().apply { timeInMillis = lastUsedAt }
        "آخرین استفاده: ${calendar.get(Calendar.YEAR)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
}

data class LaunchResult(val success: Boolean, val message: String)
