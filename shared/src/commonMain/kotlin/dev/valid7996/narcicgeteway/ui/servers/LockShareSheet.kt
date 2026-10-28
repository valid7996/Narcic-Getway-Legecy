@file:OptIn(
    kotlin.io.encoding.ExperimentalEncodingApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package dev.valid7996.narcicgeteway.ui.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.valid7996.narcicgeteway.crypto.NrgLink
import dev.valid7996.narcicgeteway.crypto.ZsxCrypto
import dev.valid7996.narcicgeteway.crypto.ZsxSealRequest
import dev.valid7996.narcicgeteway.data.config.NrgRelay
import dev.valid7996.narcicgeteway.domain.config.VpnProfile
import dev.valid7996.narcicgeteway.platform.currentTimeMillis
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import org.jetbrains.compose.resources.stringResource
import dev.valid7996.narcicgeteway.ui.components.PageHeader
import dev.valid7996.narcicgeteway.ui.components.ProvidePageBack
import dev.valid7996.narcicgeteway.ui.components.QrDialog
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun formatExpiryTimestamp(
    epochMs: Long,
    expiredStr: String,
    daysHoursTmpl: String,
    daysTmpl: String,
    hoursMinsTmpl: String,
    hoursTmpl: String,
    minsTmpl: String,
): String {
    val diffMs = epochMs - currentTimeMillis()
    if (diffMs <= 0) return expiredStr
    val totalMinutes = diffMs / (60 * 1000L)
    val days = totalMinutes / (24 * 60)
    val remMinutesAfterDays = totalMinutes % (24 * 60)
    val hours = remMinutesAfterDays / 60
    val minutes = remMinutesAfterDays % 60
    return when {
        days > 0 && hours > 0 -> daysHoursTmpl.replace("%1\$d", days.toString()).replace("%2\$d", hours.toString())
        days > 0 -> daysTmpl.replace("%1\$d", days.toString())
        hours > 0 && minutes > 0 -> hoursMinsTmpl.replace("%1\$d", hours.toString()).replace("%2\$d", minutes.toString())
        hours > 0 -> hoursTmpl.replace("%1\$d", hours.toString())
        else -> minsTmpl.replace("%1\$d", minutes.toString())
    }
}

@Composable
fun LockShareSheet(
    profile: VpnProfile,
    rawPayload: String,
    onDismiss: () -> Unit,
    relayEndpoint: String = "",
    relayToken: String = "",
    onRelayChanged: (endpoint: String, token: String) -> Unit = { _, _ -> },
) {
    val platform = LocalPlatform.current
    val strExpired = stringResource(Res.string.lock_expired)
    val strDaysHours = stringResource(Res.string.lock_expires_days_hours)
    val strDays = stringResource(Res.string.lock_expires_days)
    val strHoursMins = stringResource(Res.string.lock_expires_hours_mins)
    val strHours = stringResource(Res.string.lock_expires_hours)
    val strMins = stringResource(Res.string.lock_expires_mins)
    val toastRelayUrlErr = stringResource(Res.string.lock_err_relay_url)
    val toastRelayUploadErr = stringResource(Res.string.lock_err_relay_upload)
    val toastCreatedExclusive = stringResource(Res.string.lock_toast_created_exclusive)
    val toastCreatedPublic = stringResource(Res.string.lock_toast_created_public)
    val toastGeneratingErr = stringResource(Res.string.lock_err_generating)
    val shareHeader = stringResource(Res.string.lock_share_header)
    val shareName = stringResource(Res.string.lock_share_name)
    val shareValidity = stringResource(Res.string.lock_share_validity)
    val sharePermanent = stringResource(Res.string.lock_share_permanent)
    val shareDeviceExclusive = stringResource(Res.string.lock_share_device_exclusive)
    val shareWarning = stringResource(Res.string.lock_share_warning)
    val sharePublicType = stringResource(Res.string.lock_share_public_type)
    val sharePublicInfo = stringResource(Res.string.lock_share_public_info)
    val shareNote = stringResource(Res.string.lock_share_note)
    val shareLink = stringResource(Res.string.lock_share_link)
    val shareInstructions = stringResource(Res.string.lock_share_instructions)
    val toastLinkCopied = stringResource(Res.string.copied)
    val scope = rememberCoroutineScope()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var configTitle by remember { mutableStateOf(profile.name) }
    var userNote by remember { mutableStateOf("") }
    var lockMode by remember { mutableIntStateOf(0) } // 0: By Device Code, 1: Public Lock (no device code)
    var buyerDeviceCode by remember { mutableStateOf("") }
    var selectedExpiryOption by remember { mutableIntStateOf(5) } // 0: None, 1: 5m, 2: 1h, 3: 6h, 4: 24h, 5: 3d, 6: 7d, 7: 30d, 8: custom
    var customDays by remember { mutableIntStateOf(0) }
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(5) }

    var useRelay by remember { mutableStateOf(relayEndpoint.isNotBlank()) }
    var relayUrlInput by remember { mutableStateOf(relayEndpoint) }
    var relayTokenInput by remember { mutableStateOf(relayToken) }
    var generating by remember { mutableStateOf(false) }

    var generatedNrgLink by remember { mutableStateOf<String?>(null) }
    var generatedSealedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var generatedExpiresAt by remember { mutableStateOf<Long?>(null) }
    var generatedDeviceCode by remember { mutableStateOf<String?>(null) }

    var linkCopied by remember { mutableStateOf(false) }
    var packageCopied by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.3f) else Color(0xFF3C3C43).copy(alpha = 0.3f)),
            )
        },
    ) {
        ProvidePageBack(onBack = onDismiss) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PageHeader(
                    title = stringResource(Res.string.lock_share_title),
                    subtitle = stringResource(Res.string.lock_share_subtitle),
                )

                // ─── Settings Card ────────────────────────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF242426) else Color(0xFFF2F2F7),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA)),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = configTitle,
                            onValueChange = { configTitle = it },
                            label = { Text(stringResource(Res.string.lock_config_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = userNote,
                            onValueChange = { userNote = it },
                            label = { Text(stringResource(Res.string.lock_buyer_note)) },
                            placeholder = { Text(stringResource(Res.string.lock_buyer_note_placeholder)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                        )

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Lock Mode Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(Res.string.lock_type_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = lockMode == 0,
                                    onClick = { lockMode = 0 },
                                    label = { Text(stringResource(Res.string.lock_type_device), fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF9500).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFFFF9500),
                                    ),
                                )
                                FilterChip(
                                    selected = lockMode == 1,
                                    onClick = { lockMode = 1 },
                                    label = { Text(stringResource(Res.string.lock_type_public), fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF007AFF).copy(alpha = 0.2f),
                                        selectedLabelColor = Color(0xFF007AFF),
                                    ),
                                )
                            }

                            if (lockMode == 0) {
                                // Device Lock Section
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_lock),
                                            contentDescription = null,
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            stringResource(Res.string.lock_buyer_device_title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }

                                    OutlinedTextField(
                                        value = buyerDeviceCode,
                                        onValueChange = { buyerDeviceCode = it },
                                        label = { Text(stringResource(Res.string.lock_buyer_device_label)) },
                                        placeholder = { Text(stringResource(Res.string.lock_buyer_device_placeholder)) },
                                        supportingText = {
                                            Text(
                                                stringResource(Res.string.lock_buyer_device_desc),
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF9500),
                                        ),
                                        trailingIcon = {
                                            IconButton(onClick = {
                                                val clip = platform.readClipboard()
                                                if (!clip.isNullOrBlank()) {
                                                    buyerDeviceCode = clip.trim()
                                                }
                                            }) {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_content_paste),
                                                    contentDescription = stringResource(Res.string.lock_paste_device_code),
                                                    tint = Color(0xFFFF9500),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        },
                                    )
                                }
                            } else {
                                // Public Lock Explanation
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF1E2836) else Color(0xFFE8F2FD),
                                    border = BorderStroke(1.dp, Color(0xFF007AFF).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_public),
                                            contentDescription = null,
                                            tint = Color(0xFF007AFF),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            stringResource(Res.string.lock_public_desc),
                                            fontSize = 11.sp,
                                            color = if (isDark) Color(0xFFCFE4FD) else Color(0xFF0A3E80),
                                            lineHeight = 16.sp,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Expiration Section
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = painterResource(Res.drawable.ic_schedule), contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.lock_expiration_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val expiryOptions = listOf(
                                    0 to stringResource(Res.string.lock_exp_permanent),
                                    1 to stringResource(Res.string.lock_exp_5min),
                                    2 to stringResource(Res.string.lock_exp_1hour),
                                    3 to stringResource(Res.string.lock_exp_6hours),
                                    4 to stringResource(Res.string.lock_exp_24hours),
                                    5 to stringResource(Res.string.lock_exp_3days),
                                    6 to stringResource(Res.string.lock_exp_7days),
                                    7 to stringResource(Res.string.lock_exp_30days),
                                    8 to stringResource(Res.string.lock_exp_custom),
                                )
                                expiryOptions.forEach { (code, label) ->
                                    val isSelected = selectedExpiryOption == code
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedExpiryOption = code },
                                        label = { Text(label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF007AFF).copy(alpha = 0.2f),
                                            selectedLabelColor = Color(0xFF007AFF),
                                        ),
                                    )
                                }
                            }

                            AnimatedVisibility(visible = selectedExpiryOption == 8) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(Res.string.lock_exp_custom_hint), fontSize = 11.sp, color = Color.Gray)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = if (customDays > 0) customDays.toString() else "",
                                            onValueChange = { customDays = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 },
                                            label = { Text(stringResource(Res.string.lock_days)) },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = if (customHours > 0) customHours.toString() else "",
                                            onValueChange = { customHours = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(0, 23) ?: 0 },
                                            label = { Text(stringResource(Res.string.lock_hours)) },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                        OutlinedTextField(
                                            value = if (customMinutes > 0) customMinutes.toString() else "",
                                            onValueChange = { customMinutes = it.filter { c -> c.isDigit() }.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                                            label = { Text(stringResource(Res.string.lock_minutes)) },
                                            placeholder = { Text("5") },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            singleLine = true,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = if (isDark) Color(0xFF38383A) else Color(0xFFE5E5EA))

                        // Short link (relay)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(stringResource(Res.string.lock_relay_title), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    stringResource(Res.string.lock_relay_desc),
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                )
                            }
                            Switch(checked = useRelay, onCheckedChange = { useRelay = it })
                        }

                        AnimatedVisibility(visible = useRelay) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = relayUrlInput,
                                    onValueChange = { relayUrlInput = it },
                                    label = { Text(stringResource(Res.string.lock_relay_addr)) },
                                    placeholder = { Text("https://nrg.example.workers.dev") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = relayTokenInput,
                                    onValueChange = { relayTokenInput = it },
                                    label = { Text(stringResource(Res.string.lock_relay_token)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                )
                            }
                        }

                        val warnDeviceCode = stringResource(Res.string.lock_warn_enter_device_code)
                        // Generate Button
                        Button(
                            onClick = {
                                val device = buyerDeviceCode.trim()
                                if (lockMode == 0 && device.isEmpty()) {
                                    platform.toast(warnDeviceCode)
                                    return@Button
                                }

                                val now = currentTimeMillis()
                                val expiresAt: Long? = when (selectedExpiryOption) {
                                    0 -> null
                                    1 -> now + 5 * 60 * 1000L
                                    2 -> now + 60 * 60 * 1000L
                                    3 -> now + 6 * 3600 * 1000L
                                    4 -> now + 24 * 3600 * 1000L
                                    5 -> now + 3 * 24 * 3600 * 1000L
                                    6 -> now + 7 * 24 * 3600 * 1000L
                                    7 -> now + 30 * 24 * 3600 * 1000L
                                    8 -> {
                                        val totalMs = (customDays * 24 * 3600L + customHours * 3600L + customMinutes * 60L) * 1000L
                                        if (totalMs > 0) now + totalMs else null
                                    }
                                    else -> null
                                }

                                val sealReq = ZsxSealRequest(
                                    configPayload = rawPayload,
                                    nameEn = configTitle,
                                    nameFa = configTitle,
                                    note = userNote,
                                    expiresAt = expiresAt,
                                    password = if (lockMode == 0) device else null,
                                    deviceBound = (lockMode == 0),
                                )

                                if (generating) return@Button
                                generating = true
                                scope.launch {
                                    try {
                                        // Argon2 (64 MiB) + compression: keep it off the UI thread.
                                        val sealedBytes = withContext(Dispatchers.Default) { ZsxCrypto.seal(sealReq) }
                                        generatedSealedBytes = sealedBytes
                                        var link = NrgLink.encode(sealedBytes)
                                        if (useRelay) {
                                            val hostOk = NrgRelay.hostOf(relayUrlInput) != null
                                            if (!hostOk) {
                                                platform.toast(toastRelayUrlErr)
                                            } else {
                                                val up = runCatching {
                                                    NrgRelay.upload(relayUrlInput, relayTokenInput, sealedBytes, expiresAt, null)
                                                }
                                                up.onSuccess {
                                                    link = NrgLink.relayLink(it)
                                                    onRelayChanged(relayUrlInput, relayTokenInput)
                                                }.onFailure {
                                                    platform.toast(toastRelayUploadErr.replace("%1\$s", it.message ?: ""))
                                                }
                                            }
                                        }
                                        generatedNrgLink = link
                                        generatedExpiresAt = expiresAt
                                        generatedDeviceCode = if (lockMode == 0) device else null
                                        linkCopied = false
                                        packageCopied = false
                                        platform.toast(if (lockMode == 0) toastCreatedExclusive else toastCreatedPublic)
                                    } catch (e: Throwable) {
                                        platform.toast(toastGeneratingErr.replace("%1\$s", e.message ?: ""))
                                    } finally {
                                        generating = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                        ) {
                            Icon(painter = painterResource(Res.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (generating) stringResource(Res.string.lock_btn_generating) else stringResource(Res.string.lock_btn_generate), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // ─── Result Output Card ────────────────────────────────────────────────────
                AnimatedVisibility(visible = generatedNrgLink != null) {
                    val nrgLink = generatedNrgLink ?: ""
                    val expiresAt = generatedExpiresAt
                    val lockedDevice = generatedDeviceCode ?: ""

                    val fullPackageText = buildString {
                        appendLine(shareHeader)
                        appendLine(shareName.replace("%1\$s", configTitle))
                        appendLine(shareValidity.replace("%1\$s", if (expiresAt != null) formatExpiryTimestamp(expiresAt, strExpired, strDaysHours, strDays, strHoursMins, strHours, strMins) else sharePermanent))
                        if (lockedDevice.isNotEmpty()) {
                            appendLine(shareDeviceExclusive.replace("%1\$s", lockedDevice))
                            appendLine(shareWarning)
                        } else {
                            appendLine(sharePublicType)
                            appendLine(sharePublicInfo)
                        }
                        if (userNote.isNotBlank()) {
                            appendLine(shareNote.replace("%1\$s", userNote))
                        }
                        appendLine()
                        appendLine(shareLink)
                        appendLine(nrgLink)
                        appendLine()
                        appendLine(shareInstructions)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF1E261E) else Color(0xFFEDF8ED),
                        border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.4f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(Res.string.lock_ready), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF34C759))
                                Text(
                                    if (expiresAt != null) "Expires: ${formatExpiryTimestamp(expiresAt, strExpired, strDaysHours, strDays, strHoursMins, strHours, strMins)}" else stringResource(Res.string.lock_exp_permanent),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray,
                                )
                            }

                            Text(
                                "Length: ${nrgLink.length}" + if (nrgLink.startsWith("nrg://r.")) " (Short)" else "",
                                fontSize = 11.sp,
                                color = Color.Gray,
                            )

                            // Link Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDark) Color(0xFF161618) else Color(0xFFFFFFFF),
                                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        nrgLink,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                        color = if (isDark) Color.LightGray else Color.DarkGray,
                                    )
                                    val toastCopiedLink = stringResource(Res.string.lock_link_copied)
                                    IconButton(
                                        onClick = {
                                            platform.copyToClipboard(nrgLink)
                                            linkCopied = true
                                            platform.toast(toastCopiedLink)
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (linkCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste),
                                            contentDescription = "Copy Link",
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            // Device Code Badge
                            if (lockedDevice.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF28241D) else Color(0xFFFFF8EC),
                                    border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f)),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.ic_lock),
                                            contentDescription = null,
                                            tint = Color(0xFFFF9500),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(stringResource(Res.string.lock_locked_for_device), fontSize = 11.sp, color = Color.Gray)
                                            Text(
                                                lockedDevice,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF9500),
                                            )
                                        }
                                    }
                                }
                            }

                            // Share, File and QR Actions
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val toastPackage = stringResource(Res.string.lock_package_copied)
                                    Button(
                                        onClick = {
                                            platform.copyToClipboard(fullPackageText)
                                            packageCopied = true
                                            platform.toast(toastPackage)
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                    ) {
                                        Icon(painter = painterResource(if (packageCopied) Res.drawable.ic_check else Res.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (packageCopied) stringResource(Res.string.copied) else stringResource(Res.string.lock_copy_msg), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val safeName = (configTitle.trim().ifEmpty { "narcic_config" })
                                                .replace(Regex("""[^A-Za-z0-9_.\-\u0600-\u06FF]"""), "_")
                                            val rawBytes = generatedSealedBytes ?: runCatching { NrgLink.decode(nrgLink) }.getOrNull()
                                            if (rawBytes != null) {
                                                platform.shareFiles(listOf("$safeName.nrg" to rawBytes))
                                                platform.toast("$safeName.nrg")
                                            }
                                        },
                                        modifier = Modifier.height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_description), contentDescription = "Share .nrg file", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(Res.string.lock_send_file), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = { platform.shareText(fullPackageText) },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_ios_share), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(Res.string.lock_share_text), fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showQrDialog = true },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(painter = painterResource(Res.drawable.ic_qr_code_2), contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(Res.string.lock_qr_code), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog && generatedNrgLink != null) {
        val link = generatedNrgLink!!
        QrDialog(
            title = configTitle,
            text = link,
            copyLabel = stringResource(Res.string.action_copy),
            shareLabel = stringResource(Res.string.action_share),
            closeLabel = stringResource(Res.string.action_close),
            unsupportedLabel = stringResource(Res.string.qr_too_large),
            onCopy = {
                platform.copyToClipboard(link)
                platform.toast(toastLinkCopied)
            },
            onShare = { platform.shareText(link) },
            onDismiss = { showQrDialog = false },
        )
    }
}
