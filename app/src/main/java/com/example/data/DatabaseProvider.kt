package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Operator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
                        dao?.insertOperator(Operator(name = "Orange", template = "202{CODE}"))
                        dao?.insertOperator(Operator(name = "Airtel", template = "*123*{CODE}#"))
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = INSTANCE?.operatorDao() ?: return@launch
                        try {
                            val list = dao.getAllOperators().first()
                            val orangeOp = list.find { it.name.equals("Orange", ignoreCase = true) }
                            if (orangeOp != null && orangeOp.template == "202{CODE}#") {
                                dao.updateOperator(orangeOp.copy(template = "202{CODE}"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            })
            .build()
            INSTANCE = instance
            instance
        }
    }
}
