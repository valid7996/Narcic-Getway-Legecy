@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxMetadata
import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlin.io.encoding.Base64
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun UnlockConfigDialog(
    zsxBytes: ByteArray,
    metadata: ZsxMetadata,
    repository: ConfigRepository,
    subscriptionId: String = "",
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val platform = LocalPlatform.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var userKeyInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val configName = metadata.nameEn.ifBlank { metadata.nameFa }.ifBlank { stringResource(Res.string.unlock_title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = if (isDark) Color(0xFF1E1E20) else Color(0xFFFFFFFF),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.ic_lock),
                    contentDescription = null,
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.unlock_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isDark) Color.White else Color.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF28282B) else Color(0xFFF2F2F7),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(configName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (metadata.note.isNotBlank()) {
                            Text(metadata.note, fontSize = 11.sp, color = Color.Gray)
                        }
                        if (metadata.expiresAt != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.unlock_validity),
                                    fontSize = 11.sp,
                                    color = if (isDark) Color(0xFFAAAAAA) else Color(0xFF666666),
                                    fontWeight = FontWeight.Medium,
                                )
                                ExpiryCountdownBadge(
                                    expiresAt = metadata.expiresAt,
                                    isDark = isDark,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(Res.string.unlock_this_device_code, repository.nrgDeviceId()),
                                fontSize = 11.sp,
                                color = Color(0xFFFF9500),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = {
                                    platform.copyToClipboard(repository.nrgDeviceId())
                                    platform.toast(platform.readClipboard()?.let { repository.nrgDeviceId() } ?: "")
                                },
                            ) {
                                Text(stringResource(Res.string.unlock_copy), fontSize = 11.sp, color = Color(0xFFFF9500))
                            }
                        }
                    }
                }

                Text(
                    stringResource(Res.string.unlock_enter_key_desc),
                    fontSize = 12.sp,
                    color = if (isDark) Color.LightGray else Color.DarkGray,
                )

                OutlinedTextField(
                    value = userKeyInput,
                    onValueChange = {
                        userKeyInput = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.unlock_key_label)) },
                    placeholder = { Text(stringResource(Res.string.unlock_key_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9500),
                        unfocusedBorderColor = if (isDark) Color(0xFF3C3C3E) else Color(0xFFD1D1D6),
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clip = platform.readClipboard()
                            if (!clip.isNullOrBlank()) {
                                val extracted = dev.valid7996.narcicgeteway.domain.config.LockedConfigExtractor.extractKey(clip) ?: clip.trim()
                                userKeyInput = extracted
                                errorMessage = null
                            }
                        }) {
                            Icon(painter = painterResource(Res.drawable.ic_content_paste), contentDescription = stringResource(Res.string.unlock_paste_key), tint = Color(0xFFFF9500))
                        }
                    },
                )

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = Color(0xFFFF3B30),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            val warnEnterKey = stringResource(Res.string.unlock_warn_enter_key)
            val successToast = stringResource(Res.string.unlock_success_toast)
            val errOtherDevice = stringResource(Res.string.unlock_err_other_device)
            val errWrongKey = stringResource(Res.string.unlock_err_wrong_key)
            val errExpired = stringResource(Res.string.unlock_err_expired)
            val errConsumed = stringResource(Res.string.unlock_err_key_consumed)
            Button(
                onClick = {
                    val key = userKeyInput.trim()
                    if (key.isBlank()) {
                        errorMessage = warnEnterKey
                        return@Button
                    }
                    val result = repository.importLocked(zsxBytes, key, subscriptionId = subscriptionId)
                    result.fold(
                        onSuccess = {
                            platform.toast(successToast)
                            onSuccess()
                        },
                        onFailure = { err ->
                            errorMessage = when {
                                err is dev.valid7996.narcicgeteway.crypto.ZsxWrongPasswordException ->
                                    if (metadata.deviceBound) errOtherDevice
                                    else errWrongKey
                                err is dev.valid7996.narcicgeteway.crypto.ZsxExpiredException -> errExpired
                                err is dev.valid7996.narcicgeteway.crypto.ZsxKeyConsumedException -> errConsumed
                                else -> err.message ?: errWrongKey
                            }
                        },
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
            ) {
                Text(stringResource(Res.string.unlock_btn_submit), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
