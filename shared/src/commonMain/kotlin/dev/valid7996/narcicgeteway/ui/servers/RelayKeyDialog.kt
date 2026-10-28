package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import org.jetbrains.compose.resources.stringResource
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*

/** Asks for the private key of a short (relay) link that was pasted without its key. */
@Composable
fun RelayKeyDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.relay_key_dialog_title)) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(Res.string.relay_key_dialog_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (key.isNotBlank()) onSubmit(key.trim()) }) { Text(stringResource(Res.string.relay_key_dialog_activate)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
