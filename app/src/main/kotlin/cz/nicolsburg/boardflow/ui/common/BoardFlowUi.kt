package cz.nicolsburg.boardflow.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.role
import kotlin.math.roundToInt

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (accented) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (accented) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * Dialog wrapper that animates content in on entry (scale + fade from 0.92).
 * Exit uses the platform default dialog dismiss animation.
 */
@Composable
fun AnimatedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        var visible by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val dismissThresholdPx = with(density) { 96.dp.toPx() }
        val offsetY = remember { Animatable(0f) }
        val settleDuration = 180
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + scaleIn(
                tween(200, easing = FastOutSlowInEasing),
                initialScale = 0.92f,
            ),
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .pointerInput(onDismissRequest) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            var pointerId = down.id

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                    ?: break

                                pointerId = change.id

                                if (!change.pressed) {
                                    break
                                }

                                val dragAmount = change.positionChange().y
                                if (dragAmount != 0f) {
                                    change.consume()
                                    val nextOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                    scope.launch {
                                        offsetY.snapTo(nextOffset)
                                    }
                                }
                            }

                            scope.launch {
                                if (offsetY.value > dismissThresholdPx) {
                                    onDismissRequest()
                                } else {
                                    offsetY.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(settleDuration, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        }
                    }
            ) {
                content()
                CornerCloseStrip(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 20.dp)
                )
            }
        }
    }
}

fun TextStyle.withTabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")

object BoardFlowActionTokens {
    val ButtonMinHeight = 48.dp
    val ButtonShape = RoundedCornerShape(16.dp)
    val ButtonContentPadding = ButtonDefaults.ContentPadding
    val InlineActionContentPadding = ButtonDefaults.TextButtonContentPadding
    val IconButtonSize = 40.dp
    val IconSize = 20.dp
    val CompactIconSize = 18.dp
    val IconTextSpacing = 8.dp
}

enum class BoardFlowConfirmationKind {
    POSITIVE,
    NEUTRAL,
    DESTRUCTIVE
}

private object BoardFlowConfirmationTokens {
    val MaxWidth = 420.dp
    val Shape = RoundedCornerShape(24.dp)
    val OuterPadding = 24.dp
    val ContentPadding = 24.dp
    val IconContainerSize = 36.dp
    val IconSize = 18.dp
    val HeaderSpacing = 14.dp
    val MessageSpacing = 16.dp
    val ActionSpacing = 10.dp
}

@Composable
fun BoardFlowConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    kind: BoardFlowConfirmationKind = BoardFlowConfirmationKind.NEUTRAL,
    icon: ImageVector? = null,
    dismissOnOutsideTap: Boolean = true
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnOutsideTap
        )
    ) {
        Card(
            modifier = modifier
                .padding(BoardFlowConfirmationTokens.OuterPadding)
                .fillMaxWidth()
                .widthIn(max = BoardFlowConfirmationTokens.MaxWidth),
            shape = BoardFlowConfirmationTokens.Shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            border = BorderStroke(
                1.dp,
                when (kind) {
                    BoardFlowConfirmationKind.DESTRUCTIVE -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(BoardFlowConfirmationTokens.ContentPadding),
                verticalArrangement = Arrangement.spacedBy(BoardFlowConfirmationTokens.MessageSpacing)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(BoardFlowConfirmationTokens.HeaderSpacing)) {
                    val resolvedIcon = icon ?: if (kind == BoardFlowConfirmationKind.DESTRUCTIVE) {
                        Icons.Default.WarningAmber
                    } else {
                        null
                    }

                    if (resolvedIcon != null) {
                        Surface(
                            color = when (kind) {
                                BoardFlowConfirmationKind.DESTRUCTIVE -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(BoardFlowConfirmationTokens.IconContainerSize)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = resolvedIcon,
                                    contentDescription = null,
                                    tint = when (kind) {
                                        BoardFlowConfirmationKind.DESTRUCTIVE -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(BoardFlowConfirmationTokens.IconSize)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BoardFlowSecondaryButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dismissLabel)
                    }
                    when (kind) {
                        BoardFlowConfirmationKind.POSITIVE,
                        BoardFlowConfirmationKind.NEUTRAL -> {
                            BoardFlowPrimaryButton(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(confirmLabel)
                            }
                        }
                        BoardFlowConfirmationKind.DESTRUCTIVE -> {
                            BoardFlowDestructiveButton(
                                onClick = onConfirm,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(confirmLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoardFlowPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "btnScale"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = BoardFlowActionTokens.ButtonMinHeight)
            .scale(scale),
        enabled = enabled,
        colors = colors,
        shape = BoardFlowActionTokens.ButtonShape,
        contentPadding = BoardFlowActionTokens.ButtonContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun BoardFlowSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "btnScale"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = BoardFlowActionTokens.ButtonMinHeight)
            .scale(scale),
        enabled = enabled,
        colors = colors,
        shape = BoardFlowActionTokens.ButtonShape,
        contentPadding = BoardFlowActionTokens.ButtonContentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun BoardFlowDestructiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "destructiveBtnScale"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = BoardFlowActionTokens.ButtonMinHeight)
            .scale(scale),
        enabled = enabled,
        shape = BoardFlowActionTokens.ButtonShape,
        contentPadding = BoardFlowActionTokens.ButtonContentPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 0.55f else 0.24f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
        ),
        interactionSource = interactionSource
    ) {
        content()
    }
}

@Composable
fun BoardFlowInlineAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = BoardFlowActionTokens.InlineActionContentPadding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BoardFlowActionTokens.IconTextSpacing)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            content()
        }
    }
}

@Composable
fun BoardFlowIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ),
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(BoardFlowActionTokens.IconButtonSize),
        enabled = enabled,
        colors = colors
    ) {
        content()
    }
}

@Composable
fun BoardFlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit
) = BoardFlowPrimaryButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors = colors,
    content = content
)

@Composable
fun BoardFlowOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    content: @Composable RowScope.() -> Unit
) = BoardFlowSecondaryButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    colors = colors,
    content = content
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Popover(
    anchorCoordinates: LayoutCoordinates?,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!visible || anchorCoordinates == null) return
    val density = LocalDensity.current
    val view = LocalView.current
    // Get anchor position in window
    val anchorPos = anchorCoordinates.localToWindow(Offset.Zero)
    val anchorSize = anchorCoordinates.size
    // Estimate popover size (max width 320dp, height unknown until measured)
    val maxPopoverWidthPx = with(density) { 320.dp.roundToPx() }
    // Calculate initial popover position (below anchor)
    var x = anchorPos.x.toInt()
    var y = (anchorPos.y + anchorSize.height).toInt() + with(density) { 8.dp.roundToPx() }
    // Adjust x if popover would overflow right edge
    if (x + maxPopoverWidthPx > view.width) {
        x = (view.width - maxPopoverWidthPx - with(density) { 8.dp.roundToPx() }).coerceAtLeast(0)
    }
    // Adjust y if popover would overflow bottom edge (estimate height as 200dp if unknown)
    val estPopoverHeightPx = with(density) { 200.dp.roundToPx() }
    if (y + estPopoverHeightPx > view.height) {
        y = (anchorPos.y - estPopoverHeightPx - with(density) { 8.dp.roundToPx() }).toInt().coerceAtLeast(0)
    }
    // Fullscreen box to catch outside clicks
    Box(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onDismissRequest()
                })
            }
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .widthIn(max = 320.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .then(modifier)
        ) {
            content()
        }
    }
}
