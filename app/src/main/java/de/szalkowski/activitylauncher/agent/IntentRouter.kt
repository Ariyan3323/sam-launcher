package de.szalkowski.activitylauncher.agent

/** Structured intent produced before any Android action or network call. */
enum class SamIntentType {
    CHAT,
    OPEN_APP,
    SEARCH_WEB,
    READ_LATEST_NOTIFICATION,
    CHECK_DEVICE,
    COMPOSE_MESSAGE
}

data class SamIntent(
    val type: SamIntentType,
    val target: String = "",
    val requiresConfirmation: Boolean = false,
    val source: String = ""
)

/** Deterministic first-pass router; the LLM can handle only the remaining CHAT intent. */
object IntentRouter {
    fun route(input: String): SamIntent {
        val command = normalize(input)
        if (command.isBlank()) return SamIntent(SamIntentType.CHAT)

        val source = when {
            containsAny(command, "جیمیل", "gmail", "ایمیل", "email") -> "gmail"
            containsAny(command, "تلگرام", "telegram") -> "telegram"
            containsAny(command, "واتساپ", "whatsapp") -> "whatsapp"
            containsAny(command, "پیامنگار", "پیام نگار", "پیام رسان", "پیامرسان", "messenger") -> "messenger"
            else -> "any"
        }
        val asksForLatest = containsAny(
            command, "آخرین پیام", "آخرینپیام", "آخرین اعلان", "latest message",
            "latest notification", "last message", "read message", "بخوان", "بخون",
            "نمایش بده", "چیست", "چیه", "latest", "last"
        )
        if (asksForLatest && (source != "any" || containsAny(command, "پیام", "اعلان", "notification", "message"))) {
            return SamIntent(SamIntentType.READ_LATEST_NOTIFICATION, requiresConfirmation = true, source = source)
        }

        if (containsAny(command, "جستجو", "سرچ", "در وب", "در اینترنت", "search", "look up")) {
            return SamIntent(SamIntentType.SEARCH_WEB, target = extractAfterSearch(command))
        }
        if (containsAny(command, "پیام بده", "پیام بفرست", "ارسال کن", "send message", "compose email")) {
            return SamIntent(SamIntentType.COMPOSE_MESSAGE, target = input.trim(), requiresConfirmation = true)
        }
        if (containsAny(command, "باتری", "battery", "حافظه گوشی", "storage", "وضعیت دستگاه", "device status")) {
            return SamIntent(SamIntentType.CHECK_DEVICE)
        }
        if (containsAny(command, "باز کن", "اجرا کن", "راه اندازی کن", "راه‌اندازی کن", "open", "launch", "start")) {
            return SamIntent(SamIntentType.OPEN_APP, target = extractAppTarget(input))
        }
        return SamIntent(SamIntentType.CHAT)
    }

    private fun extractAfterSearch(command: String): String = command
        .replace(Regex("^(جستجو|سرچ|search|look up)\\s*(کن|for)?\\s*"), "")
        .replace(Regex("^در (وب|اینترنت)\\s*"), "")
        .trim()

    private fun extractAppTarget(input: String): String = AppCommandNormalizer.cleanAppTarget(input)

    private fun containsAny(value: String, vararg terms: String): Boolean = terms.any(value::contains)

    private fun normalize(value: String): String = value.lowercase()
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ۀ', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('\u200c', ' ')
        .replace(Regex("[\\s_-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun String.normalizeUserText(): String = lowercase()
    .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
    .replace('ۀ', 'ه').replace('ة', 'ه')
    .replace(Regex("[ًٌٍَُِّْـ]"), "")
    .replace('\u200c', ' ')
    .replace(Regex("[\\s_-]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
