package de.szalkowski.activitylauncher.ui

import android.content.Intent
import android.content.Context
import android.app.ActivityManager
import android.content.IntentFilter
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
            binding.systemHealthText.text = "CPU READY   RAM ${ram.coerceAtLeast(0)}%   BAT ${battery.coerceAtLeast(0)}%"
            binding.systemHealthCard.postDelayed(this, 3000L)
        }
    }

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
        binding.avatarState.text = if (online) "ONLINE" else "LOCAL"
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
        binding.settingsButton.setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        binding.languageButton.setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        binding.systemHealthCard.post(healthTicker)
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
        binding.systemHealthCard.removeCallbacks(healthTicker)
        _binding = null
        super.onDestroyView()
    }
}
