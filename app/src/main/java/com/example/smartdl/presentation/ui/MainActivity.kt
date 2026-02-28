package com.example.smartdl.presentation.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdl.SmartDlApp
import com.example.smartdl.data.source.AppPreferences
import com.example.smartdl.databinding.ActivityMainBinding
import com.example.smartdl.domain.model.DownloadStatus
import com.example.smartdl.domain.usecase.AddHistoryUseCase
import com.example.smartdl.domain.usecase.EnqueueDownloadUseCase
import com.example.smartdl.domain.usecase.UpdateDomainHeadersUseCase
import com.example.smartdl.domain.util.hostOrNull
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

    private val enqueueDownloadUseCase: EnqueueDownloadUseCase by lazy {
        (application as SmartDlApp).container.enqueueDownload
    }

    private val updateDomainHeadersUseCase: UpdateDomainHeadersUseCase by lazy {
        (application as SmartDlApp).container.updateDomainHeaders
    }

    private val addHistoryUseCase: AddHistoryUseCase by lazy {
        (application as SmartDlApp).container.addHistory
    }

    private val prefs: AppPreferences by lazy { AppPreferences(this) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        maybeRequestNotificationPermission()

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        }

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

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.backButton.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.forwardButton.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.goButton.setOnClickListener { loadUrlFromBar() }

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                updateDomainDefaults(url)
                if (isLikelyDownload(url)) {
                    enqueueFromWebView(url, request.requestHeaders)
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                val url = request.url.toString()
                val headers = request.requestHeaders
                updateDomainDefaults(url, headers)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.addressInput.setText(url)
                updateDomainDefaults(url)
                lifecycleScope.launch {
                    addHistoryUseCase(url, view.title)
                }
            }
        }

        binding.webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val referer = binding.webView.url
            val cookies = CookieManager.getInstance().getCookie(url)
            lifecycleScope.launch {
                enqueueDownloadUseCase(
                    url = url,
                    fileName = fileName,
                    mimeType = mimeType,
                    userAgent = userAgent,
                    referer = referer,
                    cookies = cookies
                )
                ForegroundDownloadService.start(this@MainActivity, maxParallel = prefs.getMaxParallel())
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.statusText.text = state.status
                    adapter.submitList(state.downloads)
                }
            }
        }

        ForegroundDownloadService.start(this, maxParallel = prefs.getMaxParallel())

        if (savedInstanceState == null) {
            binding.webView.loadUrl("https://www.google.com")
        }
    }

    private fun loadUrlFromBar() {
        val text = binding.addressInput.text?.toString().orEmpty().trim()
        val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        binding.webView.loadUrl(url)
    }

    private fun updateDomainDefaults(url: String, headers: Map<String, String>? = null) {
        val domain = url.hostOrNull() ?: return
        val ua = headers?.get("User-Agent") ?: binding.webView.settings.userAgentString
        val ref = headers?.get("Referer") ?: url
        val cookies = CookieManager.getInstance().getCookie(url)
        lifecycleScope.launch {
            updateDomainHeadersUseCase(domain, ua, ref, cookies)
        }
    }

    private fun enqueueFromWebView(url: String, headers: Map<String, String>) {
        val userAgent = headers["User-Agent"] ?: binding.webView.settings.userAgentString
        val referer = headers["Referer"] ?: binding.webView.url
        val cookies = CookieManager.getInstance().getCookie(url)
        lifecycleScope.launch {
            enqueueDownloadUseCase(
                url = url,
                fileName = null,
                mimeType = null,
                userAgent = userAgent,
                referer = referer,
                cookies = cookies
            )
            ForegroundDownloadService.start(this@MainActivity, maxParallel = prefs.getMaxParallel())
        }
    }

    private fun isLikelyDownload(url: String): Boolean {
        val lower = url.lowercase()
        val exts = listOf(".apk", ".zip", ".mp4", ".mp3", ".pdf", ".mkv", ".jpg", ".png")
        return exts.any { lower.endsWith(it) }
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
