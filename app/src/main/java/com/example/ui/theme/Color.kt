package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colours of SCAN.
 *
 * The application sits between three operators that all own a strong colour —
 * Yas navy, Orange orange, Airtel red. So the application itself must never
 * borrow any of them: its own colour is an indigo that belongs to no operator,
 * and the operator colour only appears inside the card, the chip or the button
 * that is about that operator.
 *
 * Two families live here:
 *  - the Material roles (primary, surface…), used through `MaterialTheme.colorScheme`;
 *  - the SCAN roles (success, warning…), used through `MaterialTheme.scanColors`,
 *    because a call button that is green and a hang-up button that is red are
 *    part of the product, not of Material.
 */

// ------------------------------------------------------------------ brand ramp

/** Indigo — the colour of the application itself. */
val Indigo10 = Color(0xFF150064)
val Indigo20 = Color(0xFF26109C)
val Indigo30 = Color(0xFF3A28B9)
val Indigo40 = Color(0xFF4A3AD0)
val Indigo80 = Color(0xFFC6C0FF)
val Indigo90 = Color(0xFFE3DFFF)

/** Amber — value, credit, money: the colour of what the user gains. */
val Amber10 = Color(0xFF2E1500)
val Amber20 = Color(0xFF4A2800)
val Amber30 = Color(0xFF6A3C00)
val Amber40 = Color(0xFF8B5000)
val Amber80 = Color(0xFFFFB877)
val Amber90 = Color(0xFFFFDCBE)

/** Teal — the network, the signal, the SIM cards. */
val Teal10 = Color(0xFF002022)
val Teal20 = Color(0xFF003739)
val Teal30 = Color(0xFF004F53)
val Teal40 = Color(0xFF00696E)
val Teal80 = Color(0xFF81D3D9)
val Teal90 = Color(0xFF9DF0F6)

// --------------------------------------------------------------- light surfaces

val LightBackground = Color(0xFFFBF8FF)
val LightOnBackground = Color(0xFF1B1B21)
val LightSurfaceVariant = Color(0xFFE4E1EC)
val LightOnSurfaceVariant = Color(0xFF47464F)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF5F2FA)
val LightSurfaceContainer = Color(0xFFEFEDF5)
val LightSurfaceContainerHigh = Color(0xFFEAE7F0)
val LightSurfaceContainerHighest = Color(0xFFE4E1EA)
val LightOutline = Color(0xFF787680)
val LightOutlineVariant = Color(0xFFC8C5D0)
val LightInverseSurface = Color(0xFF303036)
val LightInverseOnSurface = Color(0xFFF3EFF7)

// ---------------------------------------------------------------- dark surfaces

val DarkBackground = Color(0xFF131318)
val DarkOnBackground = Color(0xFFE5E1E9)
val DarkSurfaceVariant = Color(0xFF47464F)
val DarkOnSurfaceVariant = Color(0xFFC8C5D0)
val DarkSurfaceContainerLowest = Color(0xFF0E0E13)
val DarkSurfaceContainerLow = Color(0xFF1B1B21)
val DarkSurfaceContainer = Color(0xFF1F1F25)
val DarkSurfaceContainerHigh = Color(0xFF2A2930)
val DarkSurfaceContainerHighest = Color(0xFF35343B)
val DarkOutline = Color(0xFF918F9A)
val DarkOutlineVariant = Color(0xFF47464F)
val DarkInverseSurface = Color(0xFFE5E1E9)
val DarkInverseOnSurface = Color(0xFF303036)

// ------------------------------------------------------------------- semantics

val ErrorLight = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/** Green of a successful scan and of everything that starts a call. */
val SuccessLight = Color(0xFF1B6B3A)
val SuccessContainerLight = Color(0xFFA9F2C0)
val OnSuccessContainerLight = Color(0xFF00210E)
val SuccessDark = Color(0xFF8DD6A5)
val OnSuccessDark = Color(0xFF003919)
val SuccessContainerDark = Color(0xFF00522A)
val OnSuccessContainerDark = Color(0xFFA9F2C0)

/** Amber of the warnings — a code to check, a transfer template never confirmed. */
val WarningLight = Color(0xFF8A5300)
val WarningContainerLight = Color(0xFFFFDDB3)
val OnWarningContainerLight = Color(0xFF2C1600)
val WarningDark = Color(0xFFFFB951)
val OnWarningDark = Color(0xFF482900)
val WarningContainerDark = Color(0xFF683E00)
val OnWarningContainerDark = Color(0xFFFFDDB3)

/**
 * Green read over the camera preview. Bright on purpose: the preview is black
 * whatever the theme, so this green is the same in light and in dark.
 */
val CameraSuccess = Color(0xFF4CAF50)
val CameraSuccessSoft = Color(0xFF81C784)

/** Red of hanging up, on the dark background of the call screen. */
val HangUpLight = Color(0xFFC62828)
val HangUpDark = Color(0xFFE05252)
