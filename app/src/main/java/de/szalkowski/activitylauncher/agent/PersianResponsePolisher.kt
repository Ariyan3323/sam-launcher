package de.szalkowski.activitylauncher.agent

/**
 * Keeps model output readable for Persian users without changing its meaning.
 * This is deliberately deterministic so it also works when no network is available.
 */
object PersianResponsePolisher {
    fun clean(raw: String): String {
        var text = raw
            .replace("ي", "ی")
            .replace("ى", "ی")
            .replace("ك", "ک")
            .replace("ة", "ه")
            .replace("ۀ", "هٔ")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("^\\s*#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*[-*•]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*\\d+[.)]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Common machine-translated/formal constructions, kept intentionally small.
        val naturalReplacements = linkedMapOf(
            "می‌باشد" to "هست",
            "می باشد" to "هست",
            "می‌گردد" to "می‌شه",
            "می گردد" to "می‌شه",
            "در صورت تمایل" to "اگه خواستی",
            "لذا" to "پس",
            "به‌منظور" to "برای",
            "به منظور" to "برای",
            "قادر به" to "می‌تونه",
            "امکان‌پذیر است" to "می‌شه",
            "امکان پذیر است" to "می‌شه",
            "اطمینان حاصل کنید" to "مطمئن شو",
            "لطفاً توجه داشته باشید" to "حواست باشه"
        )
        naturalReplacements.forEach { (formal, natural) -> text = text.replace(formal, natural) }

        return text
            .replace(Regex("\\s+([،؛؟!,.])"), "$1")
            .replace(Regex("([،؛؟!,.])(?=\\S)"), "$1 ")
            .trim()
    }
}
