package com.example.data.model
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operators")
data class Operator(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val template: String,
    val isDefault: Boolean = false
)
