package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * SCAN is drawn with soft, generous corners: the screens are full of cards that
 * hold a code, a plan or a contact, and the rounding is what separates them
 * without a single divider line.
 */
val ScanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Named radii, for the places that cannot take a `Shapes` role. */
object ScanRadius {
    val chip = 12.dp
    val card = 16.dp
    val dialog = 20.dp
    val sheet = 28.dp
    val viewfinder = 32.dp

    /**
     * Rounded past the half of what it is applied to, so the shape comes out a
     * circle or a lozenge whatever its size. Used by the mark that confirms a
     * scan, which pulses and must stay round while it grows.
     */
    val capsule = 48.dp
}
