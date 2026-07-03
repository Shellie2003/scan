package com.example.ui.util

import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri

data class SimCardInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val carrierName: String,
    val displayName: String
)

object SimManager {
    fun getActiveSims(context: Context): List<SimCardInfo> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return emptyList()
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        try {
            val list = subscriptionManager.activeSubscriptionInfoList ?: return emptyList()
            return list.map { info ->
                SimCardInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    carrierName = info.carrierName?.toString() ?: "",
                    displayName = info.displayName?.toString() ?: ""
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Checks if a SIM card matching the operator's name is present.
     * Fallback to true if read phone state permission is not granted yet.
     */
    fun isSimPresentForOperator(context: Context, operatorName: String): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return true 
        }

        val activeSims = getActiveSims(context)
        if (activeSims.isEmpty()) {
            return false
        }

        val query = operatorName.lowercase()
        return activeSims.any { sim ->
            val simCarrier = sim.carrierName.lowercase()
            val simDisplay = sim.displayName.lowercase()
            
            if (query == "yas") {
                simCarrier.contains("yas") || simCarrier.contains("telma") ||
                simDisplay.contains("yas") || simDisplay.contains("telma")
            } else {
                simCarrier.contains(query) || simDisplay.contains(query)
            }
        }
    }

    /**
     * Finds the best matching SIM card for the operator, or null.
     */
    fun getSimForOperator(context: Context, operatorName: String): SimCardInfo? {
        val activeSims = getActiveSims(context)
        val query = operatorName.lowercase()
        
        return activeSims.find { sim ->
            val simCarrier = sim.carrierName.lowercase()
            val simDisplay = sim.displayName.lowercase()
            if (query == "yas") {
                simCarrier.contains("yas") || simCarrier.contains("telma") ||
                simDisplay.contains("yas") || simDisplay.contains("telma")
            } else {
                simCarrier.contains(query) || simDisplay.contains(query)
            }
        }
    }

    /**
     * Creates a call intent directed to the specific SIM if found.
     */
    fun createCallIntent(context: Context, ussdCode: String, operatorName: String?): Intent {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:" + Uri.encode(ussdCode)))

        if (operatorName != null) {
            val sim = getSimForOperator(context, operatorName)
            if (sim != null) {
                intent.putExtra("simSlot", sim.slotIndex)
                intent.putExtra("com.android.phone.extra.slot", sim.slotIndex)
                intent.putExtra("com.android.phone.dialing_slot", sim.slotIndex)
                intent.putExtra("subscription", sim.subscriptionId)
                intent.putExtra("phone", sim.slotIndex)
                intent.putExtra("simId", sim.subscriptionId)
                intent.putExtra("simSlotIndex", sim.slotIndex)
            }
        }
        return intent
    }
}
