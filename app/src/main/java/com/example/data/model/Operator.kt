package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A mobile operator, fully user editable.
 *
 * Every dialable string is stored as a template so the user can adapt it when an
 * operator changes its short codes without waiting for an app update.
 *
 * Supported placeholders:
 *  - {CODE}   the recharge code read by the scanner
 *  - {NUMBER} the destination phone number
 *  - {AMOUNT} the amount / plan identifier
 *  - {PIN}    the secret code of the operator wallet, when required
 */
@Entity(tableName = "operators")
data class Operator(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    /** Recharge template, e.g. `#321*{CODE}#`. */
    val template: String,
    val isDefault: Boolean = false,
    /** Number of digits printed on the recharge ticket of this operator. */
    val codeLength: Int = 14,
    /** Balance enquiry code, e.g. `#321#`. */
    val balanceTemplate: String = "",
    /** Credit transfer template, e.g. `#321*{NUMBER}*{AMOUNT}#`. */
    val transferTemplate: String = "",
    /** Plan (forfait) sharing template, e.g. `#322*{NUMBER}*{AMOUNT}#`. */
    val shareTemplate: String = "",
    /** Customer service number, dialled from the operator sheet. */
    val serviceNumber: String = "",
    /** Comma separated national prefixes, e.g. `034,038`. Used to guess the operator of a number. */
    val prefixes: String = "",
    /** Comma separated aliases used to recognise the operator on a SIM label or on a scanned ticket. */
    val keywords: String = "",
    /** Brand colour, `#RRGGBB`. */
    val colorHex: String = "#607D8B",
    /** Colour used for text drawn on top of [colorHex]. */
    val accentHex: String = "#FFFFFF",
    /**
     * Some operators (Orange Madagascar) expect the code to be dialled as a plain
     * number, without the leading `*` and the trailing `#`.
     */
    val stripUssdSymbols: Boolean = false,
    /**
     * `false` while the transfer / share templates are still the ones shipped with
     * the app. The UI asks for an explicit confirmation until the user validates them.
     */
    val templatesVerified: Boolean = false,
    val sortOrder: Int = 0
) {
    val prefixList: List<String>
        get() = prefixes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val keywordList: List<String>
        get() = (keywords.split(",").map { it.trim() } + name)
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
}
