package com.example.ui.util

import androidx.compose.ui.graphics.Color

data class OperatorTheme(
    val mainColor: Color,
    val accentColor: Color,
    val containerColor: Color,
    val unselectedColor: Color
)

object OperatorColorUtils {
    fun getOperatorColor(name: String): Color {
        return when (name) {
            "Yas" -> Color(0xFF063970)
            "Orange" -> Color(0xFFF57C00)
            "Airtel" -> Color(0xFFD32F2F)
            else -> Color.Gray
        }
    }

    fun getOperatorTheme(name: String): OperatorTheme {
        return when (name) {
            "Yas" -> OperatorTheme(
                mainColor = Color(0xFF063970), // Deep Blue
                accentColor = Color(0xFFFFD700), // Bright Yellow
                containerColor = Color(0xFF052C56), // Darker Blue
                unselectedColor = Color(0xB3FFFFFF) // Semi-transparent White
            )
            "Orange" -> OperatorTheme(
                mainColor = Color(0xFFF57C00), // Vibrant Orange
                accentColor = Color.White,
                containerColor = Color(0xFFE65100), // Darker Orange
                unselectedColor = Color(0xB3FFFFFF)
            )
            "Airtel" -> OperatorTheme(
                mainColor = Color(0xFFD32F2F), // Bold Red
                accentColor = Color.White,
                containerColor = Color(0xFFB71C1C), // Darker Red
                unselectedColor = Color(0xB3FFFFFF)
            )
            else -> OperatorTheme(
                mainColor = Color.Gray,
                accentColor = Color.White,
                containerColor = Color.DarkGray,
                unselectedColor = Color.LightGray
            )
        }
    }
}

