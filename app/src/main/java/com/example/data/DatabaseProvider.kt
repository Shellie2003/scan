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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
                                // Asked for again rather than read from the
                                // field: the field is written just after this
                                // builder returns, and a seeding skipped here
                                // means an application that opens with no
                                // operator and no plan, saying nothing.
                                runCatching { DataSeeder.seed(appContext, getDatabase(appContext)) }
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
