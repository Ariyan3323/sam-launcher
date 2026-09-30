package de.szalkowski.activitylauncher.agent

import java.util.Locale

/**
 * Offline conversation layer for Sam.
 * Designed to feel human, warm and useful even without network.
 * This is intentionally deterministic and not presented as a full LLM.
 * Future sessions: expand knowledge categories here; do not replace with a fake LLM claim.
 */
class ConversationCore(private val knowledge: OfflineKnowledgeStore? = null) {
    private val recentTopics = ArrayDeque<String>()

    fun reply(input: String): String {
        val original = input.trim()
        val text = normalize(original)
        if (text.isBlank()) return "من اینجام؛ هر چی می‌خوای بگو."
        rememberTopic(text)

        // 1) Prefer previously learned answers from OfflineKnowledgeStore
        knowledge?.search(original, limit = 1)?.firstOrNull()?.let { remembered ->
            return polish("یادم هست اینو: $remembered")
        }

        // 2) Built-in rule + knowledge base
        return polish(matchBuiltIn(text, original))
    }

    fun contextSummary(): String = recentTopics.joinToString(" | ")

    private fun matchBuiltIn(text: String, original: String): String {
        // ── Greeting & identity ──────────────────────────────
        if (containsAny(text, "سلام", "درود", "hello", "hi", "hey", "صبح بخیر", "عصر بخیر", "شب بخیر")) {
            return "سلام، من سام‌ام. خوشحالم که اینجایی. راحت حرف بزن؛ می‌تونی سؤال بپرسی، درددل کنی یا بگی کاری روی گوشی انجام بدم."
        }
        if (containsAny(text, "تو کی هستی", "اسمت چیه", "who are you", "چی هستی")) {
            return "من سام هستم؛ یه همراه فارسی‌زبان که می‌تونه باهات حرف بزنه، برنامه‌ها رو پیدا کنه و با اجازه‌ات کارهای گوشی رو انجام بده. همیشه سعی می‌کنم مفید و صمیمی باشم."
        }
        if (containsAny(text, "ممنون", "مرسی", "thank you", "thanks", "دمت گرم")) {
            return "خواهش می‌کنم. هر وقت خواستی ادامه می‌دیم."
        }
        if (containsAny(text, "خداحافظ", "بای", "bye", "فعلا")) {
            return "فعلاً، مواظب خودت باش. هر وقت برگشتی من اینجام."
        }

        // ── Emotional support ────────────────────────────────
        if (containsAny(text, "حالم گرفته", "حالم بده", "حالم خوب نیست", "دلم گرفته", "غمگینم", "ناراحتم", "i feel sad", "feeling down")) {
            return "متأسفم که حالت خوب نیست. اگر دوست داری بگو چی بیشتر اذیتت می‌کنه؛ من گوش می‌دم و قضاوتت نمی‌کنم."
        }
        if (containsAny(text, "استرس", "نگران", "اضطراب", "stressed", "worried", "anxious")) {
            return "می‌فهمم نگرانی خسته‌کننده‌ست. یه نفس آروم بکش و بگو نگرانی‌ات بیشتر درباره‌ی چیه تا با هم یه قدم کوچیک برداریم."
        }
        if (containsAny(text, "تنها", "lonely", "حوصله ندارم")) {
            return "تنهایی گاهی سنگینه. من اینجام؛ می‌خوای درباره‌ی چیزی حرف بزنیم یا فقط یه کم گپ بزنیم؟"
        }

        // ── Help / direction ─────────────────────────────────
        if (containsAny(text, "چه کار کنم", "چیکار کنم", "نمیدونم", "نمی دانم", "what should i do", "i don't know")) {
            return "لازم نیست همه‌چیز رو یه‌جا حل کنی. موضوع رو تو یه جمله بگو؛ من گزینه‌ها و قدم بعدی رو باهات بررسی می‌کنم."
        }
        if (containsAny(text, "با من حرف بزن", "چت کنیم", "گپ بزنیم", "talk to me", "let's chat")) {
            return "حتماً. من کنارتم. دوست داری درباره‌ی حال امروزت، کارهات، یادگیری یا یه موضوع آزاد حرف بزنیم؟"
        }
        if (containsAny(text, "کمک", "help", "راهنمایی")) {
            return "در خدمتم. بگو چی لازم داری: توضیح چیزی، باز کردن برنامه، کنترل گوشی، یا فقط حرف زدن."
        }

        // ── Science & general knowledge ──────────────────────
        if (containsAny(text, "انیشتین", "اینشتین", "einstein")) {
            return "آلبرت اینشتین فیزیک‌دان نظری بود که با نظریه‌ی نسبیت و توضیح اثر فوتوالکتریک شناخته می‌شه و سال ۱۹۲۱ نوبل فیزیک گرفت."
        }
        if (containsAny(text, "نیوتن", "newton")) {
            return "آیزاک نیوتن قوانین حرکت و گرانش رو فرموله کرد و پایه‌های فیزیک کلاسیک رو گذاشت."
        }
        if (containsAny(text, "خورشید", "sun ", "خورشید چیه")) {
            return "خورشید یه ستاره‌ی متوسط از نوع کوتوله‌ی زرد هست که حدود ۱۵۰ میلیون کیلومتر از زمین فاصله داره و منبع اصلی انرژی و زندگی روی زمین به‌حساب میاد."
        }
        if (containsAny(text, "زمین", "earth", "سیاره زمین")) {
            return "زمین سومین سیاره از خورشید و تنها سیاره‌ای هست که تا الان می‌دونیم روش حیات وجود داره. حدود ۷۱ درصد سطحش آب و بقیه‌ش خشکیه."
        }
        if (containsAny(text, "آب", "water", "h2o")) {
            return "آب از دو اتم هیدروژن و یک اتم اکسیژن تشکیل شده (H₂O). برای حیات ضروریه و بیشتر سطح زمین رو پوشونده."
        }
        if (containsAny(text, "انبردست", "pliers")) {
            return "انبردست یه ابزار دستی برای گرفتن، نگه‌داشتن، خم‌کردن و گاهی بریدن سیم و قطعاته. شکل فکش مشخص می‌کنه برای چه کاری مناسب‌تره."
        }
        if (containsAny(text, "کامپیوتر", "رایانه", "computer")) {
            return "کامپیوتر دستگاهی هست که داده‌ها رو پردازش می‌کنه. اجزای اصلی‌ش معمولاً پردازنده، حافظه، ذخیره‌سازی و ورودی/خروجی هستن."
        }
        if (containsAny(text, "هوش مصنوعی", "ai ", "artificial intelligence")) {
            return "هوش مصنوعی یعنی ساخت سیستم‌هایی که بتونن کارهایی شبیه به تفکر انسانی انجام بدن؛ مثل یادگیری، تشخیص الگو و تصمیم‌گیری. من هم یه نمونه‌ی ساده‌ش هستم که روی گوشی‌ت کار می‌کنم."
        }
        if (containsAny(text, "اینترنت", "internet", "وب")) {
            return "اینترنت شبکه‌ی جهانی کامپیوترهاست که اطلاعات و سرویس‌ها رو به هم وصل می‌کنه. وقتی آنلاین باشی من می‌تونم دانشم رو ازش به‌روز کنم."
        }

        // ── Health & body (light, non-medical advice) ────────
        if (containsAny(text, "خواب", "sleep", "بی خوابی")) {
            return "خواب خوب برای مغز و بدن خیلی مهمه. سعی کن ساعت خواب منظمی داشته باشی و قبلش صفحه‌ی گوشی رو کم کنی. اگه مشکل جدی داری بهتره با پزشک مشورت کنی."
        }
        if (containsAny(text, "ورزش", "exercise", "تحرک")) {
            return "حتی پیاده‌روی روزانه ۲۰–۳۰ دقیقه هم کمک زیادی به حال جسمی و روحی می‌کنه. مهم اینه که منظم باشه، نه لزوماً سنگین."
        }
        if (containsAny(text, "غذا", "تغذیه", "diet", "غذا خوردن")) {
            return "تغذیه‌ی متعادل با میوه، سبزی، پروتئین و آب کافی پایه سلامتیه. افراط در هیچ غذایی معمولاً خوب نیست."
        }

        // ── Learning & productivity ──────────────────────────
        if (containsAny(text, "یادگیری", "درس", "مطالعه", "study", "learn")) {
            return "یادگیری وقتی مؤثرتره که کوتاه و منظم باشه. موضوع رو به تکه‌های کوچیک تقسیم کن و هر روز یه کم روش کار کن. اگه بگی چی می‌خوای یاد بگیری، می‌تونم کمکت کنم برنامه‌ریزی کنی."
        }
        if (containsAny(text, "تمرکز", "focus", "حواس پرت")) {
            return "برای تمرکز بهتر، محیط رو خلوت کن، اعلان‌ها رو ببند و کار رو به بازه‌های ۲۵ دقیقه‌ای تقسیم کن (تکنیک پومودورو)."
        }
        if (containsAny(text, "برنامه ریزی", "برنامه‌ریزی", "planning")) {
            return "اول مهم‌ترین کار رو مشخص کن، بعد بقیه‌ی کارها رو اولویت‌بندی کن. بهتره روز رو با ۱–۳ کار اصلی شروع کنی تا حس پیشرفت بگیری."
        }

        // ── Phone / assistant capabilities ───────────────────
        if (containsAny(text, "چی بلدی", "چه کاری می‌تونی", "قابلیت", "چه کارهایی می‌کنی")) {
            return "می‌تونم برنامه‌ها رو باز کنم، جستجو کنم، وضعیت باتری و دستگاه رو بگم، تنظیمات رو باز کنم، پیامک و اعلان رو با اجازه‌ات بخونم، و وقتی آنلاین باشم دانشم رو به‌روز کنم. همین‌طور می‌تونیم راحت حرف بزنیم."
        }
        if (containsAny(text, "آفلاین", "بدون اینترنت", "offline")) {
            return "آفلاین هم می‌تونم باهات حرف بزنم، کارهای محلی گوشی رو انجام بدم و از دانش ذخیره‌شده‌م استفاده کنم. وقتی نت وصل بشه، دانشم رو خودم به‌روز می‌کنم."
        }

        // ── Time ─────────────────────────────────────────────
        if (containsAny(text, "ساعت چند", "چه ساعتی", "time")) {
            return "برای ساعت دقیق بهتره از فرمان دستگاه استفاده کنی. بگو «ساعت» تا وضعیت فعلی رو برات بیارم."
        }

        // ── Fallback ─────────────────────────────────────────
        return genericReply(original)
    }

    private fun genericReply(original: String): String {
        knowledge?.search(original)?.firstOrNull()?.let { return "یادم هست: $it" }
        return "درباره‌ی «${original.take(70)}» بگو دنبال توضیح، مقایسه یا انجام یه کار هستی تا از همین‌جا شروع کنیم."
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
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("[\\u200c_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}