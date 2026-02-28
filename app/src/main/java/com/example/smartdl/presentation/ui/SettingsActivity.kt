package com.example.smartdl.presentation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdl.SmartDlApp
import com.example.smartdl.data.source.AppPreferences
import com.example.smartdl.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var adapter: OrphanAdapter
    private lateinit var prefs: AppPreferences

    private val viewModel: SettingsViewModel by lazy {
        val app = application as SmartDlApp
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsViewModel(
                        app.container.getOrphanTempFiles,
                        app.container.clearIncompleteCache
                    ) as T
                }
            }
        )[SettingsViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        binding.maxParallelInput.setText(prefs.getMaxParallel().toString())

        binding.saveParallelButton.setOnClickListener {
            val value = binding.maxParallelInput.text?.toString()?.toIntOrNull() ?: 3
            prefs.setMaxParallel(value)
        }

        adapter = OrphanAdapter()
        binding.orphanList.layoutManager = LinearLayoutManager(this)
        binding.orphanList.adapter = adapter

        binding.clearCacheButton.setOnClickListener {
            viewModel.clearCache()
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.orphanCountText.text = "Orphan temp files: ${state.orphans.size}"
                adapter.submitList(state.orphans)
            }
        }
    }
}
