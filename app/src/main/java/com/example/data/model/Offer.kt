package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A plan (forfait) sold by an operator. Stored in the database so every field —
 * and above all the USSD code — can be edited, added or removed by the user.
 */
@Entity(tableName = "offers")
data class Offer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operatorName: String,
    /** "Internet", "Appel" or "SMS". */
    val category: String,
    val title: String,
    val price: String,
    val description: String,
    val validity: String,
    /** Code dialled to subscribe for oneself. */
    val ussdCode: String,
    /** Code dialled to offer the plan to someone else, using {NUMBER}. Empty when unsupported. */
    val shareCode: String = "",
    val isFavorite: Boolean = false,
    /** `true` when the row was created by the user (never touched by a defaults refresh). */
    val isCustom: Boolean = false,
    val sortOrder: Int = 0
)
