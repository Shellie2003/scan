package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.model.Operator
import com.example.data.model.SimProfile

/**
 * A SIM card physically present in the phone.
 *
 * [subscriptionId] is the only reliable identity: a phone can hold three cards of
 * the same operator, all reporting the same carrier name.
 */
data class SimCard(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val number: String = "",
    /** Nickname given by the user in the settings. */
    val label: String = "",
    /** Operator forced by the user when the carrier name is wrong or empty. */
    val operatorOverride: String = "",
    val enabled: Boolean = true
) {
    val slotLabel: String
        get() = "SIM ${slotIndex + 1}"

    /** What the user should read in a list: their nickname first, then the carrier. */
    val title: String
        get() = when {
            label.isNotBlank() -> label
            displayName.isNotBlank() -> displayName
            carrierName.isNotBlank() -> carrierName
            else -> slotLabel
        }

    val subtitle: String
        get() = listOf(slotLabel, carrierName.takeIf { it.isNotBlank() } ?: "", number)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
}

object SimManager {

    fun hasPhoneStatePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    /** Every active SIM, ordered by slot. Empty when the permission is missing. */
    @SuppressLint("MissingPermission")
    fun getSims(context: Context): List<SimCard> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return emptyList()
        return try {
            val list = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
            list.map { info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString().orEmpty(),
                    displayName = info.displayName?.toString().orEmpty(),
                    number = readNumber(info)
                )
            }.sortedBy { it.slotIndex }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    private fun readNumber(info: android.telephony.SubscriptionInfo): String = try {
        info.number.orEmpty()
    } catch (e: Exception) {
        ""
    }

    /** Merges the SIMs reported by the system with the profiles saved by the user. */
    fun getSims(context: Context, profiles: List<SimProfile>): List<SimCard> =
        getSims(context).map { sim ->
            val profile = profiles.firstOrNull { it.subscriptionId == sim.subscriptionId }
                ?: return@map sim
            sim.copy(
                label = profile.label,
                operatorOverride = profile.operatorName,
                enabled = profile.enabled,
                number = profile.phoneNumber.ifBlank { sim.number }
            )
        }

    fun getSim(context: Context, subscriptionId: Int): SimCard? =
        getSims(context).firstOrNull { it.subscriptionId == subscriptionId }

    /** `true` when this card belongs to [operator]. */
    fun matches(sim: SimCard, operator: Operator): Boolean {
        if (sim.operatorOverride.isNotBlank()) {
            return sim.operatorOverride.equals(operator.name, ignoreCase = true)
        }
        val haystack = (sim.carrierName + " " + sim.displayName).lowercase()
        if (operator.keywordList.any { it.isNotBlank() && haystack.contains(it) }) return true
        if (sim.number.isNotBlank()) {
            val prefix = NumberUtils.prefixOf(sim.number)
            if (prefix.isNotEmpty() && operator.prefixList.contains(prefix)) return true
        }
        return false
    }

    /** Operator of a card, or `null` when nothing matches. */
    fun operatorOf(sim: SimCard, operators: List<Operator>): Operator? =
        operators.firstOrNull { matches(sim, it) }

    /** Every card of [operator]. There can be several — that is the point. */
    fun simsForOperator(sims: List<SimCard>, operator: Operator): List<SimCard> =
        sims.filter { it.enabled && matches(it, operator) }

    /**
     * The telecom account backing a SIM, used to force a call onto that exact card.
     * Returns `null` on devices that do not expose the mapping, the caller then
     * falls back to the legacy dialer extras.
     */
    @SuppressLint("MissingPermission")
    fun phoneAccountHandle(context: Context, sim: SimCard): PhoneAccountHandle? {
        if (!hasPhoneStatePermission(context)) return null
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? TelecomManager ?: return null
        return try {
            val accounts = telecomManager.callCapablePhoneAccounts
            if (accounts.isNullOrEmpty()) return null
            accounts.firstOrNull { handle ->
                handle.id == sim.subscriptionId.toString() ||
                    subscriptionIdOf(context, handle) == sim.subscriptionId
            } ?: accounts.getOrNull(sim.slotIndex)
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun subscriptionIdOf(context: Context, handle: PhoneAccountHandle): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                as? android.telephony.TelephonyManager
            ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return try {
            telephonyManager.getSubscriptionId(handle)
        } catch (e: Exception) {
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }
}
