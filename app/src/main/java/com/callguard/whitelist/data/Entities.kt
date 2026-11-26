package com.callguard.whitelist.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Cor ARGB. */
    val color: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Registro da whitelist. A coluna [normalized] é a única usada pelo motor de bloqueio;
 * ela tem índice único, o que também impede duplicados.
 */
@Entity(
    tableName = "whitelist",
    indices = [
        Index(value = ["normalized"], unique = true),
        Index(value = ["categoryId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class WhitelistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Número como o usuário digitou/importou (apenas para exibição). */
    val number: String,
    /** Chave canônica (dígitos E.164). */
    val normalized: String,
    val name: String? = null,
    val categoryId: Long? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class WhitelistWithCategory(
    @Embedded val entry: WhitelistEntry,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "categoryColor") val categoryColor: Int?,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "entryCount") val entryCount: Int,
)

@Entity(tableName = "call_events", indices = [Index(value = ["timestamp"])])
data class CallEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String?,
    val normalized: String?,
    val timestamp: Long,
    val allowed: Boolean,
    /** Nome de [com.callguard.whitelist.core.DecisionReason]. */
    val reason: String,
)

/** Número bloqueado: sempre rejeitado (exceto se estiver na whitelist). */
@Entity(tableName = "blocked")
data class BlockedNumber(
    @PrimaryKey val normalized: String,
    val number: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** true = bloqueado automaticamente pelo app; false = bloqueado por você. */
    val auto: Boolean = false,
)
