package de.szalkowski.activitylauncher.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.services.PackageListService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

@AndroidEntryPoint
class LoadingFragment : Fragment() {
    @Inject
    internal lateinit var packageListService: Provider<PackageListService>
    private var navigationStarted = false

    @SuppressLint("RestrictedApi")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    withTimeout(15_000L) {
                        packageListService.get()
                    }
                }
            }
            result.onFailure { Log.e("LoadingFragment", "Package scan failed or timed out", it) }

            withResumed {
                if (navigationStarted) return@withResumed
                navigationStarted = true
                runCatching {
                    val action = LoadingFragmentDirections.actionLoadingFinished()
                    findNavController().navigate(action)
                }.onFailure { Log.e("LoadingFragment", "Could not open package list", it) }
            }
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }
}
