package de.szalkowski.activitylauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import android.provider.Telephony
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private var speakResponses = true
    private val learningPreferences by lazy { getSharedPreferences("sam_learning", MODE_PRIVATE) }

    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readLatestSms()
        else respond("برای خواندن آخرین پیامک، اجازهٔ خواندن پیامک لازم است.")
    }

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
        binding.otherAiButton.setOnClickListener {
            shareWithOtherAi(binding.command.text?.toString().orEmpty().trim())
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
            val result = textToSpeech.setLanguage(Locale("fa", "IR"))
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
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.assistant_voice_prompt))
        }
        if (recognitionIntent.resolveActivity(packageManager) == null) {
            respond(getString(R.string.assistant_voice_unavailable))
            return
        }
        runCatching { voiceInput.launch(recognitionIntent) }
            .onFailure {
                binding.bubble.setState(AssistantBubbleView.State.ERROR)
                binding.voiceStatus.setText(R.string.assistant_voice_unavailable)
                respond(getString(R.string.assistant_voice_unavailable))
            }
    }

    private fun runCommand(rawCommand: String) {
        binding.command.setText(rawCommand)
        learnInteraction(rawCommand)
        if (isDeterministicPhoneCommand(rawCommand)) {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        } else if (BuildConfig.GEMINI_API_KEY.isConfigured() || BuildConfig.OPENAI_API_KEY.isConfigured()) {
            runAgentCommand(rawCommand)
        } else {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        }
    }

    private fun isDeterministicPhoneCommand(rawCommand: String): Boolean {
        val command = rawCommand.lowercase(Locale.ROOT)
        return listOf(
            "باتری", "battery", "ساعت", "time", "تنظیمات", "settings", "وضعیت", "خلاصه",
            "باز کن", "open ", "تماس بگیر", "call ", "پیامک", "sms", "ایمیل", "email",
            "جستجو", "search", "در وب", "در اینترنت",
            "پیدا کن", "یافتن برنامه", "برنامه مناسب", "find app", "find my", "locate app",
            "آخرین پیام", "آخرین اس ام اس", "last message", "latest sms", "علایق من", "چی دوست دارم",
            "هوش مصنوعی گوشی", "دستیار گوشی", "ask another ai", "other ai",
            "هوش‌های دیگر", "هوش های دیگر", "از هوش‌های دیگر", "از هوش های دیگر", "share ai",
            "سلام", "درود", "hello", "hi", "پیامنگار", "پیام‌رسان", "اس ام اس", "پیام بده",
            "پیام بفرست", "تلگرام", "برو تل", "زنگ بزن",
            "چه خبر", "اخبار جهان", "خبرهای امروز", "اخبار مهم", "world news", "latest news",
            "سیاست", "ترید", "رمز ارز", "ارز دیجیتال", "درس", "اخبار روز",
            "حالت کار", "work mode", "حالت رانندگی", "driving mode", "حالت خواب", "sleep mode",
            "حافظه", "remember ", "به خاطر بسپار", "حریم خصوصی", "privacy mode", "حالت مهمان", "guest mode"
        ).any(command::contains)
    }

    private fun runAgentCommand(rawCommand: String) {
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        binding.agentStatus.setText(R.string.assistant_thinking)
        respond(getString(R.string.assistant_thinking), speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { agent.processCommand(rawCommand, deviceContext()) }
                .onSuccess { output ->
                    withContext(Dispatchers.Main) {
                        updateAgentStatus(output.success)
                        binding.bubble.setState(if (output.success) AssistantBubbleView.State.READY else AssistantBubbleView.State.ERROR)
                        if (!output.success) {
                            runLocalCommand(rawCommand)
                            binding.bubble.setState(AssistantBubbleView.State.READY)
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
                        runLocalCommand(rawCommand)
                        binding.bubble.setState(AssistantBubbleView.State.READY)
                    }
                }
        }
    }

    private fun updateAgentStatus(success: Boolean? = null) {
        binding.agentStatus.text = when {
            success == false -> getString(R.string.assistant_agent_error)
            success == true -> getString(R.string.assistant_agent_online)
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
            command.contains("هوش‌های دیگر") || command.contains("هوش های دیگر") ||
                command.contains("از هوش‌های دیگر") || command.contains("از هوش های دیگر") ||
                command.contains("share ai") -> shareWithOtherAi(extractSharedQuestion(rawCommand))
            command.contains("هوش مصنوعی گوشی") || command.contains("دستیار گوشی") ||
                command.contains("ask another ai") || command.contains("other ai") -> openSystemAssistant()
            command.contains("اخبار جهان") || command.contains("خبرهای امروز") || command.contains("اخبار مهم") ||
                command.contains("اخبار روز") || command.contains("world news") || command.contains("latest news") -> showWorldNews()
            command == "چه خبر" || command.contains("چه خبر از جهان") -> showWorldNews()
            command.contains("تلگرام") || command.contains("برو تل") || command.contains("telegram") -> openApp("telegram")
            command.contains("پیامنگار") || command.contains("پیام‌رسان") -> openApp("پیام")
            command.contains("اس ام اس") || command.contains("پیام بده") || command.contains("پیام بفرست") -> prepareSms(rawCommand)
            command.contains("زنگ بزن") -> prepareCall(rawCommand)
            command.contains("تنظیمات") || command.contains("settings") -> openSettings()
            command.contains("بررسی باتری") || command.contains("وضعیت باتری") ||
                command.contains("باتری") || command.contains("battery") -> respond(getString(R.string.assistant_battery_status, batteryLevel))
            command.contains("حافظه گوشی") || command.contains("حافظه دستگاه") ||
                command.contains("storage") || command.contains("memory status") -> respond(storageSummary())
            command.contains("اختلال") || command.contains("عیب یابی") || command.contains("عیب‌یابی") ||
                command.contains("عیبیابی") || command.contains("diagnostic") || command.contains("check sam") -> runDiagnostics()
            command.startsWith("open ") -> openApp(rawCommand.removePrefixIgnoreCase("open ").trim())
            command.startsWith("باز کن ") -> openApp(rawCommand.removePrefix("باز کن ").trim())
            command.contains("پیدا کن") || command.contains("یافتن برنامه") ||
                command.contains("find app") || command.contains("find my") || command.contains("locate app") ->
                openApp(extractAppQuery(rawCommand))
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
            command.contains("device status") || command.contains("وضعیت") || command.contains("خلاصه") -> respond(localDeviceSummary())
            command.contains("time") || command.contains("ساعت") -> respond(currentTime())
            command.contains("dashboard") || command.contains("داشبورد") || command.contains("خلاصه امروز") -> respond(dailyDashboard())
            command.contains("work mode") || command.contains("حالت کار") -> activateSmartMode("کار")
            command.contains("driving mode") || command.contains("حالت رانندگی") -> activateSmartMode("رانندگی")
            command.contains("sleep mode") || command.contains("حالت خواب") -> activateSmartMode("خواب")
            command.contains("آخرین پیام") || command.contains("آخرین اس ام اس") ||
                command.contains("last message") || command.contains("latest sms") -> requestLatestSms()
            command.contains("تماس بگیر") || command.contains("call ") -> prepareCall(rawCommand)
            command.contains("پیامک") || command.contains("sms") -> prepareSms(rawCommand)
            command.contains("ایمیل") || command.contains("email") -> prepareEmail(rawCommand)
            command.startsWith("remember ") -> rememberFact(rawCommand.removePrefixIgnoreCase("remember ").trim())
            command.startsWith("به خاطر بسپار ") -> rememberFact(rawCommand.removePrefix("به خاطر بسپار ").trim())
            command.contains("show memory") || command.contains("حافظه من") || command.contains("چه چیزهایی را به خاطر داری") -> showMemory()
            command.contains("علایق من") || command.contains("علاقه‌های من") || command.contains("چی دوست دارم") ||
                command.contains("what do i like") -> respond(interestSummary())
            command.contains("clear memory") || command.contains("پاک کردن حافظه") || command.contains("حافظه را پاک کن") -> clearMemory()
            command.contains("privacy mode") || command.contains("حالت حریم خصوصی") -> setPrivacyMode(!command.contains("off") && !command.contains("خاموش"))
            command.contains("guest mode") || command.contains("حالت مهمان") -> setGuestMode(!command.contains("off") && !command.contains("خاموش"))
            command.contains("clock code") || command.contains("کد ساعت") -> exportClockCode()
            command.contains("clear cache") || (command.contains("پاک") && command.contains("کش")) -> openStorageSettings()
            command.contains("help") || command.contains("راهنما") -> respond(getString(R.string.assistant_help))
            else -> {
                val localReply = localConversation(rawCommand)
                if (localReply != null) respond(localReply) else answerWithWebFallback(rawCommand)
            }
        }
    }

    private fun answerWithWebFallback(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            respond(getString(R.string.assistant_help))
            return
        }
        recordLearning("web", cleanQuestion.take(80))
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val hasInternet = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivity?.activeNetwork
            val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } else {
            @Suppress("DEPRECATION")
            connectivity?.activeNetworkInfo?.isConnectedOrConnecting == true
        }
        if (!hasInternet) {
            respond(getString(R.string.assistant_internet_required))
            return
        }
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        respond("سام در حال پیدا کردن پاسخ است…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val answer = runCatching {
                val endpoint = "https://api.duckduckgo.com/?q=${Uri.encode(cleanQuestion)}&format=json&no_html=1&skip_disambig=1"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                    requestMethod = "GET"
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText").trim()
                if (abstractText.isNotBlank()) abstractText
                else json.optJSONArray("RelatedTopics")?.let { topics ->
                    (0 until topics.length()).asSequence()
                        .mapNotNull { topics.optJSONObject(it)?.optString("Text")?.trim() }
                        .firstOrNull { it.isNotBlank() }
                }.orEmpty()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            withContext(Dispatchers.Main) {
                binding.bubble.setState(AssistantBubbleView.State.READY)
                if (answer != null) respond(answer)
                else respond(getString(R.string.assistant_web_answer_unavailable))
            }
        }
    }

    private fun showWorldNews() {
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val newsNetworkAvailable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            connectivity?.activeNetwork != null
        } else {
            @Suppress("DEPRECATION")
            connectivity?.activeNetworkInfo?.isConnectedOrConnecting == true
        }
        if (!newsNetworkAvailable) {
            respond(getString(R.string.assistant_internet_required))
            return
        }
        respond("سام در حال جمع‌کردن خبرهای مهم جهان است…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val headlines = runCatching {
                val connection = (URL("https://news.google.com/rss?hl=fa&gl=IR&ceid=IR:fa").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5_000
                    readTimeout = 7_000
                }
                val xml = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                Regex("<item>[\\s\\S]*?<title>(.*?)</title>").findAll(xml)
                    .map { it.groupValues[1].replace("&amp;", "&").replace("&quot;", "\"").trim() }
                    .filter { it.isNotBlank() }
                    .take(5)
                    .toList()
            }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) {
                if (headlines.isEmpty()) respond(getString(R.string.assistant_web_answer_unavailable))
                else respond("مهم‌ترین خبرهای تازه:\n" + headlines.mapIndexed { i, title -> "${i + 1}. $title" }.joinToString("\n"))
            }
        }
    }

    private fun localConversation(rawCommand: String): String? {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        return when {
            command.matches(Regex("(سلام|درود|hello|hi|hey).*")) ->
                "سلام! خوش آمدی. من سام هستم؛ هر کاری خواستی بگو—مثلاً برنامه‌ای باز کنم، تماس بگیرم، پیام بفرستم، تلگرام را باز کنم، باتری و حافظه را بررسی کنم یا فقط با هم صحبت کنیم."
            command.contains("اسمت چیه") || command.contains("کی هستی") || command.contains("who are you") ->
                "من سام، دستیار محلی Sam Launcher هستم؛ برای کارهای روزمره اول خود گوشی را بررسی می‌کنم."
            command.contains("خوبی") || command.contains("how are you") ->
                "آماده‌ام کمک کنم. یک فرمان کوتاه مثل «باتری»، «تنظیمات» یا «باز کن دوربین» بگو."
            command.contains("چه خبر") || command.contains("خبرها") ->
                "من خبرهای لحظه‌ای را فقط با اینترنت می‌توانم بررسی کنم. بگو «چه خبر از ...» تا خودم در وب جست‌وجو کنم."
            command.contains("حوصله ندارم") || command.contains("خسته ام") || command.contains("خسته‌ام") ->
                "می‌فهمم. اگر دوست داری با هم یک کار ساده انجام بدهیم؛ مثلاً موسیقی را باز کنم، وضعیت گوشی را بگویم یا فقط با هم صحبت کنیم."
            command.contains("کمک میخوام") || command.contains("کمک می‌خوام") || command == "کمک" ->
                "حتماً. من می‌توانم برنامه‌ها را باز کنم، باتری و حافظه را بررسی کنم، کش سام را پاک کنم، تنظیمات را باز کنم یا دربارهٔ موضوعات مختلف در وب جست‌وجو کنم."
            command.contains("دوستت دارم") || command.contains("عاشقتم") ->
                "لطف داری. من هم اینجا هستم تا کارهایت را ساده‌تر کنم."
            command.contains("صبح بخیر") || command.contains("شب بخیر") ->
                "ممنون؛ امیدوارم روز یا شبت آرام و خوب پیش برود."
            command.contains("ممنون") || command.contains("مرسی") || command.contains("thank") ->
                "خواهش می‌کنم؛ هر وقت خواستی در خدمتم."
            command.contains("چه کارهایی") || command.contains("قابلیت") || command.contains("what can you do") ->
                "می‌توانم برنامه‌ها را باز کنم، برنامه مناسب را با مفهوم پیدا کنم، باتری و ساعت را بگویم، تنظیمات و حالت‌های کار/رانندگی/خواب را باز کنم و حافظه محلی داشته باشم."
            else -> null
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
        if (launchIntent == null) {
            respond(getString(R.string.assistant_app_not_found, query))
        } else {
            runCatching { startActivity(launchIntent) }
                .onSuccess {
                    recordLearning("app", match.loadLabel(packageManager).toString())
                    respond(getString(R.string.assistant_opening, match.loadLabel(packageManager)))
                }
                .onFailure { respond("باز کردن ${match.loadLabel(packageManager)} ممکن نشد؛ برنامه را از فهرست لانچر امتحان کن.") }
        }
    }

    private fun extractAppQuery(rawCommand: String): String {
        return rawCommand
            .replace(Regex("(?i)find my|find app|locate app|find|open"), " ")
            .replace("پیدا کن", " ")
            .replace("یافتن برنامه", " ")
            .replace("برنامه", " ")
            .replace(Regex("(?i)را|رو|مناسب|app"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun requestLatestSms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            readLatestSms()
        } else {
            smsPermission.launch(Manifest.permission.READ_SMS)
        }
    }

    private fun readLatestSms() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    address to body
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result == null) respond("پیامکی در صندوق ورودی پیدا نشد.")
                else respond("آخرین پیام از شمارهٔ ${result.first} است: ${result.second.take(240)}")
            }
        }
    }

    private fun learnInteraction(rawCommand: String) {
        if (!privacyGuard.canPersistPersonalMemory()) return
        val query = rawCommand.trim().replace(Regex("\\s+"), " ").take(80)
        if (query.isBlank()) return
        incrementLearning("conversation", query)
    }

    private fun recordLearning(category: String, value: String) {
        if (privacyGuard.canPersistPersonalMemory() && value.isNotBlank()) incrementLearning(category, value)
    }

    private fun incrementLearning(category: String, value: String) {
        val key = "${category}_${value.trim().lowercase(Locale.ROOT)}"
        val count = learningPreferences.getInt(key, 0) + 1
        learningPreferences.edit().putInt(key, count).apply()
    }

    private fun interestSummary(): String {
        if (!privacyGuard.canPersistPersonalMemory()) return "حالت حریم خصوصی فعال است؛ سام چیزی از علایق شما ذخیره نمی‌کند."
        val entries = learningPreferences.all
            .filterKeys { it.startsWith("app_") || it.startsWith("web_") }
            .map { (key, value) -> key.substringAfter('_') to (value as? Int ?: 0) }
            .sortedByDescending { it.second }
            .take(5)
        return if (entries.isEmpty()) "هنوز دادهٔ کافی ندارم؛ برنامه‌هایی که باز می‌کنی و موضوعاتی که جست‌وجو می‌کنی را فقط روی همین گوشی یاد می‌گیرم."
        else "بر اساس استفادهٔ محلی شما، موضوعات/برنامه‌های پرتکرار: ${entries.joinToString("، ") { "${it.first} (${it.second} بار)" }}."
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

    private fun openSystemAssistant() {
        val assistantIntent = Intent(Intent.ACTION_ASSIST)
        if (assistantIntent.resolveActivity(packageManager) != null) {
            startActivity(assistantIntent)
            respond("دستیارهای موجود گوشی را باز کردم؛ می‌توانی یکی را انتخاب کنی. سام بدون دسترسی رسمی نمی‌تواند پاسخ داخلی برنامه‌های دیگر را مخفیانه جمع کند.")
        } else {
            respond("دستیار دیگری روی گوشی ثبت نشده است؛ سام و جست‌وجوی وب همچنان فعال هستند.")
        }
    }

    private fun extractSharedQuestion(rawCommand: String): String {
        return rawCommand
            .replace("از هوش‌های دیگر", "")
            .replace("از هوش های دیگر", "")
            .replace("هوش‌های دیگر", "")
            .replace("هوش های دیگر", "")
            .replace(Regex("(?i)share ai|ask other ai"), "")
            .replace(Regex("(?i)این سوال را|این سؤال را|این سوال|این سؤال"), "")
            .trim()
            .ifBlank { binding.command.text?.toString().orEmpty().trim() }
    }

    private fun shareWithOtherAi(question: String) {
        if (question.isBlank()) {
            respond("سؤالت را بگو تا آن را برای هوش‌های مصنوعی دیگر بفرستم.")
            return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, question)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.assistant_choose_ai)))
        respond("سؤال آماده شد؛ یکی از هوش‌های مصنوعی نصب‌شده را انتخاب کن. سام پاسخ آن برنامه را بدون API رسمی نمی‌خواند.")
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
        recordLearning("web", query.take(80))
        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        if (searchIntent.resolveActivity(packageManager) == null) { respond(getString(R.string.assistant_browser_unavailable)); return }
        startActivity(searchIntent)
        respond(getString(R.string.assistant_searching, query))
    }

    private fun openStorageSettings() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheCleared = runCatching {
                cacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                val storageIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                when {
                    storageIntent.resolveActivity(packageManager) != null -> startActivity(storageIntent)
                    fallbackIntent.resolveActivity(packageManager) != null -> startActivity(fallbackIntent)
                }
                if (cacheCleared) {
                    respond(getString(R.string.assistant_cache_guidance))
                } else {
                    respond("پاک‌سازی کش سام کامل نشد؛ تنظیمات حافظه باز شد تا آن را بررسی کنید.")
                }
            }
        }
    }

    private fun storageSummary(): String {
        val stat = StatFs(filesDir.absolutePath)
        val total = stat.totalBytes / (1024 * 1024 * 1024)
        val free = stat.availableBytes / (1024 * 1024 * 1024)
        val used = (total - free).coerceAtLeast(0)
        return "وضعیت حافظه گوشی: ${used} گیگابایت استفاده‌شده از ${total} گیگابایت؛ ${free} گیگابایت آزاد است."
    }

    private fun runDiagnostics() {
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        respond("در حال بررسی وضعیت سام…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheBytes = runCatching {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
            val cacheMb = cacheBytes / (1024 * 1024)
            val packageCount = runCatching {
                packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    PackageManager.MATCH_DEFAULT_ONLY
                ).distinctBy { it.activityInfo.packageName }.size
            }.getOrDefault(0)
            val problems = mutableListOf<String>()
            if (batteryLevel in 0..15) problems += "باتری کم"
            if (packageCount == 0) problems += "فهرست برنامه‌ها خالی است"
            if (cacheMb > 100) problems += "کش سام بزرگ است"
            val report = if (problems.isEmpty()) {
                "عیب‌یابی سام انجام شد: برنامه سالم است؛ $packageCount برنامه قابل اجرا، باتری ${batteryLevel}٪ و کش $cacheMb مگابایت."
            } else {
                "عیب‌یابی سام: ${problems.joinToString("، ")}. برنامه‌های قابل اجرا: $packageCount؛ کش سام: $cacheMb مگابایت."
            }
            withContext(Dispatchers.Main) {
                binding.bubble.setState(AssistantBubbleView.State.READY)
                respond(report)
            }
        }
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

    private fun respond(message: String, speak: Boolean = speakResponses) {
        binding.response.text = message
        if (speak && ::textToSpeech.isInitialized && message.isNotBlank()) {
            binding.voiceStatus.setText(R.string.assistant_voice_speaking)
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raad-response")
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String = if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
