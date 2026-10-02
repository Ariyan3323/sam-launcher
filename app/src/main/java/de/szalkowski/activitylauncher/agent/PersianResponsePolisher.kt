package de.szalkowski.activitylauncher.agent

/**
 * Keeps model output readable, natural, and fluent for Persian users.
 * Removes machine-translated nonsense, odd literal translations, and formatting artifacts.
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

        // Replace unnatural machine translations and literal translations with natural Persian phrases
        val naturalReplacements = linkedMapOf(
            "شلگیر زیادی بده" to "آرامش و تسکین زیادی به همراه داشته باشه",
            "شلگیر" to "آرامش و تسکین",
            "پیچکه خودت رو بیشتر ببین" to "به خودت فرصت بده و به احساساتت توجه کن",
            "پیچکه" to "به خودت فرصت بده و به",
            "نهی لحظه‌ای که درکش کنه" to "نه اینکه در یک لحظه همه‌چیز حل بشه",
            "کسی که درکاش گره کند" to "کسی که واقعاً تو رو درک کنه",
            "بپذیرت کمک لازمه" to "پذیرفتن کمک اولین قدم برای بهتر شدنه",
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
