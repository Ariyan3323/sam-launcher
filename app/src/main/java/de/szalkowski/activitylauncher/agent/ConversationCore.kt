package de.szalkowski.activitylauncher.agent

import java.util.Locale

/** A small, deterministic offline conversation layer. It is deliberately not presented as a full LLM. */
class ConversationCore(private val knowledge: OfflineKnowledgeStore? = null) {
    private val recentTopics = ArrayDeque<String>()

    fun reply(input: String): String {
        val original = input.trim()
        val text = normalize(original)
        if (text.isBlank()) return "من اینجا هستم؛ هر چیزی می‌خواهی بگو."
        rememberTopic(text)

        return when {
            containsAny(text, "سلام", "درود", "hello", "hi", "hey") ->
                "سلام، من سام هستم. راحت با من حرف بزن؛ می‌توانی سؤال بپرسی، درد دل کنی یا بگویی کاری روی گوشی انجام بدهم."
            containsAny(text, "حالم گرفته", "حالم بده", "حالم خوب نیست", "دلم گرفته", "غمگینم", "i feel sad", "feeling down") ->
                "متأسفم که حالت خوب نیست. اگر دوست داری، بگو چه چیزی بیشتر اذیتت می‌کند؛ من گوش می‌دهم و قضاوتت نمی‌کنم."
            containsAny(text, "استرس", "نگران", "اضطراب", "stressed", "worried", "anxious") ->
                "می‌فهمم که نگرانی خسته‌کننده است. یک نفس آرام بکش و بگو نگرانی‌ات بیشتر دربارهٔ چیست تا با هم آن را به یک قدم کوچک تبدیل کنیم."
            containsAny(text, "چه کار کنم", "چیکار کنم", "نمیدونم", "نمی دانم", "what should i do", "i don't know") ->
                "لازم نیست همه‌چیز را یک‌جا حل کنی. موضوع را در یک جمله بگو؛ من گزینه‌ها و قدم بعدی را با تو بررسی می‌کنم."
            containsAny(text, "با من حرف بزن", "چت کنیم", "گپ بزنیم", "talk to me", "let's chat") ->
                "حتماً. من کنارتم؛ دوست داری دربارهٔ حال امروزت، کارهایت، یادگیری یا یک موضوع آزاد صحبت کنیم؟"
            containsAny(text, "انبردست چیست", "انبردست چیه", "what is pliers") ->
                "انبردست یک ابزار دستی برای گرفتن، نگه‌داشتن، خم‌کردن و گاهی بریدن سیم و قطعات است. شکل فک آن تعیین می‌کند برای چه کاری مناسب‌تر باشد."
            containsAny(text, "تو کی هستی", "اسمت چیه", "who are you") ->
                "من سام هستم؛ یک دستیار فارسی‌محور که می‌تواند با تو گفتگو کند، برنامه‌ها را پیدا کند و با اجازه‌ات کارهای گوشی را انجام دهد."
            containsAny(text, "ممنون", "مرسی", "thank you", "thanks") ->
                "خواهش می‌کنم. هر وقت خواستی ادامه می‌دهیم."
            else -> genericReply(original)
        }
    }

    fun contextSummary(): String = recentTopics.joinToString(" | ")

    private fun genericReply(original: String): String {
        knowledge?.search(original)?.firstOrNull()?.let { return "از دانشی که قبلاً با اجازهٔ تو روی گوشی ذخیره شده: $it" }
        return "حرفت را شنیدم: «${original.take(160)}». در حالت آفلاین می‌توانم دربارهٔ موضوعات پایه و کارهای گوشی کمک کنم. اگر پاسخ تازه یا تخصصی لازم است، می‌توانم فقط با اجازهٔ تو در وب بررسی کنم."
    }

    private fun rememberTopic(value: String) {
        recentTopics.remove(value)
        recentTopics.addLast(value.take(80))
        while (recentTopics.size > 6) recentTopics.removeFirst()
    }

    private fun containsAny(value: String, vararg values: String): Boolean = values.any { value.contains(normalize(it)) }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("[\\u200c_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
