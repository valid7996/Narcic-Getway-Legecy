@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.domain.config.SstpProfile
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.action_cancel
import dev.valid7996.narcicgeteway.shared.resources.ic_chevron_right
import dev.valid7996.narcicgeteway.shared.resources.ic_keyboard_arrow_down
import dev.valid7996.narcicgeteway.shared.resources.action_save
import dev.valid7996.narcicgeteway.ui.components.PickerField
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SstpSheet(
    initial: SstpProfile? = null,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, SstpProfile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initialName) }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "443") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var sni by remember { mutableStateOf(initial?.sni ?: "") }
    var acceptAllCerts by remember { mutableStateOf(initial?.acceptAllCertificates ?: true) }
    var authProtocol by remember { mutableStateOf(initial?.authProtocol ?: SstpProfile.AUTH_MSCHAPV2) }
    var mtu by remember { mutableStateOf((initial?.mtu ?: 1400).toString()) }
    var advancedOpen by remember { mutableStateOf(false) }

    val valid = server.isNotBlank() && username.isNotBlank()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⚡",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column {
                        Text(
                            text = if (initial == null) "افزودن کانفیگ MS-SSTP" else "ویرایش کانفیگ MS-SSTP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "پروتکل امن مایکروسافت / میکروتیک (پورت 443)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نام نمایشی (اختیاری)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("مثال: MikroTik SSTP") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("آدرس سرور *") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("vpn.example.com یا IP") },
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("پورت") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("نام کاربری (Username) *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("رمز عبور (Password)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(
                            text = if (passwordVisible) "🙈" else "👁️",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "پذیرش گواهی‌های نامعتبر / خودامضا",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "برای سرورهای میکروتیک با گواهینامه Self-Signed فعال باشد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = acceptAllCerts,
                    onCheckedChange = { acceptAllCerts = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary),
                )
            }

            TextButton(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (advancedOpen) "بستن تنظیمات پیشرفته" else "نمایش تنظیمات پیشرفته (SNI / MTU)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(if (advancedOpen) Res.drawable.ic_keyboard_arrow_down else Res.drawable.ic_chevron_right),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AnimatedVisibility(visible = advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = sni,
                        onValueChange = { sni = it },
                        label = { Text("SNI دلخواه (TLS Server Name)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("در صورت خالی بودن، آدرس سرور درج می‌شود") },
                    )

                    PickerField(
                        label = "الگوریتم احراز هویت (Auth Protocol)",
                        selected = authProtocol,
                        options = listOf(
                            SstpProfile.AUTH_MSCHAPV2 to "MS-CHAP v2 (پیش‌فرض)",
                            SstpProfile.AUTH_PAP to "PAP",
                            SstpProfile.AUTH_CHAP to "CHAP",
                        ),
                        onSelect = { authProtocol = it },
                    )

                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it.filter { c -> c.isDigit() } },
                        label = { Text("سایز فریم MTU") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(
                    onClick = {
                        val parsedPort = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: 443
                        val parsedMtu = mtu.toIntOrNull()?.takeIf { it in 500..2000 } ?: 1400
                        val p = SstpProfile(
                            server = server.trim(),
                            port = parsedPort,
                            username = username.trim(),
                            password = password,
                            sni = sni.trim(),
                            acceptAllCertificates = acceptAllCerts,
                            authProtocol = authProtocol,
                            mtu = parsedMtu,
                            mru = parsedMtu,
                        )
                        val finalName = name.trim().ifBlank { "SSTP ${p.serverHost()}:${p.effectivePort()}" }
                        scope.launch { sheetState.hide() }
                        onSave(finalName, p)
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = if (MaterialTheme.colorScheme.primary.luminance() > 0.5f) Color.Black else Color.White,
                    ),
                ) {
                    Text(stringResource(Res.string.action_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
