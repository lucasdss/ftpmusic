package com.lucasdss.ftpmusic.app.data.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucasdss.ftpmusic.app.data.db.CacheQueueDao
import com.lucasdss.ftpmusic.app.data.db.CacheQueueItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DownloadItem(
    val id: Int,
    val trackId: String,
    val status: String, // pending, processing, completed, failed
    val priority: Int,
)

data class DownloadUiState(
    val items: List<DownloadItem> = emptyList(),
    val pendingCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(private val cacheQueueDao: CacheQueueDao) : ViewModel() {

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            cacheQueueDao.getAllFlow().collect { entities ->
                val items = entities.map {
                    DownloadItem(it.id, it.trackId, it.status, it.priority)
                }
                _state.value = DownloadUiState(
                    items = items,
                    pendingCount = items.count { it.status == "pending" },
                    completedCount = items.count { it.status == "completed" },
                    failedCount = items.count { it.status == "failed" },
                )
            }
        }
    }

    fun cancelDownload(id: Int) {
        viewModelScope.launch {
            cacheQueueDao.updateStatus(id, "cancelled")
        }
    }

    fun retryDownload(id: Int) {
        viewModelScope.launch {
            cacheQueueDao.updateStatus(id, "pending")
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            cacheQueueDao.deleteByStatus("completed")
        }
        viewModelScope.launch {
            cacheQueueDao.deleteByStatus("cancelled")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            cacheQueueDao.deleteAll()
        }
    }
}
