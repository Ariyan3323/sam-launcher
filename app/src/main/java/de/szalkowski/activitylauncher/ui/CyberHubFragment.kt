package de.szalkowski.activitylauncher.ui

import android.content.Intent
import android.content.Context
import android.app.ActivityManager
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import de.szalkowski.activitylauncher.AssistantActivity
import de.szalkowski.activitylauncher.BuildConfig
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.SettingsActivity
import de.szalkowski.activitylauncher.databinding.FragmentCyberHubBinding

class CyberHubFragment : Fragment() {
    private var _binding: FragmentCyberHubBinding? = null
    private val binding get() = _binding!!
    private val healthTicker = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val memory = ActivityManager.MemoryInfo()
            (requireContext().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(memory)
            val battery = requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.let { intent ->
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0) (level * 100 / scale.coerceAtLeast(1)) else -1
                } ?: -1
            val ram = if (memory.totalMem > 0) ((memory.totalMem - memory.availMem) * 100 / memory.totalMem).toInt() else -1
            val temperature = readTemperatureCelsius()
            val connectivity = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val networkOnline = connectivity?.activeNetwork?.let { network ->
                connectivity.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } == true
            binding.healthCpuValue.text = "READY"
            val ramText = if (ram >= 0) "$ram٪" else "--"
            binding.healthRamValue.text = ramText
            binding.healthTempValue.text = temperature?.let { "$it°C" } ?: "--°C"
            binding.healthNetworkValue.text = if (networkOnline) "ONLINE" else "NO SIGNAL"
            binding.healthCpuCard.postDelayed(this, 3000L)
        }
    }

    private fun readTemperatureCelsius(): Int? = runCatching {
        val raw = java.io.File("/sys/class/thermal/thermal_zone0/temp").readText().trim().toInt()
        if (raw > 1000) raw / 1000 else raw
    }.getOrNull()?.takeIf { it in -20..100 }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCyberHubBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val online = BuildConfig.GEMINI_API_KEY.isNotBlank() || BuildConfig.OPENAI_API_KEY.isNotBlank()
        binding.statusText.text = if (online) "سام // آنلاین" else "سام // حالت محلی"
        binding.statusDot.setTextColor(requireContext().getColor(if (online) R.color.cyber_green else R.color.cyber_purple))
        binding.avatarState.text = if (online) "ONLINE" else "SCIENCE"
        binding.avatarState.setTextColor(requireContext().getColor(if (online) R.color.cyber_green else R.color.cyber_purple))

        listOf(binding.nodeChat, binding.nodeApps, binding.nodeSystem, binding.nodeSettings).forEachIndexed { index, node ->
            node.alpha = 0f
            node.scaleX = 0.82f
            node.scaleY = 0.82f
            node.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(index * 90L).setDuration(360L).start()
        }
        binding.avatarFrame.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(700L).start()

        binding.nodeChat.setOnClickListener {
            navigateWithPulse(it) {
                startActivity(Intent(requireContext(), AssistantActivity::class.java))
            }
        }
        val openChat = View.OnClickListener {
            startActivity(Intent(requireContext(), AssistantActivity::class.java))
        }
        binding.avatarFrame.setOnClickListener { navigateWithPulse(it, openChat) }
        binding.avatarImage.setOnClickListener { navigateWithPulse(binding.avatarFrame, openChat) }
        binding.avatarState.setOnClickListener { navigateWithPulse(binding.avatarFrame, openChat) }
        binding.statusText.setOnClickListener(openChat)
        binding.statusDot.setOnClickListener(openChat)
        binding.hubEyebrow.setOnClickListener(openChat)
        binding.hubTitle.setOnClickListener(openChat)
        binding.hubHint.setOnClickListener(openChat)
        binding.appsNodeContent.setOnClickListener { binding.nodeApps.performClick() }
        binding.chatNodeContent.setOnClickListener { binding.nodeChat.performClick() }
        binding.settingsNodeContent.setOnClickListener { binding.nodeSettings.performClick() }
        binding.systemNodeContent.setOnClickListener { binding.nodeSystem.performClick() }
        binding.nodeApps.setOnClickListener {
            navigateWithPulse(it) { findNavController().navigate(R.id.PackageListFragment) }
        }
        binding.nodeSystem.setOnClickListener {
            navigateWithPulse(it) {
                startActivity(Intent(requireContext(), AssistantActivity::class.java).apply {
                    putExtra(AssistantActivity.EXTRA_INITIAL_COMMAND, "وضعیت دستگاه و باتری را بررسی کن")
                })
            }
        }
        binding.nodeSettings.setOnClickListener {
            navigateWithPulse(it) { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        }
        binding.optimizeButton.setOnClickListener {
            navigateWithPulse(it) {
                startActivity(Intent(requireContext(), AssistantActivity::class.java).apply {
                    putExtra(AssistantActivity.EXTRA_INITIAL_COMMAND, "برنامه‌های سنگین و کم‌استفاده را بررسی کن")
                })
            }
        }
        binding.quickGallery.setOnClickListener { launchQuickApp("com.google.android.apps.photos", "گالری") }
        binding.quickMusic.setOnClickListener { launchQuickApp("com.google.android.apps.youtube.music", "موزیک") }
        binding.quickMessages.setOnClickListener { launchQuickApp("com.google.android.apps.messaging", "پیام‌ها") }
        binding.settingsButton.setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        binding.languageButton.setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        binding.healthCpuCard.post(healthTicker)
    }

    private fun launchQuickApp(packageName: String, label: String) {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent)
        else startActivity(Intent(requireContext(), AssistantActivity::class.java).apply {
            putExtra(AssistantActivity.EXTRA_INITIAL_COMMAND, "باز کن $label")
        })
    }

    private fun navigateWithPulse(view: View, action: () -> Unit) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90L).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(130L).withEndAction(action).start()
        }.start()
    }

    private fun navigateWithPulse(view: View, listener: View.OnClickListener) {
        navigateWithPulse(view) { listener.onClick(view) }
    }

    override fun onDestroyView() {
        binding.healthCpuCard.removeCallbacks(healthTicker)
        _binding = null
        super.onDestroyView()
    }
}
