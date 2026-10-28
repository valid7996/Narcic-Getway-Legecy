package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import org.jetbrains.compose.resources.stringResource

/** The buyer sends this code to the seller, who locks the config to this device only. */
@Composable
fun DeviceCodeDialog(code: String, onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val copiedToast = stringResource(Res.string.device_code_copied)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.device_code_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(Res.string.device_code_desc))
                Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                platform.copyToClipboard(code)
                platform.toast(copiedToast)
            }) { Text(stringResource(Res.string.device_code_copy)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )
}
