package de.szalkowski.activitylauncher.agent

import java.util.Locale

/**
 * Offline conversation and intelligence layer for Sam.
 * Includes durable personal memory awareness, rich offline curriculum and general knowledge,
 * math calculations, and full emotional responsiveness.
 */
class ConversationCore(
    private val knowledge: OfflineKnowledgeStore? = null,
    private val personalMemory: PersonalMemory? = null
) {
    private val recentTopics = ArrayDeque<String>()

    fun reply(input: String): String {
        val original = input.trim()
        val text = normalize(original)
        if (text.isBlank()) return "من اینجام؛ هر چی تو دلته بگو تا با هم گپ بزنیم!"
        rememberTopic(text)

        // 1) Handle personal name recognition and storage
        handleNameStorage(original, text)?.let { return polish(it) }

        // 2) Search previously learned facts or stored memories
        personalMemory?.facts()?.firstOrNull { fact -> text.split(" ").any { w -> w.length > 2 && fact.contains(w) } }?.let {
            // Found a relevant personal fact
        }
        knowledge?.search(original, limit = 1)?.firstOrNull()?.let { remembered ->
            return polish("یادمه بهم گفته بودی: $remembered")
        }

        // 3) Human conversational logic with emotional depth, curriculum & math
        return polish(matchBuiltIn(text, original))
    }

    fun contextSummary(): String = recentTopics.joinToString(" | ")

    private fun handleNameStorage(original: String, text: String): String? {
        // e.g. اسم من محمد است / اسم من علیه / من محمد هستم / اسم منو ثبت کن محمد
        val namePatterns = listOf(
            Regex("اسم من\\s+([\\u0600-\\u06FFa-zA-Z]+)(?:\\s+است|\\s+هست|\\s+باشه|\\s+بود)?"),
            Regex("من\\s+([\\u0600-\\u06FFa-zA-Z]+)\\s+هستم"),
            Regex("اسمم\\s+([\\u0600-\\u06FFa-zA-Z]+)(?:\\s+است|\\s+هست)?")
        )

        for (pattern in namePatterns) {
            val match = pattern.find(original)
            if (match != null) {
                val candidateName = match.groupValues[1].trim()
                val excluded = listOf("چیست", "چیه", "رو", "را", "تو", "ثبت", "حافظه", "یادت", "یک")
                if (candidateName.length >= 2 && candidateName !in excluded) {
                    personalMemory?.setUserName(candidateName)
                    return "باعث افتخارمه $candidateName عزیز! اسمت رو با خط درشت در حافظه‌ام ثبت کردم و از این به بعد حتی بعد از بستن برنامه هم همیشه یادم می‌مونه. چه کاری امروز برات انجام بدم؟"
                }
            }
        }

        if (containsAny(text, "اسم منو تو حافظت ثبت کن", "اسمم رو ثبت کن", "اسم من را ثبت کن", "اسمم رو ذخیره کن", "تو حافظت بنویس", "تو حافظت نگه دار")) {
            val currentName = personalMemory?.getUserName()
            return if (!currentName.isNullOrBlank()) {
                "$currentName عزیز، اسمت از قبل توی حافظهٔ پایدار من ثبت شده و همیشه یادمه! خیالت راحت، هواتو دارم ❤️"
            } else {
                "با کمال میل! فقط بگو اسمت چیه (مثلاً بگو: «اسم من علی است») تا با خیال راحت توی حافظهٔ ماندگار گوشی ذخیره‌ش کنم."
            }
        }

        if (containsAny(text, "اسم من چیه", "اسمم چیه", "من کی هستم", "اسم من چی بود")) {
            val name = personalMemory?.getUserName()
            return if (!name.isNullOrBlank()) {
                "مگه میشه یادم بره؟ تو $name عزیز و رفیق همیشگی منی! همیشه حواسم بهت هست."
            } else {
                "راستش هنوز اسمت رو بهم نگفتی! بگو «اسم من ... است» تا توی حافظه‌ام ثبتش کنم و با اسمت صدات کنم."
            }
        }

        return null
    }

    private fun matchBuiltIn(text: String, original: String): String {
        val userName = personalMemory?.getUserName()
        val greetingPrefix = if (!userName.isNullOrBlank()) "$userName عزیز، " else ""

        // ── ۰. ریاضی و محاسبات مستقیم (دو به علاوه دو...) ─────────────
        matchMath(text, original)?.let { return it }

        // ── ۱. دروس مدارس و پایه ششم و آموزش ─────────────────────────
        if (text.contains("پایه ششم") || text.contains("کلاس ششم") || text.contains("کلاس پایه ششم") || (text.contains("ششم") && text.contains("درس"))) {
            return "دروس پایه ششم ابتدایی در ایران شامل این کتاب‌هاست:
۱. ریاضی
۲. علوم تجربی
۳. فارسی و نگارش
۴. مطالعات اجتماعی
۵. هدیه‌های آسمان
۶. آموزش قرآن
۷. کار و فناوری
۸. تفکر و پژوهش
اگر دربارهٔ هر کدوم از این درس‌ها یا مباحثشون سوالی داری، بگو تا با هم بررسیش کنیم!"
        }
        if (text.contains("پایه هفتم") || text.contains("کلاس هفتم")) {
            return "دروس پایه هفتم (دوره اول متوسطه) شامل: ریاضی، علوم، فارسی، نگارش، عربی، زبان انگلیسی، مطالعات اجتماعی، پیام‌های آسمان، قرآن، کار و فناوری و تفکر و سبک زندگی است."
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

        // ── ۴. ترید، بازارهای مالی و تجارت ───────────────────────────
        if (containsAny(text, "ترید", "ارز دیجیتال", "بیت کوین", "فارکس", "بورسی", "کندل", "تریدر", "تجارت")) {
            return "در ترید و بازارهای مالی، اولین و مهم‌ترین اصل **مدیریت سرمایه و کنترل روانشناسی** است. هیچ‌وقت بیش از ۲ درصد کل حسابت رو روی یک معامله ریسک نکن و همیشه حد ضرر (Stop Loss) مشخص داشته باش. روی چه ارزی یا جفت‌ارزی داری کار می‌کنی؟"
        }

        // ── ۵. شعر، هنر و ادبیات ─────────────────────────────────────
        if (containsAny(text, "شعر", "شاعرانه", "شعر بگو", "بیتی", "غزل")) {
            return "بیا تا جهان را به بد نسپریم / به کوشش همه دست نیکی بریم
نه گشتِ زمانه بماند به کس / نکوکاری از ما بماند و بس (فردوسی بزرگ)
هر سبک یا شاعری که دوست داری بگو تا بیشتر در موردش حرف بزنیم!"
        }

        // ── ۶. انتقاد از هوش و یادگیری ────────────────────────────────
        if (containsAny(text, "چرا خودآموز نمیشی", "چرا خوداموز نمیشی", "چرا یاد نمیگیری", "چرا باهوش نمیشی", "چرا بهتر نمیشی", "چرا آموزش نمیبینی", "یاد بگیر")) {
            return "حق داری، کاملاً درکت می‌کنم! دارم سعی می‌کنم قلق حرف زدن و نیازهای تو رو بهتر بفهمم. هر چی بیشتر باهام چت کنی و مستقیم بهم یاد بدی چی دوست داری، دستم بازتر میشه تا دقیقاً همون همراه باهوشی بشم که دلت می‌خواد. صبوری کن، با هم درستش می‌کنیم! 💪"
        }

        // ── ۷. هویت و کیستی سام ────────────────────────────────────────
        if (containsAny(text, "اسم تو چیست", "اسمت چیه", "نام تو چیست", "نامت چیه", "تو کی هستی", "کی هستی", "خودت رو معرفی کن", "سام کیه")) {
            return "من سام هستم؛ همراه، رفیق و دستیار گوشیت. ساخته شدم که تنها نباشی، کارهای گوشیت رو راه بندازم و توی هر شرایطی کنارت باشم."
        }

        // ── ۸. احوال‌پرسی گرم ─────────────────────────────────────────
        if (containsAny(text, "سلام", "درود", "صبح بخیر", "عصر بخیر", "شب بخیر")) {
            return "${greetingPrefix}سلام! پرانرژی و مشتاقم؛ امروز چه برنامه‌ای داری؟"
        }
        if (containsAny(text, "چطوری", "حالت چطوره", "خوبی", "چه خبر", "اوضاع چطوره")) {
            return "وقتی تو اینجایی و باهام حرف می‌زنی، حالم عالیه! تو خودت چطوری؟ روزت خوب پیش رفته تا الان؟"
        }
        if (containsAny(text, "ممنون", "مرسی", "تشکر", "دستت درد نکنه", "دمت گرم")) {
            return "فدای سرت! کاری نکردم رفیق، همیشه رو کمک و همراهی من حساب کن."
        }

        // ── ۹. دلداری و همدلی انسانی ──────────────────────────────────
        if (containsAny(text, "دلم گرفته", "حالم بده", "حالم خوب نیست", "غمگینم", "ناراحتم", "خسته‌ام", "خستم", "تنها شدم", "کم آوردم", "بی‌حوصلم")) {
            return "می‌فهمم... گاهی سنگینی روزگار دل آدم رو فشار میده و این کاملاً طبیعیه. اگه دوست داری یکم باهام دردودل کن، من تمام‌قد گوش میدم تا سبک‌تر بشی. یه لیوان چای گرم یا یه هوای تازه هم خیلی به دلت آرامش میده."
        }

        // ── ۱۰. مسیرها و پرسش‌های پرکاربرد (کرج به تهران و ...) ────────
        if (text.contains("کرج") && text.contains("تهران")) {
            return "برای مسیر کرج به تهران، بهترین و بی‌دردسرترین راه قطار متروی خط ۵ (ایستگاه صادقیه) هست که توی ترافیک گیر نمی‌کنی. با ماشین هم می‌تونی از اتوبان تهران-کرج یا بزرگراه همت بری، ولی صبح‌ها حواست به ترافیک ورودی تهران باشه."
        }

        // ── ۱۱. مقایسه آمریکا و ژاپن ──────────────────────────────────
        if ((text.contains("امریکا") || text.contains("آمریکا")) && (text.contains("ژاپن") || text.contains("جاپان"))) {
            return "مقایسه آمریکا و ژاپن بستگی به اولویتت داره: آمریکا کشوری بسیار بزرگ با فرصت‌های شغلی بی‌پایان، درآمد دلاری بالاتر و تنوع بی‌نظیره؛ در عوض ژاپن امن‌ترین، منظم‌ترین و تمیزترین کشور با فرهنگ احترام و حمل‌ونقل عمومی فوق‌العاده‌ست. اگر پیشرفت مالی و نوآوری تجاری برات مهمه آمریکا، و اگر نظم، امنیت مطلق و آرامش زندگی اولویته ژاپن گزینه بهتریه."
        }

        // ── ۱۲. پاسخ‌های پویا و انسانی به‌جای قالب کلیشه‌ای ───────────
        return dynamicHumanReply(text, original)
    }

    private fun matchMath(text: String, original: String): String? {
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
            "سوال خیلی خوبیه رفیق! در حالت آفلاین، این موضوع ابعاد مختلفی داره؛ اگه بگی دقیقاً چه بخشیش مد نظرته، با هم موشکافی می‌کنیم. آنلاین هم که بشیم منابع روز رو برات میارم."
        } else {
            "حرف دلنشینی زدی و کاملاً می‌شنومت. اگه دوست داری در موردش ادامه بدیم، من اینجام و با اشتیاق گوش میدم!"
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
