package com.example.data

import android.content.Context

/**
 * Small typed wrapper around the shared preferences used across the app.
 *
 * SIM choices are stored per operator: a phone can hold several SIM cards of the
 * same operator, so remembering "Yas" is not enough — we remember the exact
 * subscription id the user picked for that operator.
 */
object AppSettings {

    private const val PREFS = "app_prefs"

    const val NO_SUBSCRIPTION = -1

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- scanning

    fun autoValidation(context: Context): Boolean =
        prefs(context).getBoolean("auto_validation", false)

    fun setAutoValidation(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_validation", value).apply()
    }

    /** Ask before dialling a code that was never confirmed by the user. */
    fun confirmBeforeDial(context: Context): Boolean =
        prefs(context).getBoolean("confirm_before_dial", true)

    fun setConfirmBeforeDial(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("confirm_before_dial", value).apply()
    }

    // --------------------------------------------------------------------- SIM

    /** Subscription id the user picked for [operatorName], or [NO_SUBSCRIPTION]. */
    fun preferredSubscription(context: Context, operatorName: String): Int =
        prefs(context).getInt(simKey(operatorName), NO_SUBSCRIPTION)

    fun setPreferredSubscription(context: Context, operatorName: String, subscriptionId: Int) {
        prefs(context).edit().putInt(simKey(operatorName), subscriptionId).apply()
    }

    fun clearPreferredSubscription(context: Context, operatorName: String) {
        prefs(context).edit().remove(simKey(operatorName)).apply()
    }

    /** Default SIM for plain calls, when the user has not chosen one on the dialer. */
    fun defaultCallSubscription(context: Context): Int =
        prefs(context).getInt("default_call_sub", NO_SUBSCRIPTION)

    fun setDefaultCallSubscription(context: Context, subscriptionId: Int) {
        prefs(context).edit().putInt("default_call_sub", subscriptionId).apply()
    }

    /** Default SIM used to send SMS. */
    fun defaultSmsSubscription(context: Context): Int =
        prefs(context).getInt("default_sms_sub", NO_SUBSCRIPTION)

    fun setDefaultSmsSubscription(context: Context, subscriptionId: Int) {
        prefs(context).edit().putInt("default_sms_sub", subscriptionId).apply()
    }

    /**
     * When true, a call or a transfer is routed through the SIM of the same
     * operator as the destination number whenever such a SIM is available.
     */
    fun smartSimRouting(context: Context): Boolean =
        prefs(context).getBoolean("smart_sim_routing", true)

    fun setSmartSimRouting(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("smart_sim_routing", value).apply()
    }

    /** Always show the SIM chooser before an operation, even when a preference exists. */
    fun alwaysAskSim(context: Context): Boolean =
        prefs(context).getBoolean("always_ask_sim", false)

    fun setAlwaysAskSim(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("always_ask_sim", value).apply()
    }

    /**
     * Run USSD codes inside the app when the device supports it, so the user
     * never has to switch to another application to read the answer.
     */
    fun inAppUssd(context: Context): Boolean =
        prefs(context).getBoolean("in_app_ussd", true)

    fun setInAppUssd(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("in_app_ussd", value).apply()
    }

    // ---------------------------------------------------------------- messages

    /** Delay in milliseconds between two messages of a bulk send. */
    fun bulkSmsDelayMs(context: Context): Long =
        prefs(context).getLong("bulk_sms_delay", 400L)

    fun setBulkSmsDelayMs(context: Context, value: Long) {
        prefs(context).edit().putLong("bulk_sms_delay", value).apply()
    }

    // ------------------------------------------------------------------ look

    /** `system`, `light` or `dark` — see `ThemeMode`. */
    fun themeMode(context: Context): String =
        prefs(context).getString("theme_mode", "system") ?: "system"

    fun setThemeMode(context: Context, value: String) {
        prefs(context).edit().putString("theme_mode", value).apply()
    }

    /**
     * Off by default: SCAN keeps its own colours unless the user asks for the
     * palette of their wallpaper (Android 12+).
     */
    fun dynamicColors(context: Context): Boolean =
        prefs(context).getBoolean("dynamic_colors", false)

    fun setDynamicColors(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("dynamic_colors", value).apply()
    }

    // ------------------------------------------------------------------- misc

    fun dialerPromptDismissed(context: Context): Boolean =
        prefs(context).getBoolean("dialer_prompt_dismissed", false)

    fun setDialerPromptDismissed(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("dialer_prompt_dismissed", value).apply()
    }

    private fun simKey(operatorName: String) = "sim_for_" + operatorName.lowercase()
}
