package de.szalkowski.activitylauncher.ui

import android.content.Intent
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
    }

    private fun navigateWithPulse(view: View, action: () -> Unit) {
        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90L).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(130L).withEndAction(action).start()
        }.start()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
