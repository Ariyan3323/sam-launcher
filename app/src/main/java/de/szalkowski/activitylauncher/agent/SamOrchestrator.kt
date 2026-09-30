package de.szalkowski.activitylauncher.agent

import org.json.JSONObject

/**
 * Central policy/routing layer for SAM.
 *
 * It deliberately stays provider-agnostic: the UI and tools do not need to know
 * whether a request will be handled locally, by a cloud model, by Fugu, or by
 * web research. This is the foundation for the agent loop.
 */
enum class SamRoute { LOCAL, CLOUD, FUGU, WEB_RESEARCH, COUNCIL }

enum class ToolRisk { READ_ONLY, REVERSIBLE, SENSITIVE }

data class RouteDecision(
    val route: SamRoute,
    val reason: String,
    val requiresConfirmation: Boolean = false
)

class SamOrchestrator {

    fun route(
        text: String,
        hasLocal: Boolean,
        hasCloud: Boolean,
        hasFugu: Boolean,
        webAvailable: Boolean
    ): RouteDecision {
        val q = text.trim().lowercase()
        val current = listOf(
            "امروز", "الان", "آخرین", "جدیدترین", "قیمت", "خبر", "latest",
            "today", "now", "current", "price", "news"
        ).any(q::contains)
        val research = listOf(
            "تحقیق", "بررسی عمیق", "مقایسه", "منابع", "deep research",
            "research", "analyze deeply", "compare"
        ).any(q::contains)
        val sensitive = listOf(
            "ارسال کن", "بفرست", "حذف کن", "پاک کن", "تماس بگیر",
            "send", "delete", "call"
        ).any(q::contains)

        if (sensitive) {
            return RouteDecision(
                if (hasCloud) SamRoute.CLOUD else if (hasFugu) SamRoute.FUGU else SamRoute.LOCAL,
                "action request",
                requiresConfirmation = true
            )
        }
        if (research && hasFugu) return RouteDecision(SamRoute.FUGU, "multi-agent reasoning")
        if ((current || research) && webAvailable) return RouteDecision(SamRoute.WEB_RESEARCH, "fresh information")
        if (hasFugu && (research || q.length > 500)) return RouteDecision(SamRoute.FUGU, "complex task")
        if (hasCloud) return RouteDecision(SamRoute.CLOUD, "general cloud reasoning")
        if (hasLocal) return RouteDecision(SamRoute.LOCAL, "offline fallback")
        return RouteDecision(SamRoute.LOCAL, "safe default")
    }

    fun shouldConfirm(risk: ToolRisk): Boolean = risk == ToolRisk.SENSITIVE

    fun toolEvent(name: String, success: Boolean, data: Any? = null): JSONObject =
        JSONObject().apply {
            put("tool", name)
            put("success", success)
            if (data != null) put("data", data.toString())
        }
}
