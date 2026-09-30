package de.szalkowski.activitylauncher.agent

/** Logical body of SAM. Each organ may contain multiple interchangeable specialist models/tools. */
enum class SamOrgan { BRAIN, HEART, EYES, EARS, MEMORY, HANDS, LEGS }

data class SamSpecialist(
    val id: String,
    val organ: SamOrgan,
    val title: String,
    val capabilities: Set<String>,
    val enabled: Boolean = true,
)

/**
 * Model/tool registry used by the orchestrator. It is intentionally provider-neutral:
 * concrete providers can be attached later without changing the SAM personality.
 */
object SamSpecialistRegistry {
    val default: List<SamSpecialist> = listOf(
        SamSpecialist("reasoning", SamOrgan.BRAIN, "استدلال و تصمیم", setOf("reasoning", "planning", "decision")),
        SamSpecialist("research", SamOrgan.BRAIN, "تحقیق و جمع‌بندی", setOf("research", "current", "sources")),
        SamSpecialist("artist", SamOrgan.HEART, "هنرمند", setOf("art", "creative", "image")),
        SamSpecialist("psychology", SamOrgan.HEART, "روان‌شناسی", setOf("psychology", "empathy", "conversation")),
        SamSpecialist("vision", SamOrgan.EYES, "بینایی", setOf("vision", "image", "screen")),
        SamSpecialist("voice", SamOrgan.EARS, "شنوایی و گفتار", setOf("voice", "audio")),
        SamSpecialist("memory", SamOrgan.MEMORY, "حافظه", setOf("memory", "personalization")),
        SamSpecialist("coder", SamOrgan.HANDS, "کدنویس", setOf("code", "debug", "programming")),
        SamSpecialist("tools", SamOrgan.HANDS, "ابزار و API", setOf("tools", "files", "api", "automation")),
        SamSpecialist("browser", SamOrgan.LEGS, "حرکت در وب و برنامه‌ها", setOf("navigation", "browser", "apps")),
        SamSpecialist("device", SamOrgan.LEGS, "اجرای عملیات دستگاه", setOf("device", "actions")),
    )

    fun choose(query: String): List<SamSpecialist> {
        val q = query.lowercase()
        val requested = mutableSetOf<String>()
        if (listOf("کد", "برنامه", "کدنویس", "code", "program").any(q::contains)) requested += "code"
        if (listOf("عکس", "تصویر", "نقاشی", "هنر", "image", "photo", "art").any(q::contains)) requested += "art"
        if (listOf("روان", "احساس", "استرس", "روانشناس", "psychology").any(q::contains)) requested += "psychology"
        if (listOf("خبر", "امروز", "الان", "قیمت", "جدیدترین", "news", "today", "current", "price").any(q::contains)) requested += "current"
        if (listOf("جستجو", "سرچ", "وب", "اینترنت", "search", "web").any(q::contains)) requested += "research"
        if (listOf("باز کن", "اجرا", "کلیک", "open", "launch").any(q::contains)) requested += "navigation"
        if (listOf("فایل", "api", "خودکار", "اتوماسیون", "automation").any(q::contains)) requested += "tools"
        if (requested.isEmpty()) requested += "reasoning"
        val matches = default.filter { it.enabled && it.capabilities.any(requested::contains) }
        return (matches + default.first { it.id == "reasoning" }).distinctBy { it.id }
    }
}
