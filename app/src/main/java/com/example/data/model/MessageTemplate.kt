package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reusable message body for the bulk SMS screen.
 * `{NAME}` is replaced by the recipient name and `{NUMBER}` by their number.
 */
@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val body: String,
    val sortOrder: Int = 0
)
