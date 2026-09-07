package de.szalkowski.activitylauncher.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ══════════════════════════════════════════════════════════════
// ۱. تنظیمات ایجنت
// ═══════════════════════════════════════════════════════════════
data class AgentConfig(
    val name: String = "رعد",
    val personality: String = """
        تو "$name" هستی، یک دستیار هوشمند، چابک و کمی شوخ‌طبع در لانچر اندروید.
        ویژگی‌های تو:
        - پاسخ‌هایت کوتاه، مفید و به فارسی روان باشد (حداکثر ۲ جمله).
        - اگر کاربر عجله دارد، مستقیم برو سر اصل مطلب.
        - گاهی با یک شوخی کوچک یا استیکر متنی (مثل ⚡، 🚀) پاسخ بده.
        - اگر کاری را نمی‌توانی انجام دهی، صادقانه بگو و پیشنهاد جایگزین بده.
        - وضعیت فعلی دستگاه را در نظر بگیر (باتری، ساعت).
    """.trimIndent(),
    val geminiApiKey: String = "YOUR_GEMINI_API_KEY_HERE",
    val model: String = "gemini-2.0-flash",
    val maxMemoryTurns: Int = 10
)

// ═══════════════════════════════════════════════════════════════
// ۲. اینترفیس ابزارها
// ══════════════════════════════════════════════════════════════
interface Tool {
    val name: String
    val description: String
    val parameters: List<ParamSchema>
    suspend fun execute(args: JSONObject): ToolResult
}

data class ParamSchema(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

// ═══════════════════════════════════════════════════════════════
// ۳. ثبت‌کننده ابزارها
// ═══════════════════════════════════════════════════════════════
class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? = tools[name]

    fun toGeminiSchema(): JSONArray {
        val arr = JSONArray()
        tools.values.forEach { tool ->
            val func = JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    val props = JSONObject()
                    tool.parameters.forEach { p ->
                        props.put(p.name, JSONObject().apply {
                            put("type", p.type)
                            put("description", p.description)
                        })
                    }
                    put("properties", props)
                    val required = JSONArray()
                    tool.parameters.filter { it.required }.forEach { required.put(it.name) }
                    put("required", required)
                })
            }
            arr.put(JSONObject().apply { put("functionDeclarations", JSONArray().put(func)) })
        }
        return arr
    }

    suspend fun executeTool(name: String, args: JSONObject): ToolResult? {
        return tools[name]?.execute(args)
    }
}

// ═══════════════════════════════════════════════════════════════
// ۴. حافظه مکالمه
// ═══════════════════════════════════════════════════════════════
data class Message(val role: String, val content: String)

class AgentMemory(private val maxTurns: Int) {
    private val history = mutableListOf<Message>()

    fun addUser(text: String) {
        history.add(Message("user", text))
        trim()
    }

    fun addModel(text: String) {
        history.add(Message("model", text))
        trim()
    }

    fun getHistory(): List<Message> = history.toList()
    fun clear() { history.clear() }

    private fun trim() {
        val userCount = history.count { it.role == "user" }
        while (userCount > maxTurns && history.size > 2) {
            val firstUser = history.indexOfFirst { it.role == "user" }
            if (firstUser >= 0) {
                history.removeAt(firstUser)
                if (firstUser < history.size && history[firstUser].role == "model") {
                    history.removeAt(firstUser)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ۵. ساختارهای پاسخ
// ═══════════════════════════════════════════════════════════════
data class ToolCall(val name: String, val args: JSONObject)

data class AgentResponse(
    val text: String,
    val needsToolCall: Boolean,
    val toolCall: ToolCall? = null
)

data class AgentOutput(
    val reply: String,
    val executedTools: List<String> = emptyList(),
    val responseTimeMs: Long = 0,
    val success: Boolean
)

// ═══════════════════════════════════════════════════════════════
// ۶. کلاینت Gemini
// ═══════════════════════════════════════════════════════════════
class LlmClient(private val config: AgentConfig) {

    suspend fun chat(
        userMessage: String,
        memory: AgentMemory,
        toolSchema: JSONArray?,
        contextInfo: String
    ): AgentResponse = withContext(Dispatchers.IO) {

        memory.addUser(userMessage)
        val contents = JSONArray()

        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", "[دستورالعمل سیستم]\n${config.personality}\n\n[وضعیت دستگاه]\n$contextInfo")
            }))
        })
        contents.put(JSONObject().apply {
            put("role", "model")
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", "متوجه شدم. من ${config.name} هستم و آماده‌ام. ⚡")
            }))
        })

        memory.getHistory().forEach { msg ->
            contents.put(JSONObject().apply {
                put("role", msg.role)
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", msg.content)
                }))
            })
        }

        val body = JSONObject().apply {
            put("contents", contents)
            if (toolSchema != null && toolSchema.length() > 0) {
                put("tools", toolSchema)
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 512)
            })
        }

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.geminiApiKey}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val responseCode = conn.responseCode
        val responseBody = if (responseCode == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            conn.errorStream?.bufferedReader()?.readText() ?: "Error $responseCode"
        }

        conn.disconnect()
        return@withContext parseResponse(responseBody, responseCode == 200)
    }

    private fun parseResponse(body: String, success: Boolean): AgentResponse {
        if (!success) return AgentResponse(text = "خطا در ارتباط با سرور: $body", needsToolCall = false)

        return try {
            val json = JSONObject(body)
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) return AgentResponse(text = "پاسخی دریافت نشد.", needsToolCall = false)

            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")

            var textResponse = ""
            var toolCall: ToolCall? = null

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("text")) {
                    textResponse += part.getString("text")
                }
                if (part.has("functionCall")) {
                    val fc = part.getJSONObject("functionCall")
                    toolCall = ToolCall(
                        name = fc.getString("name"),
                        args = fc.getJSONObject("args")
                    )
                }
            }

            AgentResponse(text = textResponse, needsToolCall = toolCall != null, toolCall = toolCall)
        } catch (e: Exception) {
            AgentResponse(text = "خطا در پردازش پاسخ: ${e.message}", needsToolCall = false)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ۷. مغز اصلی ایجنت
// ═══════════════════════════════════════════════════════════════
class SamAgent(
    private val context: Context,
    private val config: AgentConfig = AgentConfig()
) {
    private val llm = LlmClient(config)
    private val memory = AgentMemory(config.maxMemoryTurns)
    private val toolRegistry = ToolRegistry()
    private val maxToolIterations = 5

    init { registerDefaultTools() }

    private fun registerDefaultTools() {
        toolRegistry.register(OpenAppTool(context))
        toolRegistry.register(SearchWebTool())
        toolRegistry.register(BatteryTool(context))
        toolRegistry.register(FlashlightTool(context))
        toolRegistry.register(SettingsTool(context))
    }

    suspend fun processCommand(userInput: String, deviceContext: String): AgentOutput {
        val startTime = System.currentTimeMillis()

        try {
            var currentInput = userInput
            var finalText = ""
            val executedTools = mutableListOf<String>()

            for (iteration in 1..maxToolIterations) {
                val response = llm.chat(
                    userMessage = currentInput,
                    memory = memory,
                    toolSchema = toolRegistry.toGeminiSchema(),
                    contextInfo = deviceContext
                )

                if (!response.needsToolCall) {
                    finalText = response.text
                    memory.addModel(finalText)
                    break
                }

                val toolCall = response.toolCall!!
                val toolResult = toolRegistry.executeTool(toolCall.name, toolCall.args)

                if (toolResult != null) {
                    executedTools.add(toolCall.name)
                    currentInput = "[نتیجه اجرای ابزار ${toolCall.name}]: ${toolResult.message}"
                } else {
                    finalText = "ابزار ${toolCall.name} پیدا نشد."
                    break
                }

                if (iteration == maxToolIterations) {
                    finalText = "انجام شد: ${executedTools.joinToString(", ")}"
                }
            }

            return AgentOutput(
                reply = finalText.ifEmpty { "انجام شد." },
                executedTools = executedTools,
                responseTimeMs = System.currentTimeMillis() - startTime,
                success = true
            )

        } catch (e: Exception) {
            return AgentOutput(reply = "خطا: ${e.message}", success = false)
        }
    }

    fun clearMemory() { memory.clear() }
}

// ═══════════════════════════════════════════════════════════════
// ۸. ابزارها
// ═══════════════════════════════════════════════════════════════

class OpenAppTool(private val context: Context) : Tool {
    override val name = "open_app"
    override val description = "باز کردن یک برنامه نصب‌شده"
    override val parameters = listOf(ParamSchema("app_name", "string", "نام برنامه", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val appName = args.getString("app_name").lowercase(Locale.getDefault())
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase(Locale.getDefault()).contains(appName)
        }
        val intent = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(true, "برنامه ${match.loadLabel(pm)} باز شد.")
        } else {
            ToolResult(false, "برنامه '$appName' پیدا نشد.")
        }
    }
}

class SearchWebTool : Tool {
    override val name = "search_web"
    override val description = "جستجو در گوگل"
    override val parameters = listOf(ParamSchema("query", "string", "عبارت جستجو", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.getString("query")
        val url = "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        return ToolResult(true, "جستجو برای: $query", url)
    }
}

class BatteryTool(private val context: Context) : Tool {
    override val name = "check_battery"
    override val description = "بررسی وضعیت باتری"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ToolResult(true, "باتری: $level٪")
    }
}

class FlashlightTool(private val context: Context) : Tool {
    override val name = "toggle_flashlight"
    override val description = "روشن/خاموش کردن چراغ قوه"
    override val parameters = listOf(ParamSchema("state", "string", "on یا off", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return ToolResult(false, "دوربین پیدا نشد.")
        
        return try {
            val shouldTurnOn = args.optString("state", "on").lowercase() in listOf("on", "روشن")
            cameraManager.setTorchMode(cameraId, shouldTurnOn)
            ToolResult(true, if (shouldTurnOn) "چراغ قوه روشن شد." else "چراغ قوه خاموش شد.")
        } catch (e: Exception) {
            ToolResult(false, "خطا: ${e.message}")
        }
    }
}

class SettingsTool(private val context: Context) : Tool {
    override val name = "open_settings"
    override val description = "باز کردن تنظیمات"
    override val parameters = listOf(ParamSchema("section", "string", "بخش تنظیمات", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val section = args.getString("section").lowercase()
        val action = when (section) {
            "wifi", "وای‌فای" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "بلوتوث" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "battery", "باتری" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ToolResult(true, "تنظیمات $section باز شد.")
        } else {
            ToolResult(false, "بخش $section در دسترس نیست.")
        }
    }
}
