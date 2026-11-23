package com.callguard.whitelist.data

import kotlinx.coroutines.flow.Flow

class HistoryRepository(db: AppDatabase) {
    private val dao = db.callEventDao()

    val recent: Flow<List<CallEvent>> = dao.observeRecent(MAX_EVENTS)
    val blockedCount: Flow<Int> = dao.observeBlockedCount()

    suspend fun record(event: CallEvent) {
        dao.insert(event)
        dao.prune(MAX_EVENTS)
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val MAX_EVENTS = 2_000
    }
}
