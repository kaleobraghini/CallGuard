package com.callguard.whitelist.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.callguard.whitelist.core.NormalizerConfig
import com.callguard.whitelist.core.PhoneNormalizer
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data object Ok : SaveResult
    data object InvalidNumber : SaveResult
    data class Duplicate(val existing: WhitelistEntry) : SaveResult
}

data class ImportResult(val added: Int, val skipped: Int)

/** Contato vindo da agenda ou de um arquivo, antes de virar registro da whitelist. */
data class ImportCandidate(val number: String, val name: String?, val categoryName: String? = null, val notes: String? = null, val createdAt: Long? = null)

class WhitelistRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.whitelistDao()
    private val categoryDao = db.categoryDao()

    val entries: Flow<List<WhitelistWithCategory>> = dao.observeAll()
    val count: Flow<Int> = dao.observeCount()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val categoriesWithCount: Flow<List<CategoryWithCount>> = categoryDao.observeWithCount()

    private suspend fun config(): NormalizerConfig = settings.current().normalizerConfig

    suspend fun normalize(number: String): String? = PhoneNormalizer.normalize(number, config())

    suspend fun add(number: String, name: String?, categoryId: Long?, notes: String?): SaveResult {
        val key = normalize(number) ?: return SaveResult.InvalidNumber
        dao.findByNormalized(key)?.let { return SaveResult.Duplicate(it) }
        return try {
            dao.insert(
                WhitelistEntry(
                    number = number.trim(),
                    normalized = key,
                    name = name.clean(),
                    categoryId = categoryId,
                    notes = notes.clean(),
                )
            )
            SaveResult.Ok
        } catch (e: SQLiteConstraintException) {
            dao.findByNormalized(key)?.let { SaveResult.Duplicate(it) } ?: SaveResult.InvalidNumber
        }
    }

    suspend fun update(original: WhitelistEntry, number: String, name: String?, categoryId: Long?, notes: String?): SaveResult {
        val key = normalize(number) ?: return SaveResult.InvalidNumber
        val existing = dao.findByNormalized(key)
        if (existing != null && existing.id != original.id) return SaveResult.Duplicate(existing)
        return try {
            dao.update(
                original.copy(
                    number = number.trim(),
                    normalized = key,
                    name = name.clean(),
                    categoryId = categoryId,
                    notes = notes.clean(),
                )
            )
            SaveResult.Ok
        } catch (e: SQLiteConstraintException) {
            dao.findByNormalized(key)?.let { SaveResult.Duplicate(it) } ?: SaveResult.InvalidNumber
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Importa vários números de uma vez; duplicados e inválidos são ignorados. */
    suspend fun importAll(candidates: List<ImportCandidate>, defaultCategoryId: Long? = null): ImportResult {
        val cfg = config()
        val categoryIds = mutableMapOf<String, Long>()
        var skipped = 0
        val seen = HashSet<String>()
        val rows = mutableListOf<WhitelistEntry>()
        db.withTransaction {
            for (c in candidates) {
                val key = PhoneNormalizer.normalize(c.number, cfg)
                if (key == null || !seen.add(key)) { skipped++; continue }
                val catId = c.categoryName?.takeIf { it.isNotBlank() }?.let { catName ->
                    categoryIds.getOrPut(catName.lowercase()) {
                        categoryDao.findByName(catName)?.id
                            ?: categoryDao.insert(CategoryEntity(name = catName.trim(), color = DEFAULT_CATEGORY_COLORS[categoryIds.size % DEFAULT_CATEGORY_COLORS.size]))
                    }
                } ?: defaultCategoryId
                rows += WhitelistEntry(
                    number = c.number.trim(),
                    normalized = key,
                    name = c.name.clean(),
                    categoryId = catId,
                    notes = c.notes.clean(),
                    createdAt = c.createdAt ?: System.currentTimeMillis(),
                )
            }
            val ids = dao.insertAllIgnore(rows)
            skipped += ids.count { it == -1L }
        }
        return ImportResult(added = candidates.size - skipped, skipped = skipped)
    }

    /**
     * Recalcula as chaves quando o usuário muda o código do país ou o DDD padrão.
     * Se duas entradas passarem a colidir, a mais antiga mantém a chave e a outra fica como estava.
     */
    suspend fun renormalizeAll() {
        val cfg = config()
        db.withTransaction {
            for (e in dao.getAll().sortedBy { it.createdAt }) {
                val key = PhoneNormalizer.normalize(e.number, cfg) ?: continue
                if (key == e.normalized) continue
                if (dao.findByNormalized(key) != null) continue
                try { dao.update(e.copy(normalized = key)) } catch (ex: SQLiteConstraintException) { /* mantém a chave antiga */ }
            }
        }
    }

    // ---- Categorias (somente organização visual) ----

    suspend fun addCategory(name: String, color: Int): Boolean {
        val n = name.trim()
        if (n.isEmpty() || categoryDao.findByName(n) != null) return false
        categoryDao.insert(CategoryEntity(name = n, color = color))
        return true
    }

    suspend fun updateCategory(category: CategoryEntity, name: String, color: Int): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return false
        val other = categoryDao.findByName(n)
        if (other != null && other.id != category.id) return false
        categoryDao.update(category.copy(name = n, color = color))
        return true
    }

    /** Exclui a categoria; os números continuam autorizados, apenas ficam sem categoria. */
    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            dao.clearCategory(id)
            categoryDao.deleteById(id)
        }
    }

    suspend fun ensureDefaultCategories() {
        if (categoryDao.getAll().isNotEmpty()) return
        listOf("Família", "Trabalho", "Amigos", "Clientes", "Serviços", "Emergências", "Outros")
            .forEachIndexed { i, name -> categoryDao.insert(CategoryEntity(name = name, color = DEFAULT_CATEGORY_COLORS[i % DEFAULT_CATEGORY_COLORS.size])) }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        val DEFAULT_CATEGORY_COLORS: List<Int> = listOf(
            0xFF4F7CAC.toInt(), 0xFF2E9E6E.toInt(), 0xFFE0A030.toInt(), 0xFF8E5CC2.toInt(),
            0xFF3AA3B5.toInt(), 0xFFD9534F.toInt(), 0xFF7A8794.toInt(), 0xFFE07B39.toInt(),
            0xFF5B6EE1.toInt(), 0xFF6DA544.toInt(), 0xFFC2477E.toInt(), 0xFF8D6E63.toInt(),
        )
    }
}
