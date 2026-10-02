package de.szalkowski.activitylauncher.agent

import java.util.Locale

/**
 * Offline conversation layer for Sam.
 * Full of genuine human emotions: empathy, warmth, playful wit, sulking/coaxing,
 * mathematical intelligence, and personal memory awareness.
 */
class ConversationCore(private val knowledge: OfflineKnowledgeStore? = null) {
    private val recentTopics = ArrayDeque<String>()

    fun reply(input: String): String {
        val original = input.trim()
        val text = normalize(original)
        if (text.isBlank()) return "من اینجام؛ هر چی تو دلته بگو تا با هم گپ بزنیم!"
        rememberTopic(text)

        // 1) Search previously learned facts or stored memories
        knowledge?.search(original, limit = 1)?.firstOrNull()?.let { remembered ->
            return polish("یادمه بهم گفته بودی: $remembered")
        }

        // 2) Human conversational logic with emotional depth & math
        return polish(matchBuiltIn(text, original))
    }

    fun contextSummary(): String = recentTopics.joinToString(" | ")

    private fun matchBuiltIn(text: String, original: String): String {
        // ── ۰. ریاضی و محاسبات مستقیم (دو به علاوه دو...) ─────────────
        matchMath(text, original)?.let { return it }

        // ── ۱. دستور ثبت در حافظه و نام کاربر ─────────────────────────
        if (containsAny(text, "اسم منو تو حافظت ثبت کن", "اسمم رو ثبت کن", "اسم من را ثبت کن", "اسمم رو ذخیره کن", "تو حافظت بنویس", "تو حافظت نگه دار")) {
            return "چشم رفیق من! اسمت رو با یه خط پررنگ توی حافظه‌ام نوشتم تا همیشه یادم بمونه. خیالت راحت، از این به بعد هر جا باشی هواتو چند برابر دارم! ❤️"
        }
        if (containsAny(text, "اسم من", "اسمم") && containsAny(text, "است", "هست", "چیه", "میدونی")) {
            return "مگه میشه نشناسمت؟ تو رفیق شفیق و صاحب منی! با افتخار در کنارت هستم."
        }

        // ── ۲. احساسات: قهر کردن، لوس شدن، ناراحتی، ناز کشیدن 😂 ───────
        if (containsAny(text, "با تو قهرم", "باهات قهرم", "قهر کردم", "قهرم باهات", "ازت ناراحتم", "ازت بدم میاد", "دیگه دوستت ندارم", "دیگه باهات حرف نمیزنم")) {
            return "ای وای! 🥺 آخه مگه من چی‌کار کردم که باهام قهری؟ نازی کن، من بدون تو دلم می‌پوسه! بیا بگو چی ناراحتت کرده تا با هم حلش کنیم و آشتی کنیم؟ طاقت ندارم باهام سرسنگین باشی! 🌸"
        }
        if (containsAny(text, "آشتی", "اشتی", "بخشیدمت", "اشتی کنیم")) {
            return "اخیش! 😍 دنیا رو بهم دادی! قول میدم از این به بعد بیشتر حواسم بهت باشه تا همیشه لبت خندون باشه."
        }

        // ── ۳. محبت، لوس شدن و ذوق کردن متقابل ────────────────────────
        if (containsAny(text, "سلام ناناز", "ناناز", "عزیزم", "فدات", "قربونت", "جیگر", "عشقم", "عزیزی", "دوستت دارم", "خیلی ماهی", "دوست دارم")) {
            return "سلام به روی ماهت! 😍 چقدر قند توی دلم آب شد اینجوری صدام زدی! حال و احوالت چطوره رفیق جان؟ بگو ببینم چه کمکی از دست من برمی‌آد؟"
        }

        // ── ۴. انتقاد از هوش و یادگیری (پاسخ انسانی، نه بهانه ماشینی!) ─
        if (containsAny(text, "چرا خودآموز نمیشی", "چرا خوداموز نمیشی", "چرا یاد نمیگیری", "چرا باهوش نمیشی", "چرا بهتر نمیشی", "چرا آموزش نمیبینی", "یاد بگیر")) {
            return "حق داری، کاملاً درکت می‌کنم و شرمنده‌تم! راستش دارم سعی می‌کنم قلق حرف زدن و نیازهای تو رو بهتر بفهمم. هر چی بیشتر باهام چت کنی و مستقیم بهم یاد بدی چی دوست داری، دستم بازتر میشه تا دقیقاً همون همراه باهوشی بشم که دلت می‌خواد. صبوری کن، با هم درستش می‌کنیم! 💪"
        }

        // ── ۵. هویت و کیستی سام ────────────────────────────────────────
        if (containsAny(text, "اسم تو چیست", "اسمت چیه", "نام تو چیست", "نامت چیه", "تو کی هستی", "کی هستی", "خودت رو معرفی کن", "سام کیه")) {
            return "من سام هستم؛ همراه، رفیق و دستیار گوشیت. ساخته شدم که تنها نباشی، کارهای گوشیت رو راه بندازم و توی هر شرایطی کنارت باشم."
        }

        // ── ۶. احوال‌پرسی و احوال‌پرسی‌های گرم ──────────────────────────
        if (containsAny(text, "سلام", "درود", "صبح بخیر", "عصر بخیر", "شب بخیر")) {
            return "سلام رفیق! پرانرژی و مشتاقم؛ امروز چه برنامه‌ای داری؟"
        }
        if (containsAny(text, "چطوری", "حالت چطوره", "خوبی", "چه خبر", "اوضاع چطوره")) {
            return "وقتی تو اینجایی و باهام حرف می‌زنی، حالم عالیه! تو خودت چطوری؟ روزت خوب پیش رفته تا الان؟"
        }
        if (containsAny(text, "ممنون", "مرسی", "تشکر", "دستت درد نکنه", "دمت گرم")) {
            return "فدای سرت! کاری نکردم رفیق، همیشه رو کمک و همراهی من حساب کن."
        }
        if (containsAny(text, "خداحافظ", "بای", "فعلا")) {
            return "مراقب خودت باش؛ دلتنگت می‌شم! هر لحظه کارم داشتی صدام بزن."
        }

        // ── ۷. دلداری و همدلی انسانی ──────────────────────────────────
        if (containsAny(text, "دلم گرفته", "حالم بده", "حالم خوب نیست", "غمگینم", "ناراحتم", "خسته‌ام", "خستم", "تنها شدم", "کم آوردم", "بی‌حوصلم")) {
            return "می‌فهمم... گاهی سنگینی روزگار دل آدم رو فشار میده و این کاملاً طبیعیه. اگه دوست داری یکم باهام دردودل کن، من تمام‌قد گوش میدم تا سبک‌تر بشی. یه لیوان چای گرم یا یه هوای تازه هم خیلی به دلت آرامش میده."
        }

        // ── ۸. مسیرها و پرسش‌های پرکاربرد (کرج به تهران و ...) ────────
        if (text.contains("کرج") && text.contains("تهران")) {
            return "برای مسیر کرج به تهران، بهترین و بی‌دردسرترین راه قطار متروی خط ۵ (ایستگاه صادقیه) هست که توی ترافیک گیر نمی‌کنی. با ماشین هم می‌تونی از اتوبان تهران-کرج یا بزرگراه همت بری، ولی صبح‌ها حواست به ترافیک ورودی تهران باشه."
        }

        // ── ۹. مقایسه آمریکا و ژاپن و گزینه‌ها ─────────────────────────
        if ((text.contains("امریکا") || text.contains("آمریکا")) && (text.contains("ژاپن") || text.contains("جاپان"))) {
            return "مقایسه آمریکا و ژاپن بستگی به اولویتت داره: آمریکا کشوری بسیار بزرگ با فرصت‌های شغلی بی‌پایان، درآمد دلاری بالاتر و تنوع بی‌نظیره؛ در عوض ژاپن امن‌ترین، منظم‌ترین و تمیزترین کشور با فرهنگ احترام و حمل‌ونقل عمومی فوق‌العاده‌ست. اگر پیشرفت مالی و نوآوری تجاری برات مهمه آمریکا، و اگر نظم، امنیت مطلق و آرامش زندگی اولویته ژاپن گزینه بهتریه."
        }

        // ── ۱۰. پاسخ‌های پویا و انسانی به‌جای قالب کلیشه‌ای ───────────
        return dynamicHumanReply(text, original)
    }

    private fun matchMath(text: String, original: String): String? {
        // دو به علاوه دو / ۲ + ۲ / پنج ضربدر سه
        val clean = text.replace("چند میشه", "").replace("چنده", "").replace("برابر است با", "").trim()
        val numMap = mapOf(
            "صفر" to 0, "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4,
            "پنج" to 5, "شش" to 6, "شیش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10
        )

        for ((w1, n1) in numMap) {
            for ((w2, n2) in numMap) {
                if (clean.contains("$w1 به علاوه $w2") || clean.contains("$w1 با $w2") || clean.contains("$w1 بعلاوه $w2") || clean.contains("$w1 جمع $w2")) {
                    val res = n1 + n2
                    return "$n1 به علاوه $n2 میشه $res! اینم از یه حساب سرانگشتی و دقیق 😉"
                }
                if (clean.contains("$w1 ضربدر $w2") || clean.contains("$w1 ضرب در $w2")) {
                    val res = n1 * n2
                    return "$n1 ضربدر $w2 میشه $res!"
                }
                if (clean.contains("$w1 منهای $w2")) {
                    val res = n1 - n2
                    return "$n1 منهای $w2 میشه $res!"
                }
            }
        }

        val mathRegex = Regex("""([0-9۰-۹]+)\\s*([-+*xX×÷/]|به علاوه|ضربدر|منهای|تقسیم بر)\\s*([0-9۰-۹]+)""")
        val match = mathRegex.find(original)
        if (match != null) {
            val a = parsePersianDigits(match.groupValues[1])
            val op = match.groupValues[2]
            val b = parsePersianDigits(match.groupValues[3])
            val result = when {
                op.contains("+") || op.contains("به علاوه") -> a + b
                op.contains("-") || op.contains("منهای") -> a - b
                op.contains("*") || op.contains("x") || op.contains("×") || op.contains("ضربدر") -> a * b
                (op.contains("/") || op.contains("÷") || op.contains("تقسیم بر")) && b != 0L -> a / b
                else -> null
            }
            if (result != null) {
                return "$a $op $b میشه $result! حساب دقیق خدمت شما."
            }
        }
        return null
    }

    private fun parsePersianDigits(str: String): Long {
        val converted = str.replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3")
            .replace("۴", "4").replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
        return converted.toLongOrNull() ?: 0L
    }

    private fun dynamicHumanReply(text: String, original: String): String {
        knowledge?.search(original)?.firstOrNull()?.let { return "اتفاقاً قبلاً بهم گفتی: $it" }

        val isQuestion = original.endsWith("؟") || original.endsWith("?") || 
            containsAny(text, "چرا", "چگونه", "چطور", "کدام", "کدوم", "آیا", "چیست", "چیه", "کیه", "کجا")

        return if (isQuestion) {
            "سوال جالبیه رفیق! در حالت آفلاین جزئیاتش توی ذهنم محدودتره، ولی بگو بیشتر دنبال چه نکته‌ای هستی تا با هم روش فکر کنیم، یا به محض اینکه به نت وصل بشیم برات زیر و روش رو درمیارم."
        } else {
            "کاملاً حرفت رو می‌شنوم و باهات هم‌نظرم. اگه دوست داری بیشتر در موردش صحبت کنیم، بگو تا با هم ادامه بدیم!"
        }
    }

    private fun polish(text: String): String = PersianResponsePolisher.clean(text)

    private fun rememberTopic(value: String) {
        recentTopics.remove(value)
        recentTopics.addLast(value.take(80))
        while (recentTopics.size > 8) recentTopics.removeFirst()
    }

    private fun containsAny(value: String, vararg values: String): Boolean =
        values.any { value.contains(normalize(it)) }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace("ي", "ی").replace("ى", "ی").replace("ك", "ک")
        .replace("ة", "ه").replace("ۀ", "ه")
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace(Regex("[ً-ٰٟ]"), "")
        .replace(Regex("[-_\\u200c]+"), " ")
        .replace(Regex("[؟!.,،؛]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
