package com.callguard.whitelist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WhitelistEntry::class, CategoryEntity::class, CallEvent::class, BlockedNumber::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
    abstract fun categoryDao(): CategoryDao
    abstract fun callEventDao(): CallEventDao
    abstract fun blockedDao(): BlockedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "callguard.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Versão 2: tabela de números bloqueados (a whitelist e o histórico continuam iguais). */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `blocked` (`normalized` TEXT NOT NULL, `number` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `auto` INTEGER NOT NULL, PRIMARY KEY(`normalized`))"
                )
            }
        }
    }
}
