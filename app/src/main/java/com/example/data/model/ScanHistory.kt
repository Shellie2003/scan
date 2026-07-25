package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One executed operation: recharge, plan purchase, credit transfer or bulk SMS. */
@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val operatorId: Int,
    val code: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operatorName: String = "",
    /** The string actually dialled. */
    val dialedCode: String = "",
    /** Subscription id of the SIM used, or -1 when unknown. */
    val subscriptionId: Int = -1,
    /** Human readable SIM label, kept so the history stays readable if the SIM is removed. */
    val simLabel: String = "",
    /** One of [KIND_RECHARGE], [KIND_OFFER], [KIND_TRANSFER], [KIND_SMS]. */
    val kind: String = KIND_RECHARGE,
    /** Free form label: plan name, recipient number, … */
    val label: String = ""
) {
    companion object {
        const val KIND_RECHARGE = "recharge"
        const val KIND_OFFER = "offer"
        const val KIND_TRANSFER = "transfer"
        const val KIND_SMS = "sms"
    }
}
