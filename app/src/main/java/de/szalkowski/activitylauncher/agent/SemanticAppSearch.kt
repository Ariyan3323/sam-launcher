package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

object SemanticAppSearch {
    private val aliases = mapOf(
        "عکس" to listOf("photo", "gallery", "image", "تصویر", "گالری"),
        "ویرایش عکس" to listOf("photo", "editor", "picsart", "تصویر", "ادیت"),
        "قبض" to listOf("bill", "payment", "wallet", "بانک", "پرداخت"),
        "پرداخت" to listOf("payment", "wallet", "bank", "بانک", "پرداخت"),
        "نقشه" to listOf("map", "navigation", "نقشه", "مسیریابی"),
        "موسیقی" to listOf("music", "spotify", "podcast", "موسیقی"),
        "پیام" to listOf("message", "sms", "chat", "messenger", "پیام"),
        "پیام‌رسان" to listOf("messenger", "telegram", "whatsapp", "message", "پیام"),
        "مرورگر" to listOf("browser", "chrome", "firefox", "مرورگر"),
        "اینستاگرام" to listOf("instagram", "اینستا"),
        "اینستا" to listOf("instagram"),
        "واتساپ" to listOf("whatsapp", "واتس اپ", "واتزاپ"),
        "تلگرام" to listOf("telegram"),
        "یوتیوب" to listOf("youtube", "یو تیوب"),
        "کروم" to listOf("chrome", "google chrome"),
        "فایرفاکس" to listOf("firefox"),
        "تیک تاک" to listOf("tiktok"),
        "اسنپ" to listOf("snapp", "snapp.ir", "com.snapp"),
        "اسنپ فود" to listOf("snappfood", "snapp food", "com.snappfood"),
        "نتفلیکس" to listOf("netflix"),
        "اسپاتیفای" to listOf("spotify"),
        "شیپور" to listOf("sheypoor", "sheypur", "sheypour", "com.sheypoor"),
        "ترب" to listOf("torob", "com.torob", "torobshop"),
        "ایسام" to listOf("esam", "esam.ir", "com.esam"),
        "دیجی کالا" to listOf("digikala", "دیجیکالا"),
        "دیوار" to listOf("divar", "divar.ir"),
        "کافه بازار" to listOf("cafebazaar", "bazaar"),
        "بازار" to listOf("cafebazaar", "bazaar"),
        "آپارات" to listOf("aparat"),
        "بله" to listOf("bale", "bale messenger"),
        "روبیکا" to listOf("rubika"),
        "ایتا" to listOf("eitaa"),
        "نشان" to listOf("neshan"),
        "بلد" to listOf("balad"),
        "تنظیمات" to listOf("settings", "setting"),
        "دوربین" to listOf("camera"),
        "ماشین حساب" to listOf("calculator", "calc"),
        "ساعت" to listOf("clock", "alarm"),
        "مدیا پلیر" to listOf("media player", "mediaplayer", "video player", "player"),
        "پخش کننده" to listOf("media player", "mediaplayer", "video player", "player"),
        "ویدیو پلیر" to listOf("video player", "videoplayer", "mx player", "vlc"),
        "فایل منیجر" to listOf("file manager", "filemanager", "files", "explorer"),
        "مدیریت فایل" to listOf("file manager", "filemanager", "files", "explorer")
    )

    fun findMatches(context: Context, query: String, limit: Int = 12): List<ApplicationInfo> {
        val normalized = normalize(query).removeCommandWords()
        if (normalized.isBlank()) return emptyList()
        val terms = buildTerms(normalized)
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { app -> app to score(pm, app, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun buildTerms(query: String): List<String> {
        val matchedAliases = aliases.entries
            .filter { query.contains(normalize(it.key)) || normalize(it.key).contains(query) }
            .flatMap { listOf(it.key) + it.value }
        return (listOf(query) + query.split(Regex("\\s+")) + matchedAliases)
            .map(::normalize)
            .filter { it.length >= 2 }
            .distinct()
    }

    private fun score(pm: PackageManager, app: ApplicationInfo, terms: List<String>): Int {
        val labelVariants = variants(app.loadLabel(pm).toString())
        val packageVariants = variants(app.packageName)
        return terms.fold(0) { total, term ->
            total + when {
                labelVariants.any { it == term } || packageVariants.any { it == term } -> 30
                labelVariants.any { it.startsWith(term) } -> 20
                packageVariants.any { it.startsWith(term) } -> 14
                labelVariants.any { it.contains(term) } -> 11
                packageVariants.any { it.contains(term) } -> 7
                term.length >= 3 && (labelVariants.any { fuzzyContains(it, term) } || packageVariants.any { fuzzyContains(it, term) }) -> 3
                else -> 0
            }
        }
    }

    private fun fuzzyContains(value: String, query: String): Boolean {
        val window = query.length.coerceAtLeast(3)
        return value.indices.any { start ->
            val end = (start + window + 1).coerceAtMost(value.length)
            levenshtein(value.substring(start, end), query) <= (query.length / 4).coerceAtLeast(1)
        }
    }

    private fun levenshtein(left: String, right: String): Int {
        val previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { i, leftChar ->
            var diagonal = previous[0]
            previous[0] = i + 1
            right.forEachIndexed { j, rightChar ->
                val above = previous[j + 1]
                previous[j + 1] = if (leftChar == rightChar) diagonal
                else 1 + minOf(diagonal, above, previous[j])
                diagonal = above
            }
        }
        return previous[right.length]
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('\u200c', ' ')
        .replace(Regex("[\\s_\\-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.removeCommandWords(): String = replace(
        Regex("^(لطفاً\\s+|لطفا\\s+)?(برنامه\\s+)?(باز\\s*کن|اجرا\\s*کن|راه\\s*اندازی\\s*کن|بازکردن|اجرا|open|launch|run|start)\\s+", RegexOption.IGNORE_CASE), ""
    ).replace(Regex("\\s+(رو|را|rо|please)$", RegexOption.IGNORE_CASE), "").trim()

    private fun variants(value: String): List<String> {
        val normalized = normalize(value)
        val transliterated = normalized.map { persianToLatin[it] ?: it }.joinToString("")
        return listOf(normalized, transliterated).distinct()
    }

    private val persianToLatin = mapOf(
        'ا' to "a", 'ب' to "b", 'پ' to "p", 'ت' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "ch",
        'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "z", 'ر' to "r", 'ز' to "z", 'ژ' to "zh",
        'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "z", 'ط' to "t", 'ظ' to "z", 'ع' to "a",
        'غ' to "gh", 'ف' to "f", 'ق' to "gh", 'ک' to "k", 'گ' to "g", 'ل' to "l", 'م' to "m",
        'ن' to "n", 'و' to "v", 'ه' to "h", 'ی' to "y"
    )
}
