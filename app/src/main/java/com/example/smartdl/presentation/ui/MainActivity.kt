package com.example.smartdl.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdl.SmartDlApp
import com.example.smartdl.databinding.ActivityMainBinding
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.presentation.service.ForegroundDownloadService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DownloadAdapter

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    private val viewModel: MainViewModel by lazy {
        val app = application as SmartDlApp
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(app.container.observeDownloads, app.container.resumeIncomplete) as T
                }
            }
        )[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeRequestNotificationPermission()

        adapter = DownloadAdapter(
            onPauseResume = { id, status ->
                if (status == DownloadStatus.DOWNLOADING) {
                    ForegroundDownloadService.pause(this, id)
                } else {
                    ForegroundDownloadService.resume(this, id)
                }
            },
            onCancel = { id ->
                ForegroundDownloadService.cancel(this, id)
            }
        )

        binding.downloadList.layoutManager = LinearLayoutManager(this)
        binding.downloadList.adapter = adapter

        binding.startButton.setOnClickListener {
            val urls = parseUrls(binding.urlInput.text?.toString().orEmpty())
            val parallelism = binding.parallelismInput.text?.toString()?.toIntOrNull() ?: 3
            if (urls.isNotEmpty()) {
                ForegroundDownloadService.start(this, urls, parallelism)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.statusText.text = state.status
                    adapter.submitList(state.downloads)
                }
            }
        }
    }

    private fun parseUrls(input: String): List<String> {
        return input
            .split('\n', ',', ';', ' ')
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
