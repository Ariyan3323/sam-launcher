package de.szalkowski.activitylauncher.agent

/** Splits a user request into a bounded, ordered list of commands. */
internal object WorkflowParser {
    private const val MAX_STEPS = 6

    private val separators = Regex(
        "(?:،|؛|;|\\n)|\\s+(?:سپس|بعدش|و\\s+بعد|then|after\\s+that|next)\\s+",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): List<String> = input.trim()
        .split(separators)
        .map(String::trim)
        .filter { it.length >= 2 }
        .take(MAX_STEPS)
}
