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
        "پیام" to listOf("message", "sms", "chat", "پیام"),
        "مرورگر" to listOf("browser", "chrome", "firefox", "مرورگر"),
        "عکس ها" to listOf("photo", "gallery", "image", "تصویر", "گالری"),
        "اینستاگرام" to listOf("instagram"),
        "اینستا" to listOf("instagram"),
        "واتساپ" to listOf("whatsapp"),
        "واتس اپ" to listOf("whatsapp"),
        "تلگرام" to listOf("telegram"),
        "یوتیوب" to listOf("youtube"),
        "کروم" to listOf("chrome", "google chrome"),
        "فایرفاکس" to listOf("firefox"),
        "تیک تاک" to listOf("tiktok"),
        "تیک‌تاک" to listOf("tiktok"),
        "نتفلیکس" to listOf("netflix"),
        "اسپاتیفای" to listOf("spotify"),
        "شیپور" to listOf("sheypoor", "sheypur", "sheypour", "com.sheypoor"),
        "شپور" to listOf("sheypoor", "sheypur", "sheypour"),
        "ترب" to listOf("torob", "com.torob", "torobshop"),
        "تربچه" to listOf("torob", "torobshop"),
        "اسنپ" to listOf("snapp", "snapp.ir", "com.snapp"),
        "اسنپ فود" to listOf("snappfood", "snapp food", "com.snappfood"),
        "ایسام" to listOf("esam", "esam.ir", "com.esam"),
        "دیوار" to listOf("divar", "divar.ir"),
        "کافه بازار" to listOf("cafebazaar", "bazaar", "کافه بازار"),
        "بازار" to listOf("cafebazaar", "bazaar"),
        "آپارات" to listOf("aparat"),
        "بله" to listOf("bale", "bale messenger"),
        "روبیکا" to listOf("rubika"),
        "ایتا" to listOf("eitaa"),
        "نشان" to listOf("neshan"),
        "بلد" to listOf("balad")
    )

    fun findMatches(context: Context, query: String, limit: Int = 12): List<ApplicationInfo> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        // Split the raw query before normalizing: normalize() removes whitespace.
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val terms = (aliases.entries.firstOrNull { normalized.contains(normalize(it.key)) }
            ?.value.orEmpty() + words).map(::normalize).distinct()
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .map { app -> app to score(pm, app, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun score(pm: PackageManager, app: ApplicationInfo, terms: List<String>): Int {
        val labelVariants = variants(app.loadLabel(pm).toString())
        val packageVariants = variants(app.packageName)
        return terms.fold(0) { total, term ->
            total + when {
                term.isBlank() -> 0
                labelVariants.any { it == term } || packageVariants.any { it == term } -> 10
                labelVariants.any { it.startsWith(term) } -> 8
                packageVariants.any { it.startsWith(term) } -> 6
                labelVariants.any { it.contains(term) } -> 5
                packageVariants.any { it.contains(term) } -> 3
                term.length >= 3 && (labelVariants.any { fuzzyContains(it, term) } || packageVariants.any { fuzzyContains(it, term) }) -> 2
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
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace('ة', 'ه')
        .replace(Regex("[\\s_\\-‌]+"), "")
        .trim()

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
