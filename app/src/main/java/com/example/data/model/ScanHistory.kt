package com.example.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operatorId: Int,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)
