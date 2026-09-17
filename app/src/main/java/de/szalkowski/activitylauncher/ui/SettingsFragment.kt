package de.szalkowski.activitylauncher.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import de.szalkowski.activitylauncher.MainActivity
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.services.RootDetectionService
import de.szalkowski.activitylauncher.services.SettingsService
import de.szalkowski.activitylauncher.agent.SecureAiSettings
import java.util.Objects
import javax.inject.Inject


@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: SharedPreferences
    private var needsRestart: Boolean = false

    @Inject
    internal lateinit var rootDetectionService: RootDetectionService

    @Inject
    internal lateinit var settingsService: SettingsService

    override fun onDestroy() {
        super.onDestroy()
        if (!needsRestart) return

        // workaround for applying settings by restarting app - PRs welcome
        // FIXME reset the services state and reload affected activities
        restartApp()
    }

    private fun restartApp() {
        val intent = Intent(
            this.requireContext(),
            MainActivity::class.java,
        )

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        this.startActivity(intent)
        this.requireActivity().finishAffinity()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity().baseContext)
        val secureAi = SecureAiSettings(requireContext())

        val hidePrivate: SwitchPreference = Objects.requireNonNull(findPreference("hide_private"))
        val allowRoot: SwitchPreference = Objects.requireNonNull(findPreference("allow_root"))
        val theme: ListPreference = Objects.requireNonNull(findPreference("theme"))
        val languages: ListPreference = Objects.requireNonNull(findPreference("language"))

        languages.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance())
        populateLanguages(languages)
        languages.setOnPreferenceChangeListener { _, newValue ->
            onLanguageUpdated(
                newValue as String
            )
        }

        hidePrivate.setOnPreferenceChangeListener { _, newValue ->
            onHidePrivateUpdated(
                newValue as Boolean
            )
        }

        allowRoot.setOnPreferenceChangeListener { _, newValue ->
            onAllowRootUpdated(
                newValue as Boolean
            )
        }

        theme.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance())
        theme.setOnPreferenceChangeListener { _, newValue -> onThemeUpdated(newValue as String) }

        val provider = findPreference<ListPreference>("ai_provider")!!
        provider.value = secureAi.getProvider()
        provider.setOnPreferenceChangeListener { _, value ->
            val selected = value as String
            secureAi.setProvider(selected)
            if (selected == "gemini" && (secureAi.getModel().isBlank() || secureAi.getModel().startsWith("gpt-"))) {
                secureAi.setModel("gemini-2.5-flash")
            } else if (selected == "openai" && secureAi.getModel().startsWith("gemini")) {
                secureAi.setModel("gpt-4o-mini")
            }
            needsRestart = true
            true
        }
        val mode = findPreference<ListPreference>("ai_mode")!!
        mode.value = secureAi.getMode()
        mode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance())
        mode.setOnPreferenceChangeListener { _, value ->
            secureAi.setMode(value as String)
            needsRestart = true
            true
        }

        val geminiKey = findPreference<EditTextPreference>("ai_gemini_key")!!
        val openAiKey = findPreference<EditTextPreference>("ai_openai_key")!!
        val baseUrl = findPreference<EditTextPreference>("ai_base_url")!!
        val model = findPreference<EditTextPreference>("ai_model")!!
        geminiKey.text = ""
        openAiKey.text = ""
        geminiKey.summary = if (secureAi.get("gemini").isBlank()) getString(R.string.ai_key_summary) else "•••••••• (configured)"
        openAiKey.summary = if (secureAi.get("openai").isBlank()) getString(R.string.ai_key_summary) else "•••••••• (configured)"
        baseUrl.text = secureAi.getBaseUrl()
        model.text = secureAi.getModel()
        geminiKey.setOnPreferenceChangeListener { _, value ->
            secureAi.set("gemini", value as String)
            if (value.isNotBlank()) {
                secureAi.setProvider("gemini")
                secureAi.setMode("cloud")
                if (secureAi.getModel().isBlank() || secureAi.getModel().startsWith("gpt-")) secureAi.setModel("gemini-2.5-flash")
                needsRestart = true
            }
            geminiKey.summary = "•••••••• (configured)"
            false
        }
        openAiKey.setOnPreferenceChangeListener { _, value ->
            secureAi.set("openai", value as String)
            if (value.isNotBlank()) {
                secureAi.setProvider("openai")
                secureAi.setMode("cloud")
                needsRestart = true
            }
            openAiKey.summary = "•••••••• (configured)"
            false
        }
        baseUrl.setOnPreferenceChangeListener { _, value -> secureAi.setBaseUrl(value as String); true }
        model.setOnPreferenceChangeListener { _, value -> secureAi.setModel(value as String); true }
        findPreference<Preference>("ai_clear_keys")!!.setOnPreferenceClickListener {
            secureAi.remove("gemini"); secureAi.remove("openai")
            geminiKey.summary = getString(R.string.ai_key_summary)
            openAiKey.summary = getString(R.string.ai_key_summary)
            Toast.makeText(requireContext(), getString(R.string.ai_keys_cleared), Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun populateLanguages(languages: ListPreference) {
        val languageValues = resources.getStringArray(R.array.locales)
            .map { locale -> settingsService.getCountryName(locale) }
        languages.entries = languageValues.toTypedArray()
    }

    private fun onAllowRootUpdated(newValue: Boolean): Boolean {
        val hasSU = rootDetectionService.detectSU()
        if (newValue && !hasSU) {
            Toast.makeText(activity, getText(R.string.warning_root_check), Toast.LENGTH_LONG).show()
        }
        prefs.edit().putBoolean("allow_root", newValue).apply()
        needsRestart = true
        return true
    }

    private fun onThemeUpdated(newValue: String): Boolean {
        prefs.edit().putString("theme", newValue).apply()
        settingsService.setTheme(newValue)
        return true
    }

    private fun onHidePrivateUpdated(newValue: Boolean): Boolean {
        prefs.edit().putBoolean("hide_hide_private", newValue).apply()
        needsRestart = true
        return true
    }

    private fun onLanguageUpdated(newValue: String): Boolean {
        prefs.edit().putString("language", newValue).apply()

        settingsService.applyLocaleConfiguration(requireActivity().baseContext)
        needsRestart = true
        return true
    }
}
