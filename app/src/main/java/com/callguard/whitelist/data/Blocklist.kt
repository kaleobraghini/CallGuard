package com.callguard.whitelist.data

import androidx.room.InvalidationTracker
import com.callguard.whitelist.core.BlocklistLookup
import com.callguard.whitelist.core.PhoneNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** HashSet dos bloqueados, igual ao da whitelist: recarrega do banco sozinho quando muda. */
class BlocklistIndex(private val db: AppDatabase) : BlocklistLookup {
    @Volatile private var cache: Set<String>? = null
    private val mutex = Mutex()
    private val observer = object : InvalidationTracker.Observer(arrayOf("blocked")) {
        override fun onInvalidated(tables: Set<String>) { cache = null }
    }

    init { db.invalidationTracker.addObserver(observer) }

    override suspend fun containsAny(keys: Set<String>): Boolean {
        val set = cache ?: mutex.withLock { cache ?: HashSet(db.blockedDao().allNormalized()).also { cache = it } }
        return keys.any { it in set }
    }
}

class BlocklistRepository(private val db: AppDatabase, private val settings: SettingsRepository) {
    private val dao = db.blockedDao()
    val all: Flow<List<BlockedNumber>> = dao.observeAll()
    val count: Flow<Int> = dao.observeCount()

    /** Bloqueia. Um número da whitelist precisa sair de lá primeiro (a whitelist sempre vence). */
    suspend fun block(numberOrKey: String, auto: Boolean = false): Boolean {
        val key = PhoneNormalizer.normalize(numberOrKey, settings.current().normalizerConfig) ?: return false
        if (db.whitelistDao().findByNormalized(key) != null) return false
        dao.insert(BlockedNumber(normalized = key, number = numberOrKey, auto = auto))
        return true
    }

    suspend fun unblock(key: String) = dao.delete(key)

    suspend fun unblockNumber(number: String) {
        PhoneNormalizer.normalize(number, settings.current().normalizerConfig)?.let { dao.delete(it) }
    }
}
