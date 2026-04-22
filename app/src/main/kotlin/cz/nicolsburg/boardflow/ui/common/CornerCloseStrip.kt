package cz.nicolsburg.boardflow.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CornerCloseStrip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 118.dp,
    height: Dp = 70.dp,
    clipShape: Shape = MaterialTheme.shapes.large
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    val fillAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.38f else 0.30f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "closeStripFillAlpha"
    )
    val separatorAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.34f else 0.22f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "closeStripSeparatorAlpha"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.92f,
        animationSpec = tween(140, easing = FastOutSlowInEasing),
        label = "closeStripIconAlpha"
    )

    val geometry = remember(width, height, density) {
        with(density) { cornerStripGeometry(widthPx = width.toPx(), heightPx = height.toPx()) }
    }

    val iconSize = 18.dp
    val iconSizePx = with(density) { iconSize.toPx() }
    val iconOffset = remember(geometry, iconSizePx) {
        IntOffset(
            x = (geometry.iconCenter.x - iconSizePx / 2f).roundToInt(),
            y = (geometry.iconCenter.y - iconSizePx / 2f).roundToInt()
        )
    }

    val fillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = fillAlpha)
    val separatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = separatorAlpha)
    val iconColor = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha)

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(clipShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val panelPath = Path().apply {
                moveTo(geometry.topLeft.x, geometry.topLeft.y)
                lineTo(geometry.topRight.x, geometry.topRight.y)
                lineTo(geometry.rightCut.x, geometry.rightCut.y)
                close()
            }

            drawPath(
                path = panelPath,
                color = fillColor
            )

            drawLine(
                color = separatorColor,
                start = geometry.topLeft,
                end = geometry.rightCut,
                strokeWidth = 1.5.dp.toPx()
            )
        }

        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = iconColor,
            modifier = Modifier
                .offset { iconOffset }
                .size(iconSize)
        )
    }
}

private data class CornerStripGeometry(
    val topLeft: Offset,
    val topRight: Offset,
    val rightCut: Offset,
    val iconCenter: Offset
)

private fun cornerStripGeometry(
    widthPx: Float,
    heightPx: Float
): CornerStripGeometry {
    val topInset = widthPx * 0.50f
    val rightCutY = heightPx * 0.84f

    val topLeft = Offset(topInset, 0f)
    val topRight = Offset(widthPx, 0f)
    val rightCut = Offset(widthPx, rightCutY)

    val iconCenter = Offset(
        x = widthPx * 0.84f,
        y = heightPx * 0.28f
    )

    return CornerStripGeometry(
        topLeft = topLeft,
        topRight = topRight,
        rightCut = rightCut,
        iconCenter = iconCenter
    )
}
