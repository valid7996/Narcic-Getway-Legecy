@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.valid7996.narcicgeteway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.valid7996.narcicgeteway.shared.resources.Res
import dev.valid7996.narcicgeteway.shared.resources.*
import dev.valid7996.narcicgeteway.ui.platform.LocalPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AdbLogSheet(onDismiss: () -> Unit) {
    val platform = LocalPlatform.current
    val logsCopied = stringResource(Res.string.adb_logs_copied)
    val logsCleared = stringResource(Res.string.adb_logs_cleared)

    var logText by remember { mutableStateOf("") }
    var crashReport by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val refreshLogs: () -> Unit = {
        scope.launch {
            isLoading = true
            val (c, l) = withContext(Dispatchers.Default) {
                platform.getLastCrashReport() to platform.getAdbLog()
            }
            crashReport = c
            logText = l
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshLogs()
    }

    val lines = remember(logText, query) {
        val needle = query.trim()
        val all = logText.lines()
        if (needle.isEmpty()) all
        else all.filter { it.contains(needle, ignoreCase = true) }
    }

    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && atBottom) listState.scrollToItem(lines.lastIndex)
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(Res.string.adb_logs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.weight(1f),
                )
                if (!isLoading) {
                    Text(
                        "${lines.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = {
                    platform.copyToClipboard(logText)
                    platform.toast(logsCopied)
                }) {
                    Icon(painterResource(Res.drawable.ic_content_paste), stringResource(Res.string.logs_copy), Modifier.height(18.dp))
                }
                FilledTonalButton(onClick = { platform.shareAdbLog() }) {
                    Icon(painterResource(Res.drawable.ic_ios_share), stringResource(Res.string.adb_logs_share), Modifier.height(18.dp))
                }
                FilledTonalButton(onClick = { refreshLogs() }) {
                    Icon(painterResource(Res.drawable.ic_sync), stringResource(Res.string.adb_logs_refresh), Modifier.height(18.dp))
                }
                FilledTonalButton(onClick = {
                    platform.clearAdbLog()
                    crashReport = null
                    logText = ""
                    platform.toast(logsCleared)
                }) {
                    Icon(painterResource(Res.drawable.ic_delete), stringResource(Res.string.logs_clear), Modifier.height(18.dp))
                }
            }

            if (!crashReport.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    painterResource(Res.drawable.ic_info),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    stringResource(Res.string.adb_logs_crash_detected),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    platform.copyToClipboard(crashReport!!)
                                    platform.toast(logsCopied)
                                },
                            ) {
                                Text(
                                    stringResource(Res.string.adb_logs_copy_crash),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Box(Modifier.height(4.dp))
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                        ) {
                            Text(
                                text = crashReport!!.lines().take(6).joinToString("\n"),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text(stringResource(Res.string.logs_search)) },
                leadingIcon = { Icon(painterResource(Res.drawable.ic_search), null, Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { query = "" }) {
                            Icon(painterResource(Res.drawable.ic_close), stringResource(Res.string.action_cancel), Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            Box(Modifier.fillMaxWidth().height(420.dp)) {
                if (isLoading) {
                    Box(Modifier.align(Alignment.Center)) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                    }
                } else {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalLayoutDirection provides LayoutDirection.Ltr,
                    ) {
                        if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
                            Text(
                                stringResource(
                                    if (logText.isBlank()) Res.string.adb_logs_empty else Res.string.logs_no_match,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        } else {
                            val logBoxShape = RoundedCornerShape(14.dp)
                            Surface(
                                color = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7),
                                shape = logBoxShape,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(420.dp),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    itemsIndexed(lines, key = { i, l -> "$i:${l.hashCode()}" }) { _, line ->
                                        AdbLogRow(line)
                                    }
                                }
                            }
                        }
                    }

                    if (!atBottom && lines.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { scope.launch { listState.scrollToItem(lines.lastIndex) } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        ) {
                            Icon(painterResource(Res.drawable.ic_keyboard_arrow_down), null, Modifier.size(16.dp))
                            Box(Modifier.width(6.dp))
                            Text(stringResource(Res.string.logs_jump_latest), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbLogRow(line: String) {
    val colour = when {
        line.contains(" E ") || line.contains(" E/") || line.contains("FATAL") || line.contains("AndroidRuntime") || line.contains("CRASH") ->
            MaterialTheme.colorScheme.error
        line.contains(" W ") || line.contains(" W/") ->
            MaterialTheme.colorScheme.tertiary
        line.contains(" D ") || line.contains(" D/") ->
            MaterialTheme.colorScheme.onSurfaceVariant
        line.startsWith("===") || line.startsWith("---") || line.startsWith("!!!") ->
            MaterialTheme.colorScheme.primary
        else ->
            MaterialTheme.colorScheme.onSurface
    }
    val isImportant = line.contains(" E ") || line.contains(" E/") || line.contains("FATAL") || line.contains("AndroidRuntime")

    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .background(
                    if (isImportant) colour else Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Box(Modifier.width(6.dp))
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colour,
        )
    }
}
