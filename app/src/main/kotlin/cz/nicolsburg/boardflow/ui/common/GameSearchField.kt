package cz.nicolsburg.boardflow.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GameSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search games...",
    trailingAction: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingAction,
        singleLine = true,
        modifier = modifier
    )
}

@Composable
fun SearchFieldActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    BoardFlowIconButton(onClick = onClick) {
        content()
    }
}
