package com.example.smartdl

import android.app.Application
import androidx.room.Room
import com.example.smartdl.data.db.AppDatabase
import com.example.smartdl.data.repository.DownloadRepositoryImpl
import com.example.smartdl.data.source.ParallelDownloader
import com.example.smartdl.data.source.YtDlpRunner
import com.example.smartdl.domain.repository.DownloadRepository
import com.example.smartdl.domain.usecase.CancelDownloadUseCase
import com.example.smartdl.domain.usecase.ObserveDownloadsUseCase
import com.example.smartdl.domain.usecase.PauseDownloadUseCase
import com.example.smartdl.domain.usecase.ResumeDownloadUseCase
import com.example.smartdl.domain.usecase.ResumeIncompleteUseCase
import com.example.smartdl.domain.usecase.StartDownloadUseCase
import okhttp3.OkHttpClient
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
        ).build()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val downloader = ParallelDownloader(client, database.downloadDao())
        val ytDlpRunner = YtDlpRunner(applicationContext)

        val repository: DownloadRepository = DownloadRepositoryImpl(
            context = applicationContext,
            dao = database.downloadDao(),
            downloader = downloader,
            ytDlpRunner = ytDlpRunner
        )

        container = AppContainer(
            startDownload = StartDownloadUseCase(repository),
            pauseDownload = PauseDownloadUseCase(repository),
            resumeDownload = ResumeDownloadUseCase(repository),
            cancelDownload = CancelDownloadUseCase(repository),
            observeDownloads = ObserveDownloadsUseCase(repository),
            resumeIncomplete = ResumeIncompleteUseCase(repository),
            repository = repository
        )
    }

    class AppContainer(
        val startDownload: StartDownloadUseCase,
        val pauseDownload: PauseDownloadUseCase,
        val resumeDownload: ResumeDownloadUseCase,
        val cancelDownload: CancelDownloadUseCase,
        val observeDownloads: ObserveDownloadsUseCase,
        val resumeIncomplete: ResumeIncompleteUseCase,
        val repository: DownloadRepository
    )
}
