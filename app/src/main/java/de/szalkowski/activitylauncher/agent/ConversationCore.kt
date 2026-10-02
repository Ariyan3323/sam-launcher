package de.szalkowski.activitylauncher.agent

import java.util.Locale

/**
 * Offline conversation layer for Sam.
 * Designed to feel warm, human, natural and thoughtful in Persian even without network.
 */
class ConversationCore(private val knowledge: OfflineKnowledgeStore? = null) {
    private val recentTopics = ArrayDeque<String>()

    fun reply(input: String): String {
        val original = input.trim()
        val text = normalize(original)
        if (text.isBlank()) return "من اینجام؛ هر چی می‌خوای بگو تا با هم صحبت کنیم."
        rememberTopic(text)

        // 1) Prefer previously learned answers from OfflineKnowledgeStore
        knowledge?.search(original, limit = 1)?.firstOrNull()?.let { remembered ->
            return polish("یادم هست: $remembered")
        }

        // 2) Built-in intelligent conversational rules
        return polish(matchBuiltIn(text, original))
    }

    fun contextSummary(): String = recentTopics.joinToString(" | ")

    private fun matchBuiltIn(text: String, original: String): String {
        // ── 1. هویت و نام سام ──────────────────────────────────────────
        if (containsAny(text, 
            "اسم تو چیست", "اسمت چیه", "نام تو چیست", "نامت چیه", 
            "تو کی هستی", "کی هستی", "تو کیستی", "کیستی", 
            "خودت رو معرفی کن", "خودتو معرفی کن", "معرفی کن", 
            "درباره خودت بگو", "سام کیه", "سام چیست", "who are you", "what is your name"
        )) {
            return "من سام هستم؛ همراه و دستیار صمیمی تو روی گوشی. چه آفلاین باشی و چه آنلاین، اینجام تا برای کارهای گوشی، همفکری و گپ زدن کمکت کنم."
        }

        // ── 2. احوال‌پرسی و سلام ───────────────────────────────────────
        if (containsAny(text, "سلام", "درود", "hello", "hi", "hey", "صبح بخیر", "عصر بخیر", "شب بخیر")) {
            return "سلام! خوشحالم صدات رو می‌شنوم. امروز چطوری و چه کمکی از دستم برمی‌آد؟"
        }
        if (containsAny(text, "چطوری", "حالت چطوره", "خوبی", "احوالت", "چه خبر", "how are you")) {
            return "ممنون، من خیلی خوب و پرانرژی‌ام! تو چطوری؟ خوشحال می‌شم با هم صحبت کنیم."
        }
        if (containsAny(text, "ممنون", "مرسی", "تشکر", "دستت درد نکنه", "دمت گرم", "thanks", "thank you")) {
            return "خواهش می‌کنم، انجام وظیفه بود! هر وقت کاری داشتی من همیشه همین‌جام."
        }
        if (containsAny(text, "خداحافظ", "بای", "فعلا", "bye", "خداحافظی")) {
            return "فعلاً مراقب خودت باش! هر وقت برگشتی من اینجام."
        }

        // ── 3. دلداری، حال روحی و احساسی ──────────────────────────────
        if (containsAny(text, "دلم گرفته", "حالم بده", "حالم خوب نیست", "غمگینم", "ناراحتم", "خسته‌ام", "خستم", "تنها شدم", "کم آوردم", "بی‌حوصلم", "i feel sad")) {
            return "می‌فهمم چی می‌گی؛ گاهی دل آدم از خستگی یا سنگینی فکری می‌گیره و این کاملاً طبیعیه. اگه دوست داری برام بگو تا با هم حرف بزنیم و سبک‌تر شی. گاهی یه پیاده‌روی کوتاه، یه موزیک آروم یا یه چای گرم هم حال و هوای آدم رو خیلی عوض می‌کنه."
        }
        if (containsAny(text, "استرس", "نگرانم", "اضطراب", "می‌ترسم", "stressed", "worried")) {
            return "نفس عمیق بکش؛ بیشتر نگرانی‌ها اون‌قدری که به نظر میان ترسناک نیستن. موضوع رو مرحله‌به‌مرحله با هم نگاه می‌کنیم تا حلش کنیم."
        }

        // ── 4. مقایسه کشورها و گزینه‌ها ───────────────────────────────
        if ((text.contains("امریکا") || text.contains("آمریکا")) && (text.contains("ژاپن") || text.contains("جاپان"))) {
            return "مقایسه آمریکا و ژاپن بستگی به اولویتت داره: آمریکا کشوری بسیار بزرگ با فرصت‌های شغلی بی‌پایان، درآمد دلاری بالاتر و تنوع بی‌نظیره؛ در عوض ژاپن امن‌ترین، منظم‌ترین و تمیزترین کشور با فرهنگ احترام و حمل‌ونقل عمومی فوق‌العاده‌ست. اگر پیشرفت مالی و نوآوری تجاری برات مهمه آمریکا، و اگر نظم، امنیت مطلق و آرامش زندگی اولویته ژاپن گزینه بهتریه."
        }
        if (text.contains("بهتره یا") || text.contains("کدوم بهتره") || text.contains("مقایسه")) {
            return "هر دو گزینه ویژگی‌ها و مزایای خاص خودشون رو دارن. برای یک تصمیم‌گیری درست، بگو بیشتر چه هدفی داری و چه معیاری برات اولویته تا با هم مقایسه‌شون کنیم."
        }

        // ── 5. قابلیت‌ها و کارهای گوشی ───────────────────────────────
        if (containsAny(text, "چی بلدی", "چه کاری می‌تونی", "قابلیت", "چه کارهایی می‌کنی", "توانایی")) {
            return "می‌تونم برنامه‌های گوشیت رو باز کنم، مخاطبین رو پیدا کنم، پیام بفرستم، وضعیت باتری و حافظه رو نشون بدم و در موضوعات مختلف باهات همفکری کنم. در حالت آنلاین هم به کل وب دسترسی دارم."
        }
        if (containsAny(text, "آفلاین", "بدون اینترنت", "offline")) {
            return "آفلاین هم کاملاً حواسم بهت هست؛ می‌تونیم حرف بزنیم، ابزارهای گوشی رو کنترل کنیم و از حافظه محلی استفاده کنیم."
        }
        if (containsAny(text, "ساعت چنده", "چه ساعتیه", "time")) {
            return "برای ساعت دقیق، کافیه بگی «ساعت» تا مستقیماً برات نمایش بدم."
        }

        // ── 6. دانش عمومی و تفکر ──────────────────────────────────────
        if (containsAny(text, "اینشتین", "انیشتین", "einstein")) {
            return "آلبرت اینشتین فیزیک‌دان بزرگی بود که با نظریه نسبیت مفهوم زمان و فضا رو دگرگون کرد و برنده جایزه نوبل فیزیک شد."
        }
        if (containsAny(text, "هوش مصنوعی", "ai", "artificial intelligence")) {
            return "هوش مصنوعی سامانه‌هایی هستن که توانایی یادگیری، حل مسئله و درک زبان رو دارن؛ من هم به عنوان سام تلاش می‌کنم بهترین همیار دیجیتال برات باشم."
        }

        // ── 7. بازخورد نهایی و هوشمندانه (حذف قالب رباتیک تکراری) ─────
        return genericReply(text, original)
    }

    private fun genericReply(text: String, original: String): String {
        knowledge?.search(original)?.firstOrNull()?.let { return "یادم هست: $it" }

        val isQuestion = original.endsWith("؟") || original.endsWith("?") || 
            containsAny(text, "چرا", "چگونه", "چطور", "کدام", "کدوم", "آیا", "چیست", "چیه", "کیه", "کجا")

        return if (isQuestion) {
            "پرسش جالبیه! از دیدگاه من در حالت آفلاین، این موضوع بستگی زیادی به شرایط و زاویه دیدت داره. اگه جزئیات بیشتری از خواسته‌ت بگی دقیق‌تر با هم همفکری می‌کنیم، و آنلاین که شدیم می‌تونم تمام داده‌های تازه‌ش رو هم از وب برات دربیارم."
        } else {
            "نکته قابل توجهی رو مطرح کردی. خوشحال می‌شم نظرت رو بیشتر بشنوم تا همفکری کنیم؛ هر زاویه‌ای از این موضوع مد نظرته بگو ادامه بدیم."
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
        .replace(Regex("[\\u200c_\\-]+"), " ")
        .replace(Regex("[؟!.,،؛]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
