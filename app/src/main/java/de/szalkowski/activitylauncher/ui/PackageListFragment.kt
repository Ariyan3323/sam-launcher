package de.szalkowski.activitylauncher.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.szalkowski.activitylauncher.AssistantActivity
import de.szalkowski.activitylauncher.BuildConfig
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.databinding.FragmentPackageListBinding
import de.szalkowski.activitylauncher.services.ViewIntentParserService
import javax.inject.Inject

@AndroidEntryPoint
class PackageListFragment : Fragment() {
    @Inject
    internal lateinit var packageListAdapter: PackageListAdapter

    @Inject
    internal lateinit var viewIntentParserService: ViewIntentParserService

    private var _binding: FragmentPackageListBinding? = null
    private var pulseAnimator: ObjectAnimator? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPackageListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val actionBar = activity as? ActionBarSearch
        packageListAdapter.filter = actionBar?.actionBarSearchText.orEmpty()
        actionBar?.onActionBarSearchListener = { search ->
            packageListAdapter.filter = search
        }

        packageListAdapter.onItemClick = {
            runCatching {
                val action = PackageListFragmentDirections.actionSelectPackage(it.packageName)
                findNavController().navigate(action)
            }.onFailure { Log.e("Navigation", "Error while navigating from PackageListFragment") }

        }
        binding.rvPackages.adapter = packageListAdapter
        binding.rvPackages.isNestedScrollingEnabled = false

        binding.agentStatus.setText(
            if (BuildConfig.GEMINI_API_KEY.isNotBlank()) R.string.launcher_agent_online
            else R.string.launcher_agent_local
        )
        binding.agentDemoButton.setOnClickListener {
            openAssistant(getString(R.string.launcher_agent_demo_command))
        }
        binding.agentAskButton.setOnClickListener {
            openAssistant(binding.agentCommandInput.text?.toString().orEmpty())
        }
        binding.agentBatteryButton.setOnClickListener { openAssistant("باتری را بررسی کن") }
        binding.agentSettingsButton.setOnClickListener { openAssistant("تنظیمات را باز کن") }
        binding.appListButton.setOnClickListener {
            packageListAdapter.filter = ""
            actionBar?.actionBarSearchText = ""
            binding.rvPackages.smoothScrollToPosition(0)
        }
        pulseAnimator = ObjectAnimator.ofFloat(binding.agentPulse, View.ALPHA, 0.45f, 1f).apply {
            duration = 1100L
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        runCatching {
            val intent = activity?.intent ?: return
            val packageName = viewIntentParserService.packageFromIntent(intent) ?: return
            val action = PackageListFragmentDirections.actionSelectPackage(packageName)
            findNavController().navigate(action)
        }.onFailure {
            Toast.makeText(
                requireContext(),
                getString(R.string.error_invalid_activity_link),
                Toast.LENGTH_LONG
            )
                .show()
        }

    }

    private fun openAssistant(command: String) {
        val intent = Intent(requireContext(), AssistantActivity::class.java)
        if (command.isNotBlank()) intent.putExtra(AssistantActivity.EXTRA_INITIAL_COMMAND, command.trim())
        startActivity(intent)
    }

    override fun onDestroyView() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
