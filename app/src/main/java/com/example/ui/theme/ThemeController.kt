package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.data.AppSettings

/** How the user wants the application to be drawn. */
enum class ThemeMode(val key: String) {
    /** Follow the phone. */
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Holds the appearance chosen by the user.
 *
 * Written the same way as `LanguageManager`: a piece of observable state that
 * any screen can read, so that changing the theme in the settings repaints the
 * whole application — the call screen included — without restarting it.
 */
object ThemeController {

    private val _mode = mutableStateOf(ThemeMode.SYSTEM)
    val mode: State<ThemeMode> = _mode

    private val _dynamicColors = mutableStateOf(false)

    /** Take the colours of the wallpaper instead of the SCAN palette (Android 12+). */
    val dynamicColors: State<Boolean> = _dynamicColors

    fun init(context: Context) {
        _mode.value = ThemeMode.fromKey(AppSettings.themeMode(context))
        _dynamicColors.value = AppSettings.dynamicColors(context)
    }

    fun setMode(context: Context, mode: ThemeMode) {
        _mode.value = mode
        AppSettings.setThemeMode(context, mode.key)
    }

    fun setDynamicColors(context: Context, enabled: Boolean) {
        _dynamicColors.value = enabled
        AppSettings.setDynamicColors(context, enabled)
    }

    /** Whether the dark palette applies right now. */
    @Composable
    fun isDark(): Boolean = when (_mode.value) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
