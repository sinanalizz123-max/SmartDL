package com.example.smartdl.presentation.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdl.databinding.ItemDownloadBinding
import com.example.smartdl.domain.model.DownloadStatus

class DownloadAdapter(
    private val onPauseResume: (Long, DownloadStatus) -> Unit,
    private val onCancel: (Long) -> Unit
) : ListAdapter<DownloadUiModel, DownloadAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<DownloadUiModel>() {
        override fun areItemsTheSame(oldItem: DownloadUiModel, newItem: DownloadUiModel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadUiModel, newItem: DownloadUiModel): Boolean {
            return oldItem == newItem
        }
    }

    class ViewHolder(val binding: ItemDownloadBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ItemDownloadBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        binding.fileNameText.text = item.fileName
        binding.percentText.text = "${item.percent}%"
        binding.speedText.text = item.speedText

        binding.progressBar.isIndeterminate = item.percent <= 0 && item.status == DownloadStatus.DOWNLOADING
        binding.progressBar.progress = item.percent.coerceIn(0, 100)

        val pauseResumeText = if (item.status == DownloadStatus.DOWNLOADING) {
            binding.root.context.getString(com.example.smartdl.R.string.pause)
        } else {
            binding.root.context.getString(com.example.smartdl.R.string.resume)
        }
        binding.pauseResumeButton.text = pauseResumeText

        val pauseResumeEnabled = item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PAUSED
        binding.pauseResumeButton.isEnabled = pauseResumeEnabled
        binding.cancelButton.isEnabled = item.status != DownloadStatus.COMPLETED

        binding.pauseResumeButton.setOnClickListener {
            onPauseResume(item.id, item.status)
        }
        binding.cancelButton.setOnClickListener {
            onCancel(item.id)
        }
    }
}
