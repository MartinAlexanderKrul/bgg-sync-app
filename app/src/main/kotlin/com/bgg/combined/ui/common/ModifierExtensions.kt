package com.bgg.combined.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    then(Modifier.clickable(onClick = onClick))
