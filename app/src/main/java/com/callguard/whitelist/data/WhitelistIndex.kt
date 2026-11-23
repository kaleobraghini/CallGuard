package com.callguard.whitelist.data

import androidx.room.InvalidationTracker
import com.callguard.whitelist.core.WhitelistLookup
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Índice em memória (HashSet, busca O(1)) apenas com as chaves numéricas da whitelist.
 *
 * - O banco Room é a fonte da verdade; o HashSet é só um cache.
 * - Se o processo foi encerrado pelo Android, o primeiro acesso recarrega o conjunto
 *   a partir do banco (uma única consulta de uma coluna indexada).
 * - Qualquer escrita na tabela "whitelist" invalida o cache automaticamente
 *   via InvalidationTracker do Room (inclusive importações e restaurações).
 */
class WhitelistIndex(private val db: AppDatabase) : WhitelistLookup {

    @Volatile
    private var cache: Set<String>? = null
    private val mutex = Mutex()

    private val observer = object : InvalidationTracker.Observer(arrayOf("whitelist")) {
        override fun onInvalidated(tables: Set<String>) {
            cache = null
        }
    }

    init {
        db.invalidationTracker.addObserver(observer)
    }

    private suspend fun numbers(): Set<String> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: HashSet(db.whitelistDao().allNormalized()).also { cache = it }
        }
    }

    override suspend fun isEmpty(): Boolean = numbers().isEmpty()

    override suspend fun containsAny(keys: Set<String>): Boolean {
        val set = numbers()
        return keys.any { it in set }
    }

    /** Pré-carrega o índice (chamado ao abrir o app) para a primeira chamada ser instantânea. */
    suspend fun warmUp() {
        runCatching { numbers() }
    }
}
