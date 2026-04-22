package cz.nicolsburg.boardflow.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.unit.dp

@Composable
fun CornerCloseStrip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val stripAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.22f else 0.12f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "stripAlpha"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0.88f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "iconAlpha"
    )

    val stripShape = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(size.width * 0.58f, size.height)
        close()
    }

    Box(
        modifier = modifier
            .width(104.dp)
            .height(64.dp)
            .clip(stripShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = stripAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopEnd
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha),
            modifier = Modifier
                .offset(x = (-14).dp, y = 10.dp)
        )
    }
}