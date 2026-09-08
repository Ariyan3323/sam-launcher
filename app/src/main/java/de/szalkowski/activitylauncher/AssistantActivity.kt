package de.szalkowski.activitylauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.szalkowski.activitylauncher.agent.AgentConfig
import de.szalkowski.activitylauncher.agent.PersonalMemory
import de.szalkowski.activitylauncher.agent.PrivacyGuard
import de.szalkowski.activitylauncher.agent.SamAgent
import de.szalkowski.activitylauncher.agent.SemanticAppSearch
import de.szalkowski.activitylauncher.databinding.ActivityAssistantBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/** Local assistant with optional Gemini agent mode, voice transcription, and safe Android actions. */
class AssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    companion object {
        const val EXTRA_INITIAL_COMMAND = "de.szalkowski.activitylauncher.extra.INITIAL_COMMAND"
    }

    private lateinit var binding: ActivityAssistantBinding
    private var batteryLevel = -1
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var codeWriter: CodeWriter
    private lateinit var agent: SamAgent
    private lateinit var personalMemory: PersonalMemory
    private lateinit var privacyGuard: PrivacyGuard

    private val voiceInput =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val phrase = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (phrase != null) {
                binding.voiceStatus.setText(R.string.assistant_voice_processing)
                binding.bubble.setState(AssistantBubbleView.State.THINKING)
                runCommand(phrase)
            } else {
                binding.voiceStatus.setText(R.string.assistant_voice_no_result)
                respond(getString(R.string.assistant_voice_no_result))
            }
        }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput()
        else {
            binding.bubble.setState(AssistantBubbleView.State.ERROR)
            binding.voiceStatus.setText(R.string.assistant_voice_error)
            respond(getString(R.string.assistant_microphone_required))
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1
            binding.batteryStatus.text = getString(R.string.assistant_battery_status, batteryLevel)
            if (batteryLevel in 0..15) binding.response.text = getString(R.string.assistant_low_battery, batteryLevel)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        textToSpeech = TextToSpeech(this, this)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    binding.bubble.setState(AssistantBubbleView.State.SPEAKING)
                    binding.voiceStatus.setText(R.string.assistant_voice_speaking)
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    binding.bubble.setState(AssistantBubbleView.State.READY)
                    binding.voiceStatus.setText(R.string.assistant_voice_ready)
                }
            }

            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    binding.bubble.setState(AssistantBubbleView.State.ERROR)
                    binding.voiceStatus.setText(R.string.assistant_voice_error)
                }
            }
        })
        codeWriter = CodeWriter(this)
        personalMemory = PersonalMemory(this)
        privacyGuard = PrivacyGuard(this)
        agent = SamAgent(
            this,
            AgentConfig(
                name = "سام",
                geminiApiKey = BuildConfig.GEMINI_API_KEY,
                openAiApiKey = BuildConfig.OPENAI_API_KEY
            )
        )
        updateAgentStatus()
        binding.command.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submitCommand(); true } else false
        }
        binding.sendButton.setOnClickListener { submitCommand() }
        binding.voiceButton.setOnClickListener { requestVoiceInput() }
        binding.bubble.setOnClickListener { binding.command.requestFocus() }
        binding.appearanceButton.setOnClickListener {
            binding.bubble.nextAppearance()
            respond(getString(R.string.assistant_appearance_changed))
        }
        intent.getStringExtra(EXTRA_INITIAL_COMMAND)?.takeIf { it.isNotBlank() }?.let { command ->
            binding.root.post { runCommand(command) }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.forLanguageTag("fa-IR"))
            textToSpeech.setSpeechRate(0.95f)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech.language = Locale.getDefault()
            }
        } else {
            binding.voiceStatus.setText(R.string.assistant_voice_error)
        }
    }

    private fun submitCommand() {
        val command = binding.command.text?.toString()?.trim().orEmpty()
        if (command.isNotEmpty()) runCommand(command)
    }

    private fun requestVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceInput() {
        binding.bubble.setState(AssistantBubbleView.State.LISTENING)
        binding.voiceStatus.setText(R.string.assistant_voice_listening)
        val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_voice_prompt))
        }
        if (recognitionIntent.resolveActivity(packageManager) == null) {
            respond(getString(R.string.assistant_voice_unavailable))
            return
        }
        voiceInput.launch(recognitionIntent)
    }

    private fun runCommand(rawCommand: String) {
        binding.command.setText(rawCommand)
        if (BuildConfig.GEMINI_API_KEY.isConfigured() || BuildConfig.OPENAI_API_KEY.isConfigured()) {
            runAgentCommand(rawCommand)
        } else {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        }
    }

    private fun runAgentCommand(rawCommand: String) {
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        binding.agentStatus.setText(R.string.assistant_thinking)
        respond(getString(R.string.assistant_thinking))
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { agent.processCommand(rawCommand, deviceContext()) }
                .onSuccess { output ->
                    withContext(Dispatchers.Main) {
                        updateAgentStatus(output.success)
                        binding.bubble.setState(if (output.success) AssistantBubbleView.State.READY else AssistantBubbleView.State.THINKING)
                        if (!output.success) {
                            runLocalCommand(rawCommand)
                            return@withContext
                        }
                        val tools = output.executedTools.distinct()
                        val reply = if (tools.isEmpty()) output.reply
                        else "${output.reply}\n\n${getString(R.string.assistant_agent_tools, tools.joinToString(", "))}"
                        respond(reply)
                    }
                }
                .onFailure { error ->
                    withContext(Dispatchers.Main) {
                        updateAgentStatus(false)
                        binding.bubble.setState(AssistantBubbleView.State.ERROR)
                        respond(getString(R.string.assistant_agent_error) + "\n" + (error.message ?: "Unknown error"))
                    }
                }
        }
    }

    private fun updateAgentStatus(success: Boolean? = null) {
        val keyConfigured = BuildConfig.GEMINI_API_KEY.isConfigured() || BuildConfig.OPENAI_API_KEY.isConfigured()
        binding.agentStatus.text = when {
            success == false -> getString(R.string.assistant_agent_error)
            keyConfigured && success != false -> getString(R.string.assistant_agent_online)
            else -> getString(R.string.assistant_agent_offline)
        }
    }

    private fun String.isConfigured(): Boolean = isNotBlank() && this != "YOUR_GEMINI_API_KEY_HERE"

    private fun deviceContext(): String {
        val now = Calendar.getInstance()
        return "زمان: ${now.get(Calendar.HOUR_OF_DAY)}:${now.get(Calendar.MINUTE)}؛ باتری: $batteryLevel٪؛ زبان: fa-IR"
    }

    private fun runLocalCommand(rawCommand: String) {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        when {
            command.startsWith("open ") -> openApp(rawCommand.removePrefixIgnoreCase("open ").trim())
            command.startsWith("باز کن ") -> openApp(rawCommand.removePrefix("باز کن ").trim())
            command.contains("باز کن") -> {
                val after = rawCommand.substringAfter("باز کن").trim()
                val before = rawCommand.substringBefore("باز کن").trim().removeSuffix("را").trim()
                openApp(if (after.isNotBlank()) after else before)
            }
            command.startsWith("search ") -> searchWeb(rawCommand.removePrefixIgnoreCase("search ").trim())
            command.startsWith("جستجو ") -> searchWeb(rawCommand.removePrefix("جستجو ").trim())
            command.startsWith("search for ") -> searchWeb(rawCommand.removePrefixIgnoreCase("search for ").trim())
            command.contains("در وب جستجو") || command.contains("در اینترنت جستجو") -> searchWeb(
                rawCommand.substringAfter("جستجو").trim()
            )
            command.contains("battery") || command.contains("باتری") -> respond(getString(R.string.assistant_battery_status, batteryLevel))
            command.contains("device status") || command.contains("وضعیت") || command.contains("خلاصه") -> respond(localDeviceSummary())
            command.contains("time") || command.contains("ساعت") -> respond(currentTime())
            command.contains("dashboard") || command.contains("داشبورد") || command.contains("خلاصه امروز") -> respond(dailyDashboard())
            command.contains("work mode") || command.contains("حالت کار") -> activateSmartMode("کار")
            command.contains("driving mode") || command.contains("حالت رانندگی") -> activateSmartMode("رانندگی")
            command.contains("sleep mode") || command.contains("حالت خواب") -> activateSmartMode("خواب")
            command.contains("تماس بگیر") || command.contains("call ") -> prepareCall(rawCommand)
            command.contains("پیامک") || command.contains("sms") -> prepareSms(rawCommand)
            command.contains("ایمیل") || command.contains("email") -> prepareEmail(rawCommand)
            command.startsWith("remember ") -> rememberFact(rawCommand.removePrefixIgnoreCase("remember ").trim())
            command.startsWith("به خاطر بسپار ") -> rememberFact(rawCommand.removePrefix("به خاطر بسپار ").trim())
            command.contains("show memory") || command.contains("حافظه من") || command.contains("چه چیزهایی را به خاطر داری") -> showMemory()
            command.contains("clear memory") || command.contains("پاک کردن حافظه") || command.contains("حافظه را پاک کن") -> clearMemory()
            command.contains("privacy mode") || command.contains("حالت حریم خصوصی") -> setPrivacyMode(!command.contains("off") && !command.contains("خاموش"))
            command.contains("guest mode") || command.contains("حالت مهمان") -> setGuestMode(!command.contains("off") && !command.contains("خاموش"))
            command.contains("clock code") || command.contains("کد ساعت") -> exportClockCode()
            command.contains("clear cache") || (command.contains("پاک") && command.contains("کش")) -> openStorageSettings()
            command.contains("settings") || command.contains("تنظیمات") -> openSettings()
            command.contains("help") || command.contains("راهنما") -> respond(getString(R.string.assistant_help))
            else -> respond(localConversation(rawCommand))
        }
    }

    private fun localConversation(rawCommand: String): String {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        return when {
            command.matches(Regex("(سلام|درود|hello|hi|hey).*")) ->
                "سلام! من سام هستم. بدون اتصال Gemini هم می‌توانم برنامه‌ها را باز کنم، وضعیت گوشی را بگویم و حالت‌های دستگاه را مدیریت کنم."
            command.contains("اسمت چیه") || command.contains("کی هستی") || command.contains("who are you") ->
                "من سام، دستیار محلی Sam Launcher هستم؛ برای کارهای روزمره اول خود گوشی را بررسی می‌کنم."
            command.contains("خوبی") || command.contains("how are you") ->
                "آماده‌ام کمک کنم. یک فرمان کوتاه مثل «باتری»، «تنظیمات» یا «باز کن دوربین» بگو."
            command.contains("ممنون") || command.contains("مرسی") || command.contains("thank") ->
                "خواهش می‌کنم؛ هر وقت خواستی در خدمتم."
            command.contains("چه کارهایی") || command.contains("قابلیت") || command.contains("what can you do") ->
                "می‌توانم برنامه‌ها را باز کنم، برنامه مناسب را با مفهوم پیدا کنم، باتری و ساعت را بگویم، تنظیمات و حالت‌های کار/رانندگی/خواب را باز کنم و حافظه محلی داشته باشم."
            else ->
                "این فرمان را هنوز دقیق متوجه نشدم. می‌توانی بگویی «باز کن دوربین»، «باتری»، «تنظیمات» یا «در وب جستجو کن ...»؟"
        }
    }

    private fun openApp(query: String) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) { respond(getString(R.string.assistant_help)); return }
        if (!privacyGuard.canOpenApp(normalized)) {
            respond("حالت مهمان اجازه باز کردن این برنامه را نمی‌دهد.")
            return
        }
        val directMatch = packageManager.getInstalledApplications(PackageManager.MATCH_ALL).firstOrNull {
            it.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(normalized)
        }
        val match = directMatch ?: SemanticAppSearch.findMatches(this, normalized, 1).firstOrNull()
        val launchIntent = match?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
        if (launchIntent == null) respond(getString(R.string.assistant_app_not_found, query))
        else { startActivity(launchIntent); respond(getString(R.string.assistant_opening, match.loadLabel(packageManager))) }
    }

    private fun prepareCall(rawCommand: String) {
        val number = Regex("[+]?[-\\d() ]{7,}").find(rawCommand)?.value?.trim()
        if (number == null) {
            respond("شماره تماس را هم بگو؛ مثلاً «با 09121234567 تماس بگیر».")
            return
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            respond("شماره‌گیر برای $number باز شد؛ تماس نهایی با تأیید تو انجام می‌شود.")
        } else respond("شماره‌گیر روی این گوشی پیدا نشد.")
    }

    private fun prepareSms(rawCommand: String) {
        val number = Regex("[+]?[-\\d() ]{7,}").find(rawCommand)?.value?.trim()
        if (number == null) {
            val inbox = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            if (inbox.resolveActivity(packageManager) != null) startActivity(inbox)
            respond("صندوق پیامک باز شد؛ برای ارسال، شماره و متن را بگو.")
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", rawCommand.replace(number, "").replace("پیامک", "").trim())
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            respond("پیامک آماده شد؛ قبل از ارسال آن را بررسی کن.")
        } else respond("برنامه پیامک پیدا نشد.")
    }

    private fun prepareEmail(rawCommand: String) {
        val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(rawCommand)?.value
        if (email == null) {
            respond("آدرس ایمیل را هم بگو؛ مثلاً «ایمیل به test@example.com».")
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email)}"))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            respond("ایمیل برای $email آماده شد؛ ارسال نهایی با تأیید توست.")
        } else respond("برنامه ایمیل پیدا نشد.")
    }

    private fun searchWeb(query: String) {
        if (query.isBlank()) { respond(getString(R.string.assistant_help)); return }
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        if (searchIntent.resolveActivity(packageManager) == null) { respond(getString(R.string.assistant_browser_unavailable)); return }
        startActivity(searchIntent)
        respond(getString(R.string.assistant_searching, query))
    }

    private fun openStorageSettings() {
        cacheDir.deleteRecursively(); externalCacheDir?.deleteRecursively()
        val storageIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        if (storageIntent.resolveActivity(packageManager) != null) startActivity(storageIntent)
        respond(getString(R.string.assistant_cache_guidance))
    }

    private fun openSettings() {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        if (settingsIntent.resolveActivity(packageManager) != null) {
            startActivity(settingsIntent); respond(getString(R.string.assistant_opening_settings))
        } else respond(getString(R.string.assistant_browser_unavailable))
    }

    private fun localDeviceSummary(): String {
        val now = Calendar.getInstance()
        val time = String.format(Locale.getDefault(), "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return "وضعیت دستگاه: باتری ${batteryLevel}٪؛ ساعت $time؛ اندروید ${android.os.Build.VERSION.RELEASE}؛ حالت محلی فعال است."
    }

    private fun currentTime(): String {
        val now = Calendar.getInstance()
        binding.analogClock.setTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
        return getString(R.string.assistant_time, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND))
    }

    private fun activateSmartMode(mode: String) {
        getSharedPreferences("sam_preferences", MODE_PRIVATE).edit()
            .putString("active_mode", mode)
            .putLong("active_mode_at", System.currentTimeMillis())
            .apply()
        respond("حالت $mode فعال شد. سام تنظیمات پیشنهادی این حالت را در نظر می‌گیرد.")
    }

    private fun dailyDashboard(): String {
        val mode = getSharedPreferences("sam_preferences", MODE_PRIVATE)
            .getString("active_mode", "عادی") ?: "عادی"
        val now = Calendar.getInstance()
        val minute = String.format(Locale.getDefault(), "%02d", now.get(Calendar.MINUTE))
        return "داشبورد امروز: ساعت ${now.get(Calendar.HOUR_OF_DAY)}:$minute؛ باتری $batteryLevel٪؛ حالت فعال: $mode."
    }

    private fun rememberFact(fact: String) {
        if (!privacyGuard.canPersistPersonalMemory()) {
            respond("در حالت حریم خصوصی یا مهمان، حافظه شخصی غیرفعال است.")
        } else if (personalMemory.remember(fact)) respond("به خاطر سپردم؛ این اطلاعات فقط روی دستگاه ذخیره شد.")
        else respond("چیزی برای ذخیره‌کردن پیدا نکردم.")
    }

    private fun showMemory() {
        val facts = personalMemory.facts()
        respond(if (facts.isEmpty()) "حافظه شخصی خالی است." else "حافظه شخصی:\n${facts.mapIndexed { index, fact -> "${index + 1}. $fact" }.joinToString("\n")}")
    }

    private fun clearMemory() {
        personalMemory.clear()
        respond("حافظه شخصی کاملاً پاک شد.")
    }

    private fun setPrivacyMode(enabled: Boolean) {
        privacyGuard.setPrivacyMode(enabled)
        respond(if (enabled) "حالت حریم خصوصی فعال شد؛ حافظه شخصی متوقف است." else "حالت حریم خصوصی خاموش شد.")
    }

    private fun setGuestMode(enabled: Boolean) {
        privacyGuard.setGuestMode(enabled)
        respond(if (enabled) "حالت مهمان فعال شد؛ فقط برنامه‌های مجاز باز می‌شوند." else "حالت مهمان خاموش شد.")
    }

    private fun exportClockCode() {
        runCatching { codeWriter.writeClockCode() }
            .onSuccess { file -> respond(getString(R.string.assistant_clock_code_saved, file.absolutePath)) }
            .onFailure { respond(getString(R.string.assistant_clock_code_export_failed)) }
    }

    private fun respond(message: String) {
        binding.response.text = message
        if (::textToSpeech.isInitialized && message.isNotBlank()) {
            binding.voiceStatus.setText(R.string.assistant_voice_speaking)
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raad-response")
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String = if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
