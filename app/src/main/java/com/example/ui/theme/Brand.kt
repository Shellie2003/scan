package com.example.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mark of SCAN: a viewfinder and the line that reads it.
 *
 * It is the gesture of the application drawn in one shape — you point the phone
 * at a recharge ticket, the frame finds the code, the line reads it. Four corner
 * brackets, one amber line, nothing else, so that it still reads at the size of
 * a launcher icon or of a notification.
 *
 * The same geometry is repeated in `res/drawable/ic_scan_mark.xml` for the
 * launcher icon and the launch screen; when one changes, the other must follow.
 */
@Composable
fun ScanLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    /** Draw the indigo tile behind the mark. Off for a mark on a coloured header. */
    withTile: Boolean = true,
    /** Colour of the brackets. Defaults to white on the tile, primary without it. */
    markColor: Color? = null,
    accentColor: Color = Amber80
) {
    val gradient = MaterialTheme.scanColors.brandGradient
    val brackets = markColor ?: if (withTile) Color.White else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        if (withTile) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = gradient,
                    start = Offset.Zero,
                    end = Offset(side, side)
                ),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * 0.28f)
            )
        }
        drawScanMark(side = side, color = brackets, accent = accentColor)
    }
}

/** Corner brackets plus the reading line, drawn inside a square of [side] pixels. */
private fun DrawScope.drawScanMark(side: Float, color: Color, accent: Color) {
    val inset = side * 0.24f
    val far = side - inset
    val arm = side * 0.13f
    val stroke = Stroke(
        width = side * 0.075f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    fun bracket(cornerX: Float, cornerY: Float, towardX: Float, towardY: Float) {
        val path = Path().apply {
            moveTo(cornerX, cornerY + towardY * arm)
            lineTo(cornerX, cornerY)
            lineTo(cornerX + towardX * arm, cornerY)
        }
        drawPath(path = path, color = color, style = stroke)
    }

    bracket(inset, inset, 1f, 1f)
    bracket(far, inset, -1f, 1f)
    bracket(far, far, -1f, -1f)
    bracket(inset, far, 1f, -1f)

    drawLine(
        color = accent,
        start = Offset(inset + side * 0.04f, side / 2f),
        end = Offset(far - side * 0.04f, side / 2f),
        strokeWidth = side * 0.075f,
        cap = StrokeCap.Round
    )
}

/** SCAN, written the way it is written everywhere. */
@Composable
fun ScanWordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = "SCAN",
        style = ScanTextStyles.wordmark,
        color = color,
        modifier = modifier
    )
}

/** The mark and the name side by side, with an optional line underneath. */
@Composable
fun ScanLockup(
    modifier: Modifier = Modifier,
    tagline: String? = null,
    logoSize: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScanLogo(size = logoSize)
        Column {
            ScanWordmark(color = color)
            if (tagline != null) {
                Text(
                    text = tagline,
                    style = ScanTextStyles.tagline,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
    }
}
