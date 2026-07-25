package com.example.ui.util

import androidx.compose.ui.graphics.Color
import com.example.data.model.Operator

data class OperatorTheme(
    val mainColor: Color,
    val accentColor: Color,
    val containerColor: Color,
    val unselectedColor: Color
)

object OperatorColorUtils {

    private val fallback = OperatorTheme(
        mainColor = Color(0xFF607D8B),
        accentColor = Color.White,
        containerColor = Color(0xFF455A64),
        unselectedColor = Color(0xB3FFFFFF)
    )

    /** Parses `#RRGGBB` / `#AARRGGBB`, falling back to [default] on anything else. */
    fun parse(hex: String, default: Color): Color {
        val value = hex.trim()
        if (!value.startsWith("#") || (value.length != 7 && value.length != 9)) return default
        return try {
            val parsed = value.substring(1).toLong(16)
            if (value.length == 7) Color(parsed or 0xFF000000L) else Color(parsed)
        } catch (e: NumberFormatException) {
            default
        }
    }

    /** Brand colours of an operator, taken from its editable configuration. */
    fun themeOf(operator: Operator?): OperatorTheme {
        if (operator == null) return fallback
        val main = parse(operator.colorHex, fallback.mainColor)
        val accent = parse(operator.accentHex, Color.White)
        return OperatorTheme(
            mainColor = main,
            accentColor = accent,
            containerColor = main.darken(),
            unselectedColor = Color(0xB3FFFFFF)
        )
    }

    fun colorOf(operator: Operator?): Color = themeOf(operator).mainColor

    private fun Color.darken(factor: Float = 0.75f): Color =
        Color(red * factor, green * factor, blue * factor, alpha)

    // Kept for the screens that only know the name of an operator.
    fun getOperatorColor(name: String): Color = when (name.lowercase()) {
        "yas" -> Color(0xFF063970)
        "orange" -> Color(0xFFF57C00)
        "airtel" -> Color(0xFFD32F2F)
        else -> fallback.mainColor
    }

    fun getOperatorTheme(name: String): OperatorTheme = when (name.lowercase()) {
        "yas" -> OperatorTheme(Color(0xFF063970), Color(0xFFFFD700), Color(0xFF052C56), Color(0xB3FFFFFF))
        "orange" -> OperatorTheme(Color(0xFFF57C00), Color.White, Color(0xFFE65100), Color(0xB3FFFFFF))
        "airtel" -> OperatorTheme(Color(0xFFD32F2F), Color.White, Color(0xFFB71C1C), Color(0xB3FFFFFF))
        else -> fallback
    }
}
