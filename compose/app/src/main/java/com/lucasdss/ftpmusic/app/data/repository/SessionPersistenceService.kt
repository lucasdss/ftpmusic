package com.lucasdss.ftpmusic.app.data.repository

import com.lucasdss.ftpmusic.app.data.db.SessionStateDao
import com.lucasdss.ftpmusic.app.data.db.SessionStateEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the last play queue and playback position across app restarts.
 * Throttles position saves to every 5s.
 */
@Singleton
class SessionPersistenceService @Inject constructor(private val dao: SessionStateDao) {
    private val mutex = Mutex()
    private var lastSaveMs: Long = 0
    private var pending: SessionStateEntity? = null

    suspend fun save(queueItems: List<Map<String, Any>>, currentIndex: Int, positionMs: Long) {
        val json =
            JSONArray(queueItems.map { JSONObject(it.mapValues { (_, v) -> v?.toString() ?: "" }) }).toString() ?: "[]"
        pending = SessionStateEntity(
            id = 1,
            queueJson = json,
            currentIndex = currentIndex,
            positionMs = positionMs,
            updatedAt = System.currentTimeMillis(),
        )
        val now = System.currentTimeMillis()
        if (now - lastSaveMs >= 5000) {
            mutex.withLock { flush() }
        }
    }

    suspend fun restore(): SessionStateEntity? {
        val state = dao.get() ?: return null
        return if (state.queueJson.isNotEmpty()) state else null
    }

    suspend fun clear() = dao.clear()

    suspend fun flush() {
        pending?.let { dao.upsert(it) }
        lastSaveMs = System.currentTimeMillis()
        pending = null
    }
}
