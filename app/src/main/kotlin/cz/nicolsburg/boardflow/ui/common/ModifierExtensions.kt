package cz.nicolsburg.boardflow.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

fun Modifier.clickableRow(
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: () -> Unit,
): Modifier = this
    .clip(shape)
    .clickable(onClick = onClick)
