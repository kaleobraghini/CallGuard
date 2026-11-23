package com.callguard.whitelist.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {

    /** Usado pelo motor de bloqueio: lê só a coluna de números. */
    @Query("SELECT normalized FROM whitelist")
    suspend fun allNormalized(): List<String>

    @Query(
        """
        SELECT w.*, c.name AS categoryName, c.color AS categoryColor
        FROM whitelist w LEFT JOIN categories c ON c.id = w.categoryId
        ORDER BY w.createdAt DESC
        """
    )
    fun observeAll(): Flow<List<WhitelistWithCategory>>

    @Query("SELECT COUNT(*) FROM whitelist")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM whitelist")
    suspend fun getAll(): List<WhitelistEntry>

    @Query("SELECT * FROM whitelist WHERE normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): WhitelistEntry?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WhitelistEntry): Long

    /** Retorna -1 para cada registro ignorado por já existir. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entries: List<WhitelistEntry>): List<Long>

    @Update
    suspend fun update(entry: WhitelistEntry)

    @Query("DELETE FROM whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE whitelist SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long)
}

@Dao
interface CategoryDao {

    @Query(
        """
        SELECT c.*, (SELECT COUNT(*) FROM whitelist w WHERE w.categoryId = c.id) AS entryCount
        FROM categories c ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeWithCount(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CallEventDao {

    @Insert
    suspend fun insert(event: CallEvent): Long

    @Query("SELECT * FROM call_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CallEvent>>

    @Query("SELECT COUNT(*) FROM call_events WHERE allowed = 0")
    fun observeBlockedCount(): Flow<Int>

    @Query("DELETE FROM call_events")
    suspend fun clear()

    @Query("DELETE FROM call_events WHERE id NOT IN (SELECT id FROM call_events ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}
