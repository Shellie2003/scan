package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.model.Operator
import com.example.data.model.ScanHistory

@Database(entities = [Operator::class, ScanHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun operatorDao(): OperatorDao
    abstract fun scanHistoryDao(): ScanHistoryDao
}
