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

/** Why the app has no SIM card to show. */
enum class SimAvailability {
    /** Cards were found. */
    READY,

    /** The phone state permission was never granted, or was refused. */
    PERMISSION_MISSING,

    /** The permission is held but the phone reports no usable card. */
    NONE
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

    /** What to tell the user when the card list is empty. */
    fun availability(context: Context): SimAvailability = when {
        !hasPhoneStatePermission(context) -> SimAvailability.PERMISSION_MISSING
        getSims(context).isEmpty() -> SimAvailability.NONE
        else -> SimAvailability.READY
    }

    /**
     * Every active SIM, ordered by slot.
     *
     * Three ways of asking, because one is not enough: the subscription list is
     * the right question, but several manufacturers — MIUI among them — answer
     * it with nothing while still holding two cards. The phone is then asked
     * slot by slot, and finally through the telecom accounts, which is what the
     * dialer of the system itself uses.
     */
    @SuppressLint("MissingPermission")
    fun getSims(context: Context): List<SimCard> {
        if (!hasPhoneStatePermission(context)) return emptyList()
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager

        fromSubscriptionList(subscriptionManager)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        fromSlots(subscriptionManager)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        return fromPhoneAccounts(context)
    }

    @SuppressLint("MissingPermission")
    private fun fromSubscriptionList(manager: SubscriptionManager?): List<SimCard> = try {
        manager?.activeSubscriptionInfoList
            ?.map { info -> info.toSimCard() }
            ?.sortedBy { it.slotIndex }
            .orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Slot by slot: answers on phones where the whole list comes back empty. */
    @SuppressLint("MissingPermission")
    private fun fromSlots(manager: SubscriptionManager?): List<SimCard> {
        if (manager == null) return emptyList()
        val slots = try {
            manager.activeSubscriptionInfoCountMax.coerceIn(1, MAX_SLOTS)
        } catch (e: Exception) {
            MAX_SLOTS
        }
        val found = mutableListOf<SimCard>()
        for (slot in 0 until slots) {
            val info = try {
                manager.getActiveSubscriptionInfoForSimSlotIndex(slot)
            } catch (e: Exception) {
                null
            }
            if (info != null) found += info.toSimCard()
        }
        return found.distinctBy { it.subscriptionId }.sortedBy { it.slotIndex }
    }

    /**
     * Last resort: the telecom accounts that can place a call.
     *
     * Only the accounts of the telephony service of the system are kept — a
     * wifi-calling or a messaging account is not a SIM card, and a call sent to
     * one of those never rings.
     */
    @SuppressLint("MissingPermission")
    private fun fromPhoneAccounts(context: Context): List<SimCard> {
        if (!hasCallPermission(context)) return emptyList()
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? TelecomManager ?: return emptyList()
        return try {
            telecomManager.callCapablePhoneAccounts
                .orEmpty()
                .filter { isTelephonyAccount(it) }
                .mapIndexed { index, handle ->
                    val account = try {
                        telecomManager.getPhoneAccount(handle)
                    } catch (e: Exception) {
                        null
                    }
                    val subscriptionId = handle.id.toIntOrNull()
                        ?: subscriptionIdOf(context, handle)
                    SimCard(
                        subscriptionId = subscriptionId,
                        slotIndex = index,
                        carrierName = account?.label?.toString().orEmpty(),
                        displayName = account?.label?.toString().orEmpty(),
                        number = account?.address?.schemeSpecificPart.orEmpty()
                    )
                }
                .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isTelephonyAccount(handle: PhoneAccountHandle): Boolean {
        val component = handle.componentName.className.lowercase()
        return component.contains("telephony") || component.contains("telecom")
    }

    @Suppress("DEPRECATION")
    private fun android.telephony.SubscriptionInfo.toSimCard(): SimCard = SimCard(
        subscriptionId = subscriptionId,
        slotIndex = simSlotIndex,
        carrierName = carrierName?.toString().orEmpty(),
        displayName = displayName?.toString().orEmpty(),
        number = readNumber(this)
    )

    /** Phones with three card slots exist; four is a safe ceiling to probe. */
    private const val MAX_SLOTS = 4

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
     *
     * Only a positive match is returned. Guessing by slot used to pick whatever
     * account came in that position — a wifi-calling or a VoIP account among
     * them — and a call placed on the wrong account never rings anywhere. No
     * account means the system picks, which at least rings.
     */
    @SuppressLint("MissingPermission")
    fun phoneAccountHandle(context: Context, sim: SimCard): PhoneAccountHandle? {
        if (!hasPhoneStatePermission(context)) return null
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE)
                as? TelecomManager ?: return null
        return try {
            telecomManager.callCapablePhoneAccounts
                .orEmpty()
                .filter { isTelephonyAccount(it) }
                .firstOrNull { handle ->
                    handle.id == sim.subscriptionId.toString() ||
                        subscriptionIdOf(context, handle) == sim.subscriptionId
                }
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
