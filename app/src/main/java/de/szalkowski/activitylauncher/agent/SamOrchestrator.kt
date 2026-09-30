package de.szalkowski.activitylauncher.agent

import org.json.JSONObject

/**
 * Central policy/routing layer for SAM.
 *
 * SAM is presented as one personality, while the body behind it can call
 * multiple specialist models/tools. This class keeps that routing provider-agnostic.
 */
enum class SamRoute { LOCAL, CLOUD, FUGU, WEB_RESEARCH, COUNCIL }

enum class ToolRisk { READ_ONLY, REVERSIBLE, SENSITIVE }

data class RouteDecision(
    val route: SamRoute,
    val reason: String,
    val requiresConfirmation: Boolean = false,
    val specialists: List<SamSpecialist> = emptyList()
)

class SamOrchestrator {
    fun route(text: String, hasLocal: Boolean, hasCloud: Boolean, hasFugu: Boolean, webAvailable: Boolean): RouteDecision {
        val q = text.trim().lowercase()
        val current = listOf("امروز", "الان", "آخرین", "جدیدترین", "قیمت", "خبر", "latest", "today", "now", "current", "price", "news").any(q::contains)
        val research = listOf("تحقیق", "بررسی عمیق", "مقایسه", "منابع", "deep research", "research", "analyze deeply", "compare").any(q::contains)
        val sensitive = listOf("ارسال کن", "بفرست", "حذف کن", "پاک کن", "تماس بگیر", "send", "delete", "call").any(q::contains)
        val specialists = SamSpecialistRegistry.choose(text)

        if (sensitive) return RouteDecision(
            if (hasCloud) SamRoute.CLOUD else if (hasFugu) SamRoute.FUGU else SamRoute.LOCAL,
            "action request", requiresConfirmation = true, specialists = specialists
        )
        if (research && hasFugu) return RouteDecision(SamRoute.FUGU, "multi-agent reasoning", specialists = specialists)
        if ((current || research) && webAvailable) return RouteDecision(SamRoute.WEB_RESEARCH, "fresh information", specialists = specialists)
        if (hasFugu && (research || q.length > 500)) return RouteDecision(SamRoute.FUGU, "complex task", specialists = specialists)
        if (hasCloud) return RouteDecision(SamRoute.CLOUD, "general cloud reasoning", specialists = specialists)
        if (hasLocal) return RouteDecision(SamRoute.LOCAL, "offline fallback", specialists = specialists)
        return RouteDecision(SamRoute.LOCAL, "safe default", specialists = specialists)
    }

    fun shouldConfirm(risk: ToolRisk): Boolean = risk == ToolRisk.SENSITIVE

    fun toolEvent(name: String, success: Boolean, data: Any? = null): JSONObject =
        JSONObject().apply {
            put("tool", name)
            put("success", success)
            if (data != null) put("data", data.toString())
        }
}
