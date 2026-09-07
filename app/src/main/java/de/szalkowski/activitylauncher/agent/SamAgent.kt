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
import java.util.Calendar
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
    val success: Boolean,
    val workflowSteps: List<String> = emptyList()
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
        toolRegistry.register(SearchWebTool(context))
        toolRegistry.register(BatteryTool(context))
        toolRegistry.register(DeviceSummaryTool(context))
        toolRegistry.register(ListAppsTool(context))
        toolRegistry.register(FlashlightTool(context))
        toolRegistry.register(SettingsTool(context))
        toolRegistry.register(PhoneCallTool(context))
        toolRegistry.register(SmsInboxTool(context))
        toolRegistry.register(SmsComposeTool(context))
        toolRegistry.register(CallLogTool(context))
        toolRegistry.register(VoicemailTool(context))
        toolRegistry.register(EmailComposeTool(context))
        toolRegistry.register(ActivateModeTool(context))
        toolRegistry.register(DailyDashboardTool(context))
    }

    suspend fun processCommand(userInput: String, deviceContext: String): AgentOutput {
        val steps = splitWorkflow(userInput)
        return if (steps.size > 1) processWorkflow(steps, deviceContext) else processSingleCommand(userInput, deviceContext)
    }

    private suspend fun processWorkflow(steps: List<String>, deviceContext: String): AgentOutput {
        val startTime = System.currentTimeMillis()
        val executedTools = mutableListOf<String>()
        val stepReports = mutableListOf<String>()
        var allSuccessful = true

        for ((index, step) in steps.withIndex()) {
            val output = processSingleCommand(step, deviceContext)
            executedTools += output.executedTools
            val report = if (output.success) {
                "${index + 1}. $step → ${output.reply}"
            } else {
                "${index + 1}. $step → خطا: ${output.reply}"
            }
            stepReports += report
            if (!output.success) {
                allSuccessful = false
                break
            }
        }

        val summary = if (allSuccessful) "همهٔ ${stepReports.size} مرحله با موفقیت انجام شد."
        else "زنجیره در مرحلهٔ ${stepReports.size} متوقف شد."
        return AgentOutput(
            reply = "$summary\n${stepReports.joinToString("\n")}",
            executedTools = executedTools.distinct(),
            responseTimeMs = System.currentTimeMillis() - startTime,
            success = allSuccessful,
            workflowSteps = stepReports
        )
    }

    private fun splitWorkflow(input: String): List<String> = WorkflowParser.parse(input)

    private suspend fun processSingleCommand(userInput: String, deviceContext: String): AgentOutput {
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
                    if (!toolResult.success) {
                        return AgentOutput(
                            reply = toolResult.message,
                            executedTools = executedTools.distinct(),
                            responseTimeMs = System.currentTimeMillis() - startTime,
                            success = false
                        )
                    }
                    currentInput = "[نتیجه اجرای ابزار ${toolCall.name}]: ${toolResult.message}"
                } else {
                    return AgentOutput(
                        reply = "ابزار ${toolCall.name} پیدا نشد.",
                        executedTools = executedTools.distinct(),
                        responseTimeMs = System.currentTimeMillis() - startTime,
                        success = false
                    )
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

class SearchWebTool(private val context: Context) : Tool {
    override val name = "search_web"
    override val description = "جستجو در گوگل"
    override val parameters = listOf(ParamSchema("query", "string", "عبارت جستجو", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.getString("query")
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.google.com/search?q=${android.net.Uri.encode(query)}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent.resolveActivity(context.packageManager) == null) {
            return ToolResult(false, "مرورگری برای انجام این جست‌وجو در دسترس نیست.")
        }
        context.startActivity(intent)
        return ToolResult(true, "جستجو برای: $query")
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

class DeviceSummaryTool(private val context: Context) : Tool {
    override val name = "device_summary"
    override val description = "ارائهٔ خلاصهٔ وضعیت دستگاه شامل باتری، ساعت، نسخهٔ اندروید و تعداد برنامه‌ها"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val now = Calendar.getInstance()
        val time = String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val appCount = context.packageManager.getInstalledApplications(PackageManager.MATCH_ALL).size
        val summary = "باتری: $battery٪؛ ساعت: $time؛ اندروید: ${android.os.Build.VERSION.RELEASE}; برنامه‌های نصب‌شده: $appCount"
        return ToolResult(true, summary)
    }
}

class ListAppsTool(private val context: Context) : Tool {
    override val name = "list_apps"
    override val description = "نمایش چند برنامهٔ نصب‌شده برای کمک به پیدا کردن برنامهٔ موردنظر"
    override val parameters = listOf(ParamSchema("query", "string", "بخشی از نام برنامه؛ خالی برای چند برنامهٔ اخیر", false))

    override suspend fun execute(args: JSONObject): ToolResult {
        val query = args.optString("query").trim().lowercase(Locale.getDefault())
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.loadLabel(pm).toString() }
            .filter { query.isBlank() || it.lowercase(Locale.getDefault()).contains(query) }
            .distinct()
            .sorted()
            .take(12)
            .toList()
        return if (apps.isEmpty()) ToolResult(false, "برنامه‌ای مطابق جستجو پیدا نشد.")
        else ToolResult(true, "برنامه‌ها: ${apps.joinToString("، ")}")
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

/** Opens the dialer with a number; the user must press the call button. */
class PhoneCallTool(private val context: Context) : Tool {
    override val name = "call_phone"
    override val description = "باز کردن شماره‌گیر با شماره مشخص؛ تماس نهایی فقط با تأیید کاربر انجام می‌شود"
    override val parameters = listOf(ParamSchema("phone_number", "string", "شماره تلفن", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val number = args.optString("phone_number").trim()
        if (number.isBlank()) return ToolResult(false, "شماره تلفن وارد نشده است.")
        val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${android.net.Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "شماره‌گیر برای $number باز شد؛ برای تماس دکمه تماس را بزنید.")
    }
}

/** Opens the default messaging application's inbox without reading SMS content directly. */
class SmsInboxTool(private val context: Context) : Tool {
    override val name = "read_sms"
    override val description = "باز کردن صندوق پیامک برنامه پیش‌فرض برای خواندن پیام‌ها"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_MESSAGING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "برنامه پیامک پیش‌فرض باز شد؛ پیام‌ها را آنجا بخوانید.")
    }
}

/** Opens a prefilled SMS composer; the user must review and send it. */
class SmsComposeTool(private val context: Context) : Tool {
    override val name = "send_sms"
    override val description = "باز کردن پیامک آماده برای بازبینی و ارسال توسط کاربر"
    override val parameters = listOf(
        ParamSchema("phone_number", "string", "شماره گیرنده", true),
        ParamSchema("message", "string", "متن پیامک", true)
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val number = args.optString("phone_number").trim()
        val message = args.optString("message").trim()
        if (number.isBlank() || message.isBlank()) return ToolResult(false, "شماره گیرنده و متن پیامک لازم است.")
        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${android.net.Uri.encode(number)}"))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "پیامک برای $number آماده شد؛ قبل از ارسال آن را بررسی کنید.")
    }
}

/** Opens the call log, which acts as the phone assistant's recent-call view. */
class CallLogTool(private val context: Context) : Tool {
    override val name = "open_call_log"
    override val description = "باز کردن گزارش تماس‌ها و دسترسی به تماس‌های اخیر"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://call_log/calls"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "گزارش تماس‌های اخیر باز شد.")
    }
}

/** Opens the dialer with a common carrier voicemail access code when supported. */
class VoicemailTool(private val context: Context) : Tool {
    override val name = "open_voicemail"
    override val description = "باز کردن صندوق پیام صوتی اپراتور برای گوش دادن توسط کاربر"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:*86"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "شماره‌گیر صندوق صوتی باز شد؛ کد اپراتور را بررسی و در صورت نیاز تماس بگیرید.")
    }
}

/** Opens a prefilled email composer; the user must review and send it. */
class EmailComposeTool(private val context: Context) : Tool {
    override val name = "send_email"
    override val description = "باز کردن ایمیل آماده برای بازبینی و ارسال توسط کاربر"
    override val parameters = listOf(
        ParamSchema("to", "string", "آدرس ایمیل گیرنده", true),
        ParamSchema("subject", "string", "موضوع ایمیل", false),
        ParamSchema("body", "string", "متن ایمیل", true)
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val to = args.optString("to").trim()
        val subject = args.optString("subject").trim()
        val body = args.optString("body").trim()
        if (to.isBlank() || body.isBlank()) return ToolResult(false, "گیرنده و متن ایمیل لازم است.")
        val uri = android.net.Uri.Builder()
            .scheme("mailto")
            .path(to)
            .appendQueryParameter("subject", subject)
            .appendQueryParameter("body", body)
            .build()
        val intent = Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchExternal(context, intent, "ایمیل برای $to آماده شد؛ قبل از ارسال آن را بررسی کنید.")
    }
}

class ActivateModeTool(private val context: Context) : Tool {
    override val name = "activate_smart_mode"
    override val description = "فعال کردن حالت هوشمند کار، رانندگی یا خواب و ذخیره آن روی دستگاه"
    override val parameters = listOf(ParamSchema("mode", "string", "کار، رانندگی یا خواب", true))

    override suspend fun execute(args: JSONObject): ToolResult {
        val requested = args.optString("mode").trim().lowercase(Locale.ROOT)
        val mode = when {
            requested.contains("work") || requested.contains("کار") -> "کار"
            requested.contains("driv") || requested.contains("رانندگی") -> "رانندگی"
            requested.contains("sleep") || requested.contains("خواب") -> "خواب"
            else -> return ToolResult(false, "حالت معتبر نیست؛ یکی از کار، رانندگی یا خواب را انتخاب کنید.")
        }
        context.getSharedPreferences("sam_preferences", Context.MODE_PRIVATE).edit()
            .putString("active_mode", mode)
            .putLong("active_mode_at", System.currentTimeMillis())
            .apply()
        return ToolResult(true, "حالت $mode فعال شد.")
    }
}

class DailyDashboardTool(private val context: Context) : Tool {
    override val name = "daily_dashboard"
    override val description = "نمایش داشبورد کوتاه روزانه شامل ساعت، باتری و حالت فعال"
    override val parameters = emptyList<ParamSchema>()

    override suspend fun execute(args: JSONObject): ToolResult {
        val mode = context.getSharedPreferences("sam_preferences", Context.MODE_PRIVATE)
            .getString("active_mode", "عادی") ?: "عادی"
        val battery = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val now = Calendar.getInstance()
        val time = String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return ToolResult(true, "داشبورد امروز: ساعت $time؛ باتری $battery٪؛ حالت فعال: $mode.")
    }
}

private fun launchExternal(context: Context, intent: Intent, successMessage: String): ToolResult {
    if (intent.resolveActivity(context.packageManager) == null) {
        return ToolResult(false, "برنامه‌ای برای انجام این کار در دستگاه پیدا نشد.")
    }
    context.startActivity(intent)
    return ToolResult(true, successMessage)
}
