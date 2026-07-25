package com.example.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Being the default SMS app is what allows the inbox to be complete: only that
 * app receives `SMS_DELIVER`, and only that app may write to the SMS database.
 * Without the role the app still reads and sends, but received messages are
 * stored by whichever app holds it.
 */
object DefaultSmsManager {

    fun isDefaultSmsApp(context: Context): Boolean = try {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    } catch (e: Exception) {
        false
    }

    /** Intent asking the user to make this app the default SMS app. */
    @Suppress("DEPRECATION")
    fun requestIntent(context: Context): Intent? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            ) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            } else {
                null
            }
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    } catch (e: Exception) {
        null
    }
}
