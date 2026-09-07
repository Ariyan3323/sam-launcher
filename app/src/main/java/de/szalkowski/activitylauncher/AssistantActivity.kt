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
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import de.szalkowski.activitylauncher.databinding.ActivityAssistantBinding
import java.util.Calendar
import java.util.Locale

/** Local assistant with command input, voice transcription and safe Android actions. */
class AssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var binding: ActivityAssistantBinding
    private var batteryLevel = -1
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var codeWriter: CodeWriter

    private val voiceInput =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val phrase = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (phrase != null) runCommand(phrase)
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput() else respond(getString(R.string.assistant_microphone_required))
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryLevel = if (level >= 0 && scale > 0) level * 100 / scale else -1
            binding.batteryStatus.text = getString(R.string.assistant_battery_status, batteryLevel)
            if (batteryLevel in 0..15) {
                binding.response.text = getString(R.string.assistant_low_battery, batteryLevel)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        textToSpeech = TextToSpeech(this, this)
        codeWriter = CodeWriter(this)
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
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech.language = Locale.getDefault()
            }
        }
    }

    private fun submitCommand() {
        val command = binding.command.text?.toString()?.trim().orEmpty()
        if (command.isNotEmpty()) runCommand(command)
    }

    private fun requestVoiceInput() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) startVoiceInput()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVoiceInput() {
        val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
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
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        when {
            command.startsWith("open ") -> openApp(rawCommand.removePrefixIgnoreCase("open ").trim())
            command.startsWith("باز کن ") -> openApp(rawCommand.removePrefix("باز کن ").trim())
            command.startsWith("search ") -> searchWeb(rawCommand.removePrefixIgnoreCase("search ").trim())
            command.startsWith("جستجو ") -> searchWeb(rawCommand.removePrefix("جستجو ").trim())
            command.contains("battery") || command.contains("باتری") ->
                respond(getString(R.string.assistant_battery_status, batteryLevel))
            command.contains("time") || command.contains("ساعت") -> respond(currentTime())
            command.contains("clock code") || command.contains("کد ساعت") -> exportClockCode()
            command.contains("clear cache") || command.contains("پاک") && command.contains("کش") ->
                openStorageSettings()
            command.contains("settings") || command.contains("تنظیمات") -> openSettings()
            command.contains("help") || command.contains("راهنما") -> respond(getString(R.string.assistant_help))
            else -> searchWeb(rawCommand)
        }
    }

    private fun openApp(query: String) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) {
            respond(getString(R.string.assistant_help))
            return
        }
        val match = packageManager.getInstalledApplications(PackageManager.MATCH_ALL).firstOrNull {
            it.loadLabel(packageManager).toString().lowercase(Locale.ROOT).contains(normalized)
        }
        val launchIntent = match?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
        if (launchIntent == null) respond(getString(R.string.assistant_app_not_found, query))
        else {
            startActivity(launchIntent)
            respond(getString(R.string.assistant_opening, match.loadLabel(packageManager)))
        }
    }

    private fun searchWeb(query: String) {
        if (query.isBlank()) return
        val searchIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        )
        if (searchIntent.resolveActivity(packageManager) == null) {
            respond(getString(R.string.assistant_browser_unavailable))
            return
        }
        startActivity(searchIntent)
        respond(getString(R.string.assistant_searching, query))
    }

    private fun openStorageSettings() {
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        val storageIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        if (storageIntent.resolveActivity(packageManager) != null) {
            startActivity(storageIntent)
        }
        respond(getString(R.string.assistant_cache_guidance))
    }

    private fun openSettings() {
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        if (settingsIntent.resolveActivity(packageManager) != null) {
            startActivity(settingsIntent)
            respond(getString(R.string.assistant_opening_settings))
        } else {
            respond(getString(R.string.assistant_browser_unavailable))
        }
    }

    private fun currentTime(): String {
        val now = Calendar.getInstance()
        binding.analogClock.setTime(
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
        )
        return getString(
            R.string.assistant_time,
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND)
        )
    }

    private fun exportClockCode() {
        runCatching { codeWriter.writeClockCode() }
            .onSuccess { file -> respond(getString(R.string.assistant_clock_code_saved, file.absolutePath)) }
            .onFailure { respond(getString(R.string.assistant_clock_code_export_failed)) }
    }

    private fun respond(message: String) {
        binding.response.text = message
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "raad-response")
        }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
