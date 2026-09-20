package de.szalkowski.activitylauncher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import android.provider.Telephony
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import de.szalkowski.activitylauncher.agent.AgentConfig
import de.szalkowski.activitylauncher.agent.AgentOutput
import de.szalkowski.activitylauncher.agent.PersonalMemory
import de.szalkowski.activitylauncher.agent.PrivacyGuard
import de.szalkowski.activitylauncher.agent.SamAgent
import de.szalkowski.activitylauncher.agent.SecureAiSettings
import de.szalkowski.activitylauncher.agent.SemanticAppSearch
import de.szalkowski.activitylauncher.agent.IntentRouter
import de.szalkowski.activitylauncher.agent.AppCommandNormalizer
import de.szalkowski.activitylauncher.agent.normalizeUserText
import de.szalkowski.activitylauncher.agent.SamIntentType
import de.szalkowski.activitylauncher.agent.ConversationCore
import de.szalkowski.activitylauncher.agent.OfflineKnowledgeStore
import de.szalkowski.activitylauncher.agent.SamAgentEngine
import de.szalkowski.activitylauncher.agent.EngineResponse
import de.szalkowski.activitylauncher.agent.CaregiverConversationViewModel
import de.szalkowski.activitylauncher.agent.data.AppDatabase
import de.szalkowski.activitylauncher.agent.data.UserProfileRepository
import de.szalkowski.activitylauncher.agent.data.AppManagerRepository
import de.szalkowski.activitylauncher.agent.data.CaregiverMessageEntity
import de.szalkowski.activitylauncher.ui.ChatMessageAdapter
import de.szalkowski.activitylauncher.ui.MechanicalSfx
import com.fgmembers.samlauncher.ui.chat.ModernChatScreen
import com.fgmembers.samlauncher.ui.components.MascotState
import androidx.recyclerview.widget.LinearLayoutManager
import de.szalkowski.activitylauncher.databinding.ActivityAssistantBinding
import de.szalkowski.activitylauncher.services.LastNotificationCache
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
        private const val FLASHLIGHT_PERMISSION_REQUEST = 4107
    }

    private lateinit var binding: ActivityAssistantBinding
    private var batteryLevel = -1
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var codeWriter: CodeWriter
    private lateinit var agent: SamAgent
    private lateinit var personalMemory: PersonalMemory
    private lateinit var privacyGuard: PrivacyGuard
    private lateinit var conversationCore: ConversationCore
    private lateinit var offlineKnowledge: OfflineKnowledgeStore
    private lateinit var knowledgeEngine: SamAgentEngine
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var appManagerRepository: AppManagerRepository
    private val caregiverViewModel: CaregiverConversationViewModel by viewModels()
    private var speakResponses = true
    private var pendingBankOnly = false
    private var pendingContactName = ""
    private var configuredAiProvider = ""
    private var configuredAiFingerprint = ""
    private val learningPreferences by lazy { getSharedPreferences("sam_learning", MODE_PRIVATE) }
    private lateinit var chatAdapter: ChatMessageAdapter
    private var composeMessages by mutableStateOf<List<CaregiverMessageEntity>>(emptyList())
    private var composeBatteryPercent by mutableStateOf(0)
    private var composeMascotState by mutableStateOf(MascotState.Idle)
    private var composeOnline by mutableStateOf(false)
    private lateinit var mechanicalSfx: MechanicalSfx

    private val smsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readLatestSms(pendingBankOnly)
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

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lookupAndDialContact(pendingContactName)
        else respond("برای جستجوی مخاطب، اجازهٔ مخاطبین لازم است.")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBatteryLevel(intent)
        }
    }

    private fun updateBatteryLevel(intent: Intent?) {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val broadcastPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val propertyPercent = (getSystemService(BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 } ?: -1
        batteryLevel = (broadcastPercent.takeIf { it in 0..100 } ?: propertyPercent)
            .takeIf { it in 0..100 } ?: 0
        composeBatteryPercent = batteryLevel
        binding.batteryStatus.text = getString(R.string.assistant_battery_status, batteryLevel)
        if (batteryLevel in 0..15) binding.response.text = getString(R.string.assistant_low_battery, batteryLevel)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistantBinding.inflate(layoutInflater)
        mechanicalSfx = MechanicalSfx(this)
        // Binding remains an off-screen compatibility bridge for voice, TTS and legacy actions.
        chatAdapter = ChatMessageAdapter()
        binding.chatRecycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = false }
        binding.chatRecycler.adapter = chatAdapter
        updateBatteryLevel(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                caregiverViewModel.messages.collect { messages ->
                    composeMessages = messages
                    chatAdapter.submitList(messages) {
                        if (messages.isNotEmpty()) binding.chatRecycler.scrollToPosition(messages.lastIndex)
                    }
                    messages.lastOrNull()?.let { message -> binding.response.text = message.content }
                }
            }
        }
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
        offlineKnowledge = OfflineKnowledgeStore(this)
        conversationCore = ConversationCore(offlineKnowledge)
        knowledgeEngine = SamAgentEngine(this)
        userProfileRepository = UserProfileRepository(AppDatabase.get(this).userProfileDao())
        appManagerRepository = AppManagerRepository(this)
        val aiSettings = SecureAiSettings(this)
        val provider = aiSettings.getProvider()
        configuredAiProvider = provider
        val storedGemini = aiSettings.get("gemini")
        val storedOpenAi = aiSettings.get("openai")
        configuredAiFingerprint = aiFingerprint(aiSettings)
        val useGemini = provider == "gemini"
        agent = SamAgent(
            this,
            AgentConfig(
                name = "سام",
                geminiApiKey = if (useGemini) storedGemini.ifBlank { BuildConfig.GEMINI_API_KEY } else "",
                openAiApiKey = if (!useGemini) storedOpenAi.ifBlank { BuildConfig.OPENAI_API_KEY } else "",
                model = if (useGemini) {
                    aiSettings.getModel().takeUnless { it.isBlank() || it.startsWith("gpt-") } ?: "gemini-2.5-flash"
                } else {
                    aiSettings.getModel().takeUnless { it.isBlank() || it.startsWith("gemini") } ?: "gpt-4o-mini"
                },
                provider = provider,
                openAiBaseUrl = aiSettings.getBaseUrl()
            )
        )
        val canRecognize = packageManager.resolveActivity(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null
        if (!canRecognize) {
            binding.voiceButton.isEnabled = false
            binding.voiceButton.alpha = 0.5f
            binding.voiceStatus.text = getString(R.string.voice_unavailable)
        }
        updateAgentStatus()
        binding.command.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submitCommand(); true } else false
        }
        binding.sendButton.setOnClickListener { mechanicalSfx.messageSent(); submitCommand() }
        binding.voiceButton.setOnClickListener { mechanicalSfx.click(); requestVoiceInput() }
        binding.bubble.setOnClickListener { mechanicalSfx.click(); binding.command.requestFocus() }
        binding.appearanceButton.setOnClickListener {
            mechanicalSfx.panelOpen()
            binding.bubble.nextAppearance()
            respond(getString(R.string.assistant_appearance_changed))
        }
        binding.otherAiButton.setOnClickListener {
            mechanicalSfx.panelOpen()
            shareWithOtherAi(binding.command.text?.toString().orEmpty().trim())
        }
        binding.aiSettingsButton.setOnClickListener {
            mechanicalSfx.panelOpen()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        setContent {
            ModernChatScreen(
                messages = composeMessages,
                isOnline = composeOnline,
                batteryPercent = composeBatteryPercent,
                status = getString(R.string.assistant_agent_ready),
                mascotState = composeMascotState,
                onSend = { mechanicalSfx.messageSent(); runCommand(it) },
                onVoice = { mechanicalSfx.click(); requestVoiceInput() },
                onAttachment = { mechanicalSfx.panelOpen(); openAttachmentPicker() },
                onQuickAction = { mechanicalSfx.panelOpen(); handleQuickAction(it) },
                onAppearance = { mechanicalSfx.panelOpen(); respond(getString(R.string.assistant_appearance_changed)) },
                onOtherAi = { mechanicalSfx.panelOpen(); shareWithOtherAi("") },
                onSettings = { mechanicalSfx.panelOpen(); startActivity(Intent(this, SettingsActivity::class.java)) },
            )
        }
        intent.getStringExtra(EXTRA_INITIAL_COMMAND)?.takeIf { it.isNotBlank() }?.let { command ->
            window.decorView.post { runCommand(command) }
        }
    }

    override fun onStart() {
        super.onStart()
        val initial = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        updateBatteryLevel(initial)
    }

    override fun onResume() {
        super.onResume()
        if (::agent.isInitialized) {
            val settings = SecureAiSettings(this)
            val provider = settings.getProvider()
            if (provider != configuredAiProvider || aiFingerprint(settings) != configuredAiFingerprint) recreate()
            updateAgentStatus()
        }
    }

    private fun aiFingerprint(settings: SecureAiSettings): String = listOf(
        settings.getMode(), settings.getProvider(), settings.get("gemini"), settings.get("openai"),
        settings.getBaseUrl(), settings.getModel()
    ).joinToString("|").hashCode().toString()

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        if (::mechanicalSfx.isInitialized) mechanicalSfx.close()
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

    private fun openAttachmentPicker() {
        startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        })
    }

    private fun handleQuickAction(action: String) {
        when (action) {
            "files" -> openAttachmentPicker()
            "system" -> runCommand("وضعیت سیستم را بگو")
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            "search" -> { binding.command.setText("جستجو: "); binding.command.requestFocus() }
            "flashlight" -> runCommand("چراغ قوه را روشن کن")
        }
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
        caregiverViewModel.saveUserMessage(rawCommand)
        learnInteraction(rawCommand)
        if (rawCommand.contains("اتصال آنلاین") || rawCommand.contains("اتصال انلاین") || rawCommand.contains("online", ignoreCase = true)) {
            SecureAiSettings(this).setMode("cloud")
            composeOnline = hasConfiguredAiProvider() && isNetworkAvailable()
            respond(if (composeOnline) "اتصال آنلاین برقرار شد." else "حالت آنلاین فعال شد؛ برای پاسخ ابری، کلید AI و اینترنت را بررسی کن.")
            return
        }
        val selectedMode = SecureAiSettings(this).getMode()
        // Natural language must reach the configured Agent/LLM; only explicit phone
        // actions are handled by the deterministic local router.
        if (!isDeterministicPhoneCommand(rawCommand) && hasConfiguredAiProvider()) {
            updateAgentStatus()
            runAgentCommand(rawCommand)
            return
        }
        if (IntentRouter.route(rawCommand).type == SamIntentType.READ_LATEST_NOTIFICATION) {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        } else if (selectedMode == "local") {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        } else if (selectedMode == "web" && !isDeterministicPhoneCommand(rawCommand)) {
            updateAgentStatus()
            answerWithWebFallback(rawCommand)
        } else if (isDeterministicPhoneCommand(rawCommand)) {
            // Settings, app launch, battery, messages, and other phone actions
            // must never wait for a network Provider.
            updateAgentStatus()
            runLocalCommand(rawCommand)
        } else if (shouldPreferAgentForNaturalAppCommand(rawCommand)) {
            updateAgentStatus()
            runAgentCommand(rawCommand)
        } else if (hasConfiguredAiProvider()) {
            runAgentCommand(rawCommand)
        } else {
            updateAgentStatus()
            runLocalCommand(rawCommand)
        }
    }

    private fun isMessageReadRequest(rawCommand: String): Boolean {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        val latest = command.contains("آخرین پیام") || command.contains("آخرینپیام") ||
            command.contains("آخرین اعلان") || command.contains("last message") ||
            command.contains("latest message") || command.contains("latest notification")
        val read = command.contains("بخوان") || command.contains("بخون") ||
            command.contains("نمایش بده") || command.contains("چیست") || command.contains("چیه") ||
            command.contains("read") || command.contains("what")
        val source = command.contains("پیامنگار") || command.contains("پیام نگار") ||
            command.contains("پیام‌رسان") || command.contains("پیام رسان") ||
            command.contains("تلگرام") || command.contains("telegram") ||
            command.contains("جیمیل") || command.contains("gmail") ||
            command.contains("ایمیل") || command.contains("email")
        return latest || (read && source)
    }

    private fun shouldPreferAgentForNaturalAppCommand(rawCommand: String): Boolean {
        if (!hasConfiguredAiProvider()) return false
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        val launchLanguage = listOf(
            "باز کن", "اجرا کن", "راه اندازی کن", "راه‌اندازی کن", "برو به", "برو توی", "برو تو",
            "open ", "launch ", "run ", "start "
        )
        val sensitive = listOf("پیامک", "sms", "تماس", "call", "رمز", "بانک", "password", "رمز عبور")
        return launchLanguage.any(command::contains) && sensitive.none(command::contains)
    }

    private fun hasConfiguredAiProvider(): Boolean {
        val settings = SecureAiSettings(this)
        // Do not lock the app to the selected provider: if its key is missing,
        // the other configured provider must still be eligible for fallback.
        return (settings.get("gemini").isConfigured() || BuildConfig.GEMINI_API_KEY.isConfigured()) ||
            (settings.get("openai").isConfigured() || BuildConfig.OPENAI_API_KEY.isConfigured())
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun isDeterministicPhoneCommand(rawCommand: String): Boolean {
        val command = rawCommand.lowercase(Locale.ROOT)
        return listOf(
            "باتری", "battery", "ساعت", "time", "تنظیمات", "settings", "وضعیت", "خلاصه",
            "باز کن", "open ", "تماس بگیر", "call ", "پیامک", "sms", "ایمیل", "email", "جیمیل", "gmail",
            "جستجو", "search", "در وب", "در اینترنت",
            "پیدا کن", "یافتن برنامه", "برنامه مناسب", "find app", "find my", "locate app",
            "آخرین پیام", "آخرینپیام", "آخرین اعلان", "بخوان", "read message", "last message", "latest message", "latest sms", "علایق من", "چی دوست دارم",
            "هوش مصنوعی گوشی", "دستیار گوشی", "ask another ai", "other ai",
            "هوش‌های دیگر", "هوش های دیگر", "از هوش‌های دیگر", "از هوش های دیگر", "share ai",
            "سلام", "درود", "hello", "hi", "پیامنگار", "پیام نگار", "پیام‌رسان", "پیام رسان", "اس ام اس", "پیام بده",
            "پیام بفرست", "تلگرام", "برو تل", "زنگ بزن",
            "چه خبر", "چخبر", "چهخبر", "اخبار جهان", "خبرهای امروز", "اخبار مهم", "world news", "latest news",
            "سیاست", "ترید", "رمز ارز", "ارز دیجیتال", "درس", "اخبار روز",
            "حالت کار", "work mode", "حالت رانندگی", "driving mode", "حالت خواب", "sleep mode",
            "حافظه", "remember ", "به خاطر بسپار", "حریم خصوصی", "privacy mode", "حالت مهمان", "guest mode"
        ).any(command::contains)
    }

    private fun runAgentCommand(rawCommand: String) {
        composeMascotState = MascotState.Thinking
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        binding.agentStatus.setText(R.string.assistant_thinking)
        respond(getString(R.string.assistant_thinking), speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val agentContext = deviceContext()
            val settings = SecureAiSettings(this@AssistantActivity)
            val primary = runCatching { agent.processCommand(rawCommand, agentContext) }
                .getOrElse { AgentOutput("خطای اجرای Provider: ${it.message.orEmpty().take(140)}", success = false) }
            val primaryProvider = settings.getProvider()
            val fallback = if (!primary.success && settings.getMode() == "cloud") {
                alternateProviderAgent(settings, primaryProvider)?.let { candidate ->
                    runCatching { candidate.processCommand(rawCommand, agentContext) }.getOrNull()
                }
            } else null
            val output = fallback?.takeIf { it.success } ?: primary
            val usedFallback = fallback?.success == true
            withContext(Dispatchers.Main) {
                    if (usedFallback) {
                        binding.agentStatus.text = "پاسخ با Provider جایگزین آماده شد"
                    } else {
                        updateAgentStatus(output.success)
                    }
                        binding.bubble.setState(if (output.success) AssistantBubbleView.State.READY else AssistantBubbleView.State.ERROR)
                        composeMascotState = if (output.success) MascotState.Speaking else MascotState.Idle
                        if (!output.success) {
                            // Cloud failure must never leave the user without an answer.
                            // Keep the failure in the status line, then execute the same
                            // command through deterministic local routing/conversation.
                            binding.agentStatus.text = getString(R.string.assistant_scientific_fallback)
                            runLocalCommand(rawCommand)
                            binding.bubble.setState(AssistantBubbleView.State.READY)
                            return@withContext
                        }
                        val tools = output.executedTools.distinct()
                        val reply = if (tools.isEmpty()) output.reply
                        else "${output.reply}\n\n${getString(R.string.assistant_agent_tools, tools.joinToString(", "))}"
                        if (shouldCacheKnowledge(rawCommand, output.reply)) {
                            offlineKnowledge.save(rawCommand, output.reply)
                        }
                        respond(if (usedFallback) "[پاسخ از Provider جایگزین]\n$reply" else reply)
                        binding.bubble.setState(AssistantBubbleView.State.READY)
            }
        }
    }

    private fun alternateProviderAgent(settings: SecureAiSettings, primary: String): SamAgent? {
        val alternate = if (primary == "gemini") "openai" else "gemini"
        val key = settings.get(alternate).ifBlank {
            if (alternate == "gemini") BuildConfig.GEMINI_API_KEY else BuildConfig.OPENAI_API_KEY
        }
        if (key.isBlank()) return null
        val useGemini = alternate == "gemini"
        val model = if (useGemini) {
            settings.getModel().takeUnless { it.isBlank() || it.startsWith("gpt-") } ?: "gemini-2.5-flash"
        } else {
            settings.getModel().takeUnless { it.isBlank() || it.startsWith("gemini") } ?: "gpt-4o-mini"
        }
        return SamAgent(this, AgentConfig(
            name = "سام",
            geminiApiKey = if (useGemini) key else "",
            openAiApiKey = if (useGemini) "" else key,
            model = model,
            provider = alternate,
            openAiBaseUrl = settings.getBaseUrl()
        ))
    }

    private fun shouldCacheKnowledge(question: String, answer: String): Boolean {
        if (answer.isBlank() || !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("ai_auto_refresh", false)) return false
        val sensitive = listOf("پیامک", "sms", "جیمیل", "gmail", "ایمیل", "email", "رمز", "password", "بانک", "bank", "تلگرام", "telegram")
        return sensitive.none(question.lowercase(Locale.ROOT)::contains)
    }

    private fun updateAgentStatus(success: Boolean? = null) {
        val provider = SecureAiSettings(this).getProvider()
        binding.agentStatus.text = when {
            success == false -> getString(R.string.assistant_agent_error)
            success == true && provider == "openai" -> getString(R.string.agent_status_openai)
            success == true && provider == "custom" -> getString(R.string.agent_status_custom)
            success == true -> getString(R.string.agent_status_gemini)
            hasConfiguredAiProvider() && provider == "openai" -> getString(R.string.agent_status_openai_ready)
            hasConfiguredAiProvider() && provider == "custom" -> getString(R.string.agent_status_custom_ready)
            else -> getString(R.string.agent_status_scientific)
        }
    }

    private fun String.isConfigured(): Boolean = isNotBlank() && this != "YOUR_GEMINI_API_KEY_HERE"

    private suspend fun deviceContext(): String {
        val now = Calendar.getInstance()
        val profile = userProfileRepository.promptSummary()
        val profileContext = profile.takeIf { it.isNotBlank() }?.let {
            "؛ پروفایل محلی کاربر (فقط برای شخصی‌سازی پاسخ): $it"
        }.orEmpty()
        val apps = appManagerRepository.getInstalledApps()
            .take(40)
            .joinToString("، ") { "${it.name} (${it.sizeInMegabytes()}MB)" }
        val appContext = apps.takeIf { it.isNotBlank() }?.let {
            "؛ برنامه‌های نصب‌شده برای تحلیل محلی: $it"
        }.orEmpty()
        return "زمان: ${now.get(Calendar.HOUR_OF_DAY)}:${now.get(Calendar.MINUTE)}؛ باتری: $batteryLevel٪؛ زبان: fa-IR$profileContext$appContext"
    }

    private fun runLocalCommand(rawCommand: String) {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        when {
            command.contains("چراغ قوه") || command.contains("چراغقوه") ||
                command.contains("فلش گوشی") || command.contains("flashlight") || command.contains("torch") ->
                toggleFlashlight(command)
            command.contains("وای فای") || command.contains("وای‌فای") ||
                command.contains("wifi") || command.contains("wi-fi") ->
                openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "تنظیمات وای‌فای")
            command.contains("بلوتوث") || command.contains("bluetooth") ->
                openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "تنظیمات بلوتوث")
            command.contains("هوش‌های دیگر") || command.contains("هوش های دیگر") ||
                command.contains("از هوش‌های دیگر") || command.contains("از هوش های دیگر") ||
                command.contains("share ai") -> shareWithOtherAi(extractSharedQuestion(rawCommand))
            command.contains("هوش مصنوعی گوشی") || command.contains("دستیار گوشی") ||
                command.contains("ask another ai") || command.contains("other ai") -> openSystemAssistant()
            command.contains("اخبار جهان") || command.contains("خبرهای امروز") || command.contains("اخبار مهم") ||
                command.contains("اخبار روز") || command.contains("world news") || command.contains("latest news") -> showWorldNews()
            command == "چه خبر" || command == "چخبر" || command == "چهخبر" || command.contains("چه خبر از جهان") -> showWorldNews()
            command.contains("برنامه‌های سنگین") || command.contains("برنامه های سنگین") ||
                command.contains("برنامه‌های کم‌استفاده") || command.contains("برنامه های کم استفاده") ||
                command.contains("برنامه‌های اضافی") || command.contains("unused apps") ||
                command.contains("heavy apps") || command.contains("بهینه سازی برنامه") -> showAppOptimizationReport()
            (command.contains("آخرین پیام") || command.contains("آخرینپیام")) &&
                (command.contains("پیامنگار") || command.contains("پیام‌رسان") || command.contains("پیام رسان")) ->
                requestLatestNotification("پیام‌رسان", emptyList())
            (command.contains("آخرین") || command.contains("latest") || command.contains("چک") || command.contains("بررسی") || command.contains("check")) &&
                (command.contains("جیمیل") || command.contains("gmail") || command.contains("ایمیل") || command.contains("email")) ->
                requestLatestNotification("Gmail", listOf("com.google.android.gm"))
            (command.contains("آخرین پیام") || command.contains("آخرینپیام") ||
                command.contains("آخرین اعلان") || command.contains("last message") || command.contains("latest message")) ->
                requestLatestNotification("پیام یا اعلان", emptyList())
            (command.contains("پیامنگار") || command.contains("پیام نگار") ||
                command.contains("پیام‌رسان") || command.contains("پیام رسان")) &&
                (command.contains("باز") || command.contains("open") || command.contains("launch") || command.contains("برو")) ->
                openApp("پیام")
            (command.contains("آخرین پیام") || command.contains("آخرینپیام") ||
                command.contains("چک") || command.contains("بررسی") || command.contains("check") ||
                command.contains("اعلان") || command.contains("نوتیف") ||
                command.contains("پیام تلگرام") || command.contains("پیام‌های تلگرام")) &&
                (command.contains("تلگرام") || command.contains("telegram")) -> requestLatestTelegramNotification()
            command.contains("آخرین پیام") || command.contains("آخرینپیام") ||
                command.contains("آخرین اس ام اس") || command.contains("آخریناس ام اس") ||
                command.contains("last message") || command.contains("latest sms") ->
                requestLatestSms(command.contains("بانک") || command.contains("bank"))
            command.contains("ایمیل") || command.contains("email") || command.contains("جیمیل") || command.contains("gmail") ->
                if (command.contains("به ") || command.contains("برای ") || command.contains("to ") || command.contains("send") || command.contains("ارسال")) prepareEmail(rawCommand)
                else openGmail()
            command.contains("تلگرام") || command.contains("برو تل") || command.contains("telegram") -> openApp("telegram")
            command.contains("پیامنگار") || command.contains("پیام نگار") ||
                command.contains("پیام‌رسان") || command.contains("پیام رسان") -> openApp("پیام")
            command.contains("اس ام اس") || command.contains("پیام بده") || command.contains("پیام بفرست") -> prepareSms(rawCommand)
            command.contains("زنگ بزن") -> prepareCall(rawCommand)
            command.contains("تنظیمات") || command.contains("settings") -> openSettings()
            (command.contains("موزیک") || command.contains("موسیقی") || command.contains("آهنگ") ||
                command.contains("music") || command.contains("song")) &&
                (command.contains("پخش") || command.contains("اجرا") || command.contains("play") ||
                    command.contains("باز")) -> openApp("موسیقی")
            command.contains("بررسی باتری") || command.contains("وضعیت باتری") ||
                command.contains("باتری") || command.contains("battery") -> respond(getString(R.string.assistant_battery_status, batteryLevel))
            command.contains("حافظه گوشی") || command.contains("حافظه دستگاه") ||
                command.contains("storage") || command.contains("memory status") -> respond(storageSummary())
            command.contains("اختلال") || command.contains("عیب یابی") || command.contains("عیب‌یابی") ||
                command.contains("عیبیابی") || command.contains("diagnostic") || command.contains("check sam") -> runDiagnostics()
            command.startsWith("open ") -> openApp(rawCommand.removePrefixIgnoreCase("open ").trim())
            command.startsWith("open") || command.startsWith("launch") || command.startsWith("run ") ||
                command.contains("اجرا کن") || command.contains("راه‌اندازی کن") || command.contains("راه اندازی کن") ->
                openApp(extractAppQuery(rawCommand))
            command.startsWith("باز کن ") -> openApp(extractAppQuery(rawCommand))
            command.contains("پیدا کن") || command.contains("یافتن برنامه") ||
                command.contains("find app") || command.contains("find my") || command.contains("locate app") ->
                openApp(extractAppQuery(rawCommand))
            command.contains("باز کن") -> {
                openApp(extractAppQuery(rawCommand))
            }
            command.startsWith("search ") || command.startsWith("search for ") -> searchWeb(extractSearchQuery(rawCommand))
            command.startsWith("جستجو ") || command.startsWith("سرچ ") || command.startsWith("بگرد ") ->
                searchWeb(extractSearchQuery(rawCommand))
            command.contains("در وب جستجو") || command.contains("در اینترنت جستجو") -> searchWeb(
                extractSearchQuery(rawCommand.substringAfter("جستجو").trim())
            )
            command.contains("device status") || command.contains("وضعیت") || command.contains("خلاصه") -> respond(localDeviceSummary())
            command.contains("time") || command.contains("ساعت") -> respond(currentTime())
            command.contains("dashboard") || command.contains("داشبورد") || command.contains("خلاصه امروز") -> respond(dailyDashboard())
            command.contains("work mode") || command.contains("حالت کار") -> activateSmartMode("کار")
            command.contains("driving mode") || command.contains("حالت رانندگی") -> activateSmartMode("رانندگی")
            command.contains("sleep mode") || command.contains("حالت خواب") -> activateSmartMode("خواب")
            command.contains("تماس بگیر") || command.contains("call ") -> prepareCall(rawCommand)
            command.contains("پیامک") || command.contains("sms") -> prepareSms(rawCommand)
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
                if (localReply != null && !shouldUseWebForQuestion(rawCommand)) respond(localReply)
                else answerWithWebFallback(rawCommand)
            }
        }
    }

    private fun toggleFlashlight(command: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            respond("کنترل چراغ‌قوه در این نسخهٔ اندروید پشتیبانی نمی‌شود.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), FLASHLIGHT_PERMISSION_REQUEST)
            respond("برای کنترل چراغ‌قوه، اجازهٔ دوربین را تأیید کن و دوباره فرمان را بگو.")
            return
        }
        val preferences = getSharedPreferences("sam_preferences", MODE_PRIVATE)
        val previous = preferences.getBoolean("flashlight_on", false)
        val target = when {
            command.contains("خاموش") || command.contains("off") -> false
            command.contains("روشن") || command.contains("on") -> true
            else -> !previous
        }
        val result = runCatching {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: error("camera_not_found")
            cameraManager.setTorchMode(cameraId, target)
            preferences.edit().putBoolean("flashlight_on", target).apply()
        }
        respond(if (result.isSuccess) {
            if (target) "چراغ‌قوه روشن شد." else "چراغ‌قوه خاموش شد."
        } else {
            "نتوانستم چراغ‌قوه را تغییر بدهم؛ ممکن است دوربین در حال استفاده باشد."
        })
    }

    private fun openSystemSettings(action: String, title: String) {
        runCatching { startActivity(Intent(action)) }
            .onSuccess { respond("$title باز شد.") }
            .onFailure { respond("باز کردن $title ممکن نبود؛ از تنظیمات گوشی وارد شو.") }
    }

    private fun shouldUseWebForQuestion(rawCommand: String): Boolean {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        return command.contains("جستجو") || command.contains("سرچ") || command.contains("در وب") ||
            command.contains("در اینترنت") || command.contains("بررسی کن") ||
            command.contains("اطلاعات") || command.contains("تاریخچه") || command.contains("چیست") ||
            command.contains("چیه") || command.contains("what is") || command.contains("history") ||
            command.endsWith("؟") || command.endsWith("?")
    }

    private fun answerWithWebFallback(question: String) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            respond(getString(R.string.assistant_help))
            return
        }
        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("ai_web_answers", true)) {
            respond(offlineConversationReply(cleanQuestion))
            return
        }
        recordLearning("web", cleanQuestion.take(80))
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        respond("در حال بررسی دانش ذخیره‌شده و وب عمومی…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val answer = runCatching {
                when (val result = knowledgeEngine.processQuery(cleanQuestion, allowWeb = true)) {
                    is EngineResponse.Text -> result.message
                    is EngineResponse.Action -> if (result.actionType == "OPEN_SETTINGS") "تنظیمات را باز کن." else ""
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
            withContext(Dispatchers.Main) {
                binding.bubble.setState(AssistantBubbleView.State.READY)
                if (answer != null) respond(answer)
                else respond(offlineConversationReply(cleanQuestion))
            }
        }
    }

    private fun offlineConversationReply(question: String): String =
        "برای «${question.take(80)}» دادهٔ ذخیره‌شدهٔ کافی ندارم؛ سام بر پایهٔ اصول شناخته‌شده، فرض‌ها و شواهد موجود تحلیل می‌کند."

    private fun extractSearchQuery(command: String): String {
        val input = command.trim()
        val patterns = listOf(
            Regex("^(?:جستجو|سرچ|search)(?:\\s+کن(?:ید)?|\\s+for)?\\s+(.+)$", RegexOption.IGNORE_CASE),
            Regex("^(.+?)\\s+(?:را|رو)\\s+(?:جستجو|سرچ)(?:\\s+کن(?:ید)?)?$", RegexOption.IGNORE_CASE),
            Regex("^بگرد(?:\\s+(?:دنبال|برای))?\\s+(.+)$", RegexOption.IGNORE_CASE)
        )
        patterns.firstNotNullOfOrNull { pattern ->
            pattern.matchEntire(input)?.groupValues?.lastOrNull()?.trim()
        }?.takeIf { it.isNotBlank() }?.let { return it }

        return input
            .replace(Regex("^(?:جستجو|سرچ|search)(?:\\s+کن(?:ید)?|\\s+for)?\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
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
            command.contains("دلم گرفته") || command.contains("حالم بده") || command.contains("حالم خوب نیست") ||
                command.contains("دلگیرم") || command.contains("غمگینم") || command.contains("i feel sad") ||
                command.contains("feeling down") ->
                "متأسفم که این‌طور احساس می‌کنی. لازم نیست همه‌چیز را همین حالا حل کنی. اگر دوست داری، بگو بیشتر از چه چیزی ناراحتی؛ من گوش می‌دهم و قضاوتت نمی‌کنم."
            command.contains("استرس دارم") || command.contains("نگرانم") || command.contains("اضطراب") ||
                command.contains("i am stressed") || command.contains("i'm worried") ->
                "می‌فهمم؛ اول یک نفس آرام بکش و مسئله را به یک قدم کوچک تقسیم کنیم. بگو نگرانی‌ات بیشتر دربارهٔ کار، خانواده، پول یا موضوع دیگری است تا با هم یک راه عملی پیدا کنیم."
            command.contains("تنها هستم") || command.contains("احساس تنهایی") || command.contains("تنهایی") ||
                command.contains("i am lonely") ->
                "من اینجا هستم و می‌توانی با من حرف بزنی. اگر امکانش را داری، به یک آدم قابل اعتماد هم پیام کوتاهی بده؛ ارتباط واقعی می‌تواند کمک بزرگی باشد."
            command.contains("نمی‌دانم چه کار کنم") || command.contains("نمیدونم چیکار کنم") ||
                command.contains("what should i do") || command.contains("i don't know what to do") ->
                "بیاییم مسئله را ساده کنیم: الان مهم‌ترین چیزی که باید حل شود چیست؟ گزینه‌هایت را بگو تا مزایا و قدم بعدی هرکدام را با هم بررسی کنیم."
            command.contains("نصیحت") || command.contains("راهنمایی میخوام") || command.contains("advice") ->
                "حتماً. موضوع را در یک یا دو جمله بگو؛ من اول واقعیت‌ها، بعد گزینه‌های عملی و پیامدهایشان را روشن می‌کنم."
            command.contains("چه خبر") || command.contains("خبرها") ->
                "من خبرهای لحظه‌ای را فقط با اینترنت می‌توانم بررسی کنم. بگو «چه خبر از ...» تا خودم در وب جست‌وجو کنم."
            command.contains("حوصله ندارم") || command.contains("خسته ام") || command.contains("خسته‌ام") ->
                "می‌فهمم. اگر دوست داری با هم یک کار ساده انجام بدهیم؛ مثلاً موسیقی را باز کنم، وضعیت گوشی را بگویم یا فقط با هم صحبت کنیم."
            command.contains("کمک میخوام") || command.contains("کمک می‌خوام") || command == "کمک" ->
                "حتماً. من می‌توانم برنامه‌ها را باز کنم، باتری و حافظه را بررسی کنم، کش سام را پاک کنم، تنظیمات را باز کنم یا دربارهٔ موضوعات مختلف در وب جست‌وجو کنم."
            command.contains("با من حرف بزن") || command.contains("حرف بزن") ||
                command.contains("چت کنیم") || command.contains("گپ بزنیم") ||
                command.contains("let's chat") || command.contains("talk to me") ->
                "حتماً؛ من اینجا هستم. دوست داری دربارهٔ چه چیزی حرف بزنیم؟ می‌توانی درد دل کنی، سؤال بپرسی یا از من بخواهی موضوعی را مرحله‌به‌مرحله توضیح بدهم."
            command.contains("دوستت دارم") || command.contains("عاشقتم") ->
                "لطف داری. من هم اینجا هستم تا کارهایت را ساده‌تر کنم."
            command.contains("صبح بخیر") || command.contains("شب بخیر") ->
                "ممنون؛ امیدوارم روز یا شبت آرام و خوب پیش برود."
            command.contains("ممنون") || command.contains("مرسی") || command.contains("thank") ->
                "خواهش می‌کنم؛ هر وقت خواستی در خدمتم."
            command.contains("چه کارهایی") || command.contains("قابلیت") || command.contains("what can you do") ->
                "می‌توانم برنامه‌ها را باز کنم، برنامه مناسب را با مفهوم پیدا کنم، باتری و ساعت را بگویم، تنظیمات و حالت‌های کار/رانندگی/خواب را باز کنم و حافظه محلی داشته باشم."
            else -> conversationCore.reply(rawCommand)
        }
    }

    private fun openApp(query: String) {
        val normalized = extractAppQuery(query).normalizeUserText()
        if (normalized.isBlank()) { respond(getString(R.string.assistant_help)); return }
        if (!privacyGuard.canOpenApp(normalized)) {
            respond("حالت مهمان اجازه باز کردن این برنامه را نمی‌دهد.")
            return
        }
        respond("در حال پیدا کردن برنامه…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val match = SemanticAppSearch.findMatches(this@AssistantActivity, normalized, 1).firstOrNull()
                match to match?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                val match = result?.first
                val launchIntent = result?.second
                if (match == null || launchIntent == null) respond(getString(R.string.assistant_app_not_found, normalized))
                else runCatching { startActivity(launchIntent) }.onSuccess {
                    val label = match.loadLabel(packageManager).toString()
                    recordLearning("app", label)
                    learningPreferences.edit().putString("app_query_$normalized", match.packageName).apply()
                    respond(getString(R.string.assistant_opening, label))
                }.onFailure { respond("باز کردن برنامه ممکن نشد.") }
            }
        }
    }

    private fun extractAppQuery(rawCommand: String): String =
        AppCommandNormalizer.cleanAppTarget(rawCommand)

    private fun requestLatestSms(bankOnly: Boolean = false) {
        if (!BuildConfig.ALLOW_SMS_READ) {
            respond("خواندن پیامک در این نسخه غیرفعال است؛ پیام‌رسان را باز می‌کنم.")
            prepareSms("")
            return
        }
        pendingBankOnly = bankOnly
        AlertDialog.Builder(this)
            .setTitle("اجازهٔ خواندن پیامک")
            .setMessage("سام فقط برای پاسخ به همین درخواست، پیامک‌های صندوق ورودی را روی همین دستگاه بررسی می‌کند. متن پیام به اینترنت یا سرویس دیگری ارسال نمی‌شود. مسئولیت بررسی و استفاده از نتیجه با شماست.")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید و ادامه") { _, _ ->
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                    readLatestSms(pendingBankOnly)
                } else {
                    smsPermission.launch(Manifest.permission.READ_SMS)
                }
            }
            .show()
    }

    private fun isLikelyBankMessage(address: String, body: String): Boolean {
        val text = "$address $body".lowercase(Locale.ROOT)
        return address.any { it.isDigit() } && listOf(
            "بانک", "رمز پویا", "رمز دوم", "موجودی", "برداشت", "واریز", "انتقال", "شبا",
            "balance", "otp", "debit", "credit", "transaction"
        ).any(text::contains)
    }

    private fun bankMessageReply(): String = "یک پیام بانکی دریافت شد. برای حفظ حریم خصوصی، متن آن نمایش داده نمی‌شود."

    private fun readLatestSms(bankOnly: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                        val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                        if (!bankOnly || isLikelyBankMessage(address, body)) return@use address to body
                    }
                    null
                }
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result == null) {
                    respond(if (bankOnly) "پیام بانکی‌ای در صندوق ورودی پیدا نشد." else "پیامکی در صندوق ورودی پیدا نشد.")
                } else if (bankOnly) {
                    respond(bankMessageReply())
                } else {
                    respond("آخرین پیام از شمارهٔ ${result.first} است: ${result.second.take(240)}")
                }
            }
        }
    }

    private fun learnInteraction(rawCommand: String) {
        if (!privacyGuard.canPersistPersonalMemory() || !personalizationEnabled()) return
        val query = rawCommand.trim().replace(Regex("\\s+"), " ").take(80)
        if (query.isBlank()) return
        incrementLearning("conversation", query)
    }

    private fun recordLearning(category: String, value: String) {
        if (privacyGuard.canPersistPersonalMemory() && personalizationEnabled() && value.isNotBlank()) incrementLearning(category, value)
    }

    private fun personalizationEnabled(): Boolean = PreferenceManager
        .getDefaultSharedPreferences(this)
        .getBoolean("ai_personalization", true)

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
        if (number != null) { dialNumber(number); return }
        val name = rawCommand.replace(Regex("(?i)call|تماس بگیر|زنگ بزن|با|لطفاً|لطفا"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (name.isBlank()) { respond("نام مخاطب را بگو؛ مثلاً «با علی تماس بگیر». "); return }
        pendingContactName = name
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        } else lookupAndDialContact(name)
    }

    private fun lookupAndDialContact(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val phone = runCatching {
                contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$name%"), null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
            withContext(Dispatchers.Main) { if (phone == null) respond("مخاطبی با نام «$name» پیدا نشد.") else dialNumber(phone) }
        }
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        if (intent.resolveActivity(packageManager) != null) { startActivity(intent); respond("شماره‌گیر آماده شد؛ تماس نهایی با تأیید تو انجام می‌شود.") }
        else respond("شماره‌گیر روی این گوشی پیدا نشد.")
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

    private fun openGmail() {
        val gmailIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
        }
        val resolvedIntent = if (gmailIntent.resolveActivity(packageManager) != null) gmailIntent
        else Intent(Intent.ACTION_MAIN).setPackage("com.google.android.gm")
        if (resolvedIntent.resolveActivity(packageManager) != null) {
            startActivity(resolvedIntent)
            respond("جیمیل باز شد؛ برای خواندن ایمیل‌ها، بررسی و تأیید نهایی با خودت است.")
        } else {
            respond("برنامهٔ جیمیل پیدا نشد؛ ابتدا Gmail را نصب یا وارد حساب گوگل کن.")
        }
    }

    private fun requestLatestNotification(label: String, packageMatchers: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("اجازهٔ خواندن اعلان $label")
            .setMessage("سام فقط آخرین اعلان قابل‌نمایش $label را برای همین درخواست و فقط روی همین دستگاه بررسی می‌کند. صندوق حساب یا تاریخچهٔ چت مستقیماً خوانده نمی‌شود و چیزی به اینترنت ارسال نمی‌شود. تأیید می‌کنی؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید و ادامه") { _, _ -> showLatestNotification(label, packageMatchers) }
            .show()
    }

    private fun showLatestNotification(label: String, packageMatchers: List<String>) {
        val notificationAccessIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val cached = LastNotificationCache.get()
        val accessEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        val matches = cached != null && (packageMatchers.isEmpty() || packageMatchers.any { cached.packageName.contains(it, ignoreCase = true) })
        if (matches) {
            val sourceName = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(cached!!.packageName, 0)).toString()
            }.getOrElse { cached!!.packageName }
            respond("آخرین اعلان $label از برنامهٔ $sourceName، ${cached?.title?.ifBlank { "یک مخاطب" }}:\n${cached?.text}")
        } else if (!accessEnabled && notificationAccessIntent.resolveActivity(packageManager) != null) {
            startActivity(notificationAccessIntent)
            respond("برای خواندن آخرین اعلان $label، دسترسی اعلان سام را فعال کن؛ سپس یک اعلان جدید دریافت کن و دوباره بپرس.")
        } else {
            respond("اعلان قابل‌خواندنی از $label در حافظهٔ موقت پیدا نشد. یک اعلان جدید دریافت کن و دوباره بپرس.")
        }
    }

    private fun requestLatestTelegramNotification() {
        AlertDialog.Builder(this)
            .setTitle("اجازهٔ خواندن اعلان تلگرام")
            .setMessage("سام فقط متن آخرین اعلان قابل‌نمایش تلگرام را برای همین درخواست و فقط روی همین دستگاه بررسی می‌کند. متن چت‌های قدیمی خوانده نمی‌شود و چیزی به اینترنت یا سرویس دیگری ارسال نمی‌شود. تأیید می‌کنی؟")
            .setNegativeButton("انصراف", null)
            .setPositiveButton("تأیید و ادامه") { _, _ -> showLatestTelegramNotification() }
            .show()
    }

    private fun showLatestTelegramNotification() {
        val notificationAccessIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val cached = LastNotificationCache.get()
        val accessEnabled = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (cached?.packageName?.contains("telegram", ignoreCase = true) == true) {
            respond("آخرین اعلان تلگرام از ${cached.title.ifBlank { "یک مخاطب" }}:\n${cached.text}")
        } else if (accessEnabled) {
            respond("دسترسی اعلان تلگرام فعال است، اما هنوز اعلان جدیدی از تلگرام دریافت نشده. یک پیام جدید دریافت کن و دوباره بپرس.")
        } else if (notificationAccessIntent.resolveActivity(packageManager) != null) {
            startActivity(notificationAccessIntent)
            respond("برای دیدن آخرین پیام تلگرام، دسترسی اعلان سام را در این صفحه فعال کن. سام فقط متن اعلان‌های آینده را روی دستگاه نگه می‌دارد؛ تاریخچهٔ چت تلگرام قابل خواندن نیست.")
        } else {
            respond("صفحهٔ دسترسی اعلان‌ها در این دستگاه در دسترس نیست.")
        }
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
            runCatching {
                cacheDir.deleteRecursively()
                externalCacheDir?.deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                val storageIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                if (storageIntent.resolveActivity(packageManager) != null) startActivity(storageIntent)
                respond(getString(R.string.assistant_cache_guidance))
            }
        }
    }

    private fun showAppOptimizationReport() {
        binding.bubble.setState(AssistantBubbleView.State.THINKING)
        respond("در حال بررسی برنامه‌های نصب‌شده…", speak = false)
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = appManagerRepository.getUnusedOrHeavyApps()
            val report = if (apps.isEmpty()) {
                "برنامهٔ سنگین یا کم‌استفاده‌ای در بررسی فعلی پیدا نشد."
            } else {
                val lines = apps.take(10).mapIndexed { index, app ->
                    "${index + 1}. ${app.name} — ${app.sizeInMegabytes()} مگابایت؛ ${app.lastUsedLabel()}"
                }
                val accessNote = if (appManagerRepository.hasUsageAccess()) "" else
                    "\n\nبرای تشخیص دقیق آخرین استفاده، دسترسی Usage access را در تنظیمات اندروید فعال کن."
                "برنامه‌های سنگین یا کم‌استفاده (آستانهٔ حجم ۵۰۰ مگابایت یا عدم استفاده بیش از ۳۰ روز):\n" +
                    lines.joinToString("\n") + accessNote
            }
            withContext(Dispatchers.Main) {
                binding.bubble.setState(AssistantBubbleView.State.READY)
                respond(report)
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
                "عیب‌یابی سام انجام شد: برنامه سالم است؛ $packageCount برنامه قابل اجرا، باتری ${batteryLevel}٪ و کش ${cacheMb} مگابایت."
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
        return "وضعیت دستگاه: باتری ${batteryLevel}٪؛ ساعت $time؛ اندروید ${android.os.Build.VERSION.RELEASE}؛ موتور تحلیل علمی فعال است."
    }

    private fun currentTime(): String {
        val now = Calendar.getInstance()
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
        } else if (personalMemory.remember(fact)) {
            lifecycleScope.launch(Dispatchers.IO) {
                userProfileRepository.save("memory_${fact.hashCode()}", fact, "memory")
            }
            respond("به خاطر سپردم؛ این اطلاعات فقط روی دستگاه ذخیره شد.")
        }
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
        composeMascotState = if (speak && message.isNotBlank()) MascotState.Speaking else MascotState.Idle
        binding.response.text = message
        caregiverViewModel.saveAssistantMessage(message)
        if (speak && ::textToSpeech.isInitialized && message.isNotBlank()) {
            binding.voiceStatus.setText(R.string.assistant_voice_speaking)
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raad-response")
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String = if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
