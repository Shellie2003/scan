package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val appContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "scan-db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // No destructive fallback on upgrade: losing the codes, the
                    // plans and the templates a user configured is worse than
                    // any bug. Every new version must bring its migration.
                    // A downgrade — an older APK installed over a newer one —
                    // has no schema to fall back to, and is the one case where
                    // starting over is the only option left.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = INSTANCE ?: return@launch
                                runCatching { DataSeeder.seed(appContext, database) }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
