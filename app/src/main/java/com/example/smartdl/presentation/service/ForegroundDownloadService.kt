package com.example.smartdl.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.smartdl.R
import com.example.smartdl.SmartDlApp
import com.example.smartdl.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ForegroundDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        kotlinx.coroutines.runBlocking {
            (application as SmartDlApp).container.stopQueue()
        }
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause(intent)
            ACTION_RESUME -> handleResume(intent)
            ACTION_CANCEL -> handleCancel(intent)
        }
        ensureObserving()
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val maxParallel = intent.getIntExtra(EXTRA_MAX_PARALLEL, 3)
        startForeground(NOTIFICATION_ID, buildNotification("Starting downloads"))
        serviceScope.launch {
            val app = application as SmartDlApp
            app.container.startQueue(maxParallel)
        }
    }

    private fun handlePause(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        serviceScope.launch {
            val app = application as SmartDlApp
            if (id == -1L) {
                val list = app.container.repository.observeDownloads().first()
                list.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { item ->
                    app.container.pauseDownload(item.id)
                }
            } else {
                app.container.pauseDownload(id)
            }
        }
    }

    private fun handleResume(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        serviceScope.launch {
            val app = application as SmartDlApp
            if (id == -1L) {
                val list = app.container.repository.observeDownloads().first()
                list.filter { it.status == DownloadStatus.PAUSED }.forEach { item ->
                    app.container.resumeDownload(item.id)
                }
            } else {
                app.container.resumeDownload(id)
            }
        }
    }

    private fun handleCancel(intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        serviceScope.launch {
            val app = application as SmartDlApp
            if (id == -1L) {
                val list = app.container.repository.observeDownloads().first()
                list.filter { it.status != DownloadStatus.COMPLETED }.forEach { item ->
                    app.container.cancelDownload(item.id)
                }
            } else {
                app.container.cancelDownload(id)
            }
        }
    }

    private fun ensureObserving() {
        if (observeJob != null) return
        observeJob = serviceScope.launch {
            val app = application as SmartDlApp
            app.container.repository.observeDownloads().collect { list ->
                val running = list.count { it.status == DownloadStatus.DOWNLOADING }
                val queued = list.count { it.status == DownloadStatus.QUEUED }
                val paused = list.count { it.status == DownloadStatus.PAUSED }
                val totalBytes = list.filter { it.totalBytes > 0 }.sumOf { it.totalBytes }
                val downloaded = list.filter { it.totalBytes > 0 }.sumOf { it.bytesDownloaded }
                val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                val text = "Running: $running  Queued: $queued  Paused: $paused  ${percent}%"
                updateNotification(text)

                val active = list.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                if (!active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pauseIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ACTION_PAUSE
            putExtra(EXTRA_DOWNLOAD_ID, -1L)
        }
        val resumeIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ACTION_RESUME
            putExtra(EXTRA_DOWNLOAD_ID, -1L)
        }
        val cancelIntent = Intent(this, ForegroundDownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_DOWNLOAD_ID, -1L)
        }

        val pausePending = PendingIntentFactory.service(this, pauseIntent, 100)
        val resumePending = PendingIntentFactory.service(this, resumeIntent, 101)
        val cancelPending = PendingIntentFactory.service(this, cancelIntent, 102)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .addAction(0, getString(R.string.pause), pausePending)
            .addAction(0, getString(R.string.resume), resumePending)
            .addAction(0, getString(R.string.cancel), cancelPending)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "smartdl_downloads"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.example.smartdl.action.START"
        private const val ACTION_PAUSE = "com.example.smartdl.action.PAUSE"
        private const val ACTION_RESUME = "com.example.smartdl.action.RESUME"
        private const val ACTION_CANCEL = "com.example.smartdl.action.CANCEL"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val EXTRA_MAX_PARALLEL = "extra_max_parallel"

        fun start(context: Context, maxParallel: Int) {
            val intent = Intent(context, ForegroundDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MAX_PARALLEL, maxParallel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context, id: Long) {
            val intent = Intent(context, ForegroundDownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }
            context.startService(intent)
        }

        fun resume(context: Context, id: Long) {
            val intent = Intent(context, ForegroundDownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }
            context.startService(intent)
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, ForegroundDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, id)
            }
            context.startService(intent)
        }
    }
}
