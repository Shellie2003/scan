package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User settings attached to a physical SIM card.
 *
 * A phone can hold several SIM cards of the *same* operator; the subscription id
 * is what makes each of them unique, and [label] is what lets the user tell them
 * apart ("Boutique", "Perso", "Maman"…).
 */
@Entity(tableName = "sim_profiles")
data class SimProfile(
    @PrimaryKey val subscriptionId: Int,
    /** Nickname chosen by the user. Falls back to the carrier label when empty. */
    val label: String = "",
    /** Operator override, used when the carrier name reported by the SIM is wrong or empty. */
    val operatorName: String = "",
    /** Phone number of this SIM, filled by the system when readable, editable otherwise. */
    val phoneNumber: String = "",
    /** When several SIMs share an operator, this one is proposed first. */
    val isPreferred: Boolean = false,
    /** Excluded from automatic routing when false. */
    val enabled: Boolean = true
)
