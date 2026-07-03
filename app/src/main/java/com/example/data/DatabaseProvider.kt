package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Operator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "scan-db"
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = INSTANCE?.operatorDao()
                        dao?.insertOperator(Operator(name = "Yas", template = "#321*{CODE}#"))
                        dao?.insertOperator(Operator(name = "Orange", template = "202{CODE}#"))
                        dao?.insertOperator(Operator(name = "Airtel", template = "*123*{CODE}#"))
                    }
                }
            })
            .build()
            INSTANCE = instance
            instance
        }
    }
}
