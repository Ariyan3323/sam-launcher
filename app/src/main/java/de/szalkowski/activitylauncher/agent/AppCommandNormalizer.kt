package de.szalkowski.activitylauncher.agent

/** One parser for launcher and chat; never splits an app name into characters. */
object AppCommandNormalizer {
    private val commandWords = Regex(
        "(?i)\\b(find my|find app|locate app|open|launch|start|run|please|the|app|application)\\b|" +
            "باز\\s*کن|اجرا\\s*کن|راه[‌ -]?اندازی\\s*کن|برو\\s+(?:به|توی|تو)|پیدا\\s*کن|یافتن\\s+برنامه",
        RegexOption.IGNORE_CASE
    )
    private val particles = Regex("(?i)^(?:برنامه|لطفاً|لطفا|یک)\\s+|\\s+(?:رو|را|لطفاً|لطفا|برام|برای من)$")

    fun normalize(value: String): String = value.lowercase()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('ۀ', 'ه').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('\u200c', ' ')
        .replace(Regex("[\\s_-]+"), " ")
        .trim()

    fun cleanAppTarget(input: String): String {
        var value = normalize(input).replace(commandWords, " ")
        repeat(2) { value = value.replace(particles, " ").trim() }
        return value.replace(Regex("\\s+"), " ").trim()
    }
}
