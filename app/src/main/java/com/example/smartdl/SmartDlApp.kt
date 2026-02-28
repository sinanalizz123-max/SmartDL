package com.example.smartdl

import android.app.Application
import androidx.room.Room
import com.example.smartdl.data.db.AppDatabase
import com.example.smartdl.data.repository.DownloadRepositoryImpl
import com.example.smartdl.data.source.CookieExporter
import com.example.smartdl.data.source.MediaStoreWriter
import com.example.smartdl.data.source.OrphanCache
import com.example.smartdl.data.source.ParallelDownloader
import com.example.smartdl.data.source.TempFileManager
import com.example.smartdl.data.source.YtDlpRunner
import com.example.smartdl.domain.repository.DownloadRepository
import com.example.smartdl.domain.usecase.AddHistoryUseCase
import com.example.smartdl.domain.usecase.CancelDownloadUseCase
import com.example.smartdl.domain.usecase.ClearIncompleteCacheUseCase
import com.example.smartdl.domain.usecase.EnqueueDownloadUseCase
import com.example.smartdl.domain.usecase.GetOrphanTempFilesUseCase
import com.example.smartdl.domain.usecase.ObserveDownloadsUseCase
import com.example.smartdl.domain.usecase.ObserveHistoryUseCase
import com.example.smartdl.domain.usecase.PauseDownloadUseCase
import com.example.smartdl.domain.usecase.ResumeDownloadUseCase
import com.example.smartdl.domain.usecase.ResumeIncompleteUseCase
import com.example.smartdl.domain.usecase.StartQueueUseCase
import com.example.smartdl.domain.usecase.StopQueueUseCase
import com.example.smartdl.domain.usecase.UpdateDomainHeadersUseCase
import okhttp3.OkHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SmartDlApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "smartdl.db"
        ).fallbackToDestructiveMigration()
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val downloader = ParallelDownloader(client, database.downloadDao())
        val ytDlpRunner = YtDlpRunner(applicationContext)
        val tempFileManager = TempFileManager(applicationContext)
        val mediaStoreWriter = MediaStoreWriter(applicationContext)
        val cookieExporter = CookieExporter()

        val repository: DownloadRepository = DownloadRepositoryImpl(
            context = applicationContext,
            dao = database.downloadDao(),
            downloader = downloader,
            ytDlpRunner = ytDlpRunner,
            tempFileManager = tempFileManager,
            mediaStoreWriter = mediaStoreWriter,
            cookieExporter = cookieExporter
        )

        container = AppContainer(
            repository = repository,
            observeDownloads = ObserveDownloadsUseCase(repository),
            observeHistory = ObserveHistoryUseCase(repository),
            enqueueDownload = EnqueueDownloadUseCase(repository),
            updateDomainHeaders = UpdateDomainHeadersUseCase(repository),
            addHistory = AddHistoryUseCase(repository),
            pauseDownload = PauseDownloadUseCase(repository),
            resumeDownload = ResumeDownloadUseCase(repository),
            cancelDownload = CancelDownloadUseCase(repository),
            startQueue = StartQueueUseCase(repository),
            stopQueue = StopQueueUseCase(repository),
            resumeIncomplete = ResumeIncompleteUseCase(repository),
            getOrphanTempFiles = GetOrphanTempFilesUseCase(repository),
            clearIncompleteCache = ClearIncompleteCacheUseCase(repository)
        )

        val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        startupScope.launch {
            OrphanCache.lastScan = repository.getOrphanTempFiles()
        }
    }

    class AppContainer(
        val repository: DownloadRepository,
        val observeDownloads: ObserveDownloadsUseCase,
        val observeHistory: ObserveHistoryUseCase,
        val enqueueDownload: EnqueueDownloadUseCase,
        val updateDomainHeaders: UpdateDomainHeadersUseCase,
        val addHistory: AddHistoryUseCase,
        val pauseDownload: PauseDownloadUseCase,
        val resumeDownload: ResumeDownloadUseCase,
        val cancelDownload: CancelDownloadUseCase,
        val startQueue: StartQueueUseCase,
        val stopQueue: StopQueueUseCase,
        val resumeIncomplete: ResumeIncompleteUseCase,
        val getOrphanTempFiles: GetOrphanTempFilesUseCase,
        val clearIncompleteCache: ClearIncompleteCacheUseCase
    )
}
