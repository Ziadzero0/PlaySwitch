package com.playswitch.ziadzero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.BackupSnapshot
import com.playswitch.ziadzero.model.EnvironmentStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupsModalSheet(
    backups: List<BackupSnapshot>,
    isMutating: Boolean,
    accountsEngineReady: Boolean,
    playEngineReady: Boolean,
    onCreateBackup: (BackupKind) -> Unit,
    onRestoreBackup: (String) -> Unit,
    onExportBackup: (String) -> Unit,
    onImportBackup: () -> Unit,
    onDeleteBackup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pendingRestore by remember { mutableStateOf<BackupSnapshot?>(null) }
    var pendingDelete by remember { mutableStateOf<BackupSnapshot?>(null) }
    val statusColors = LocalStatusPalette.current
    val listState = rememberLazyListState()
    val listBlocker = rememberLazyBottomSheetScrollBlocker(listState)

    SmoothModalBottomSheet(onDismissRequest = onDismiss, scrollable = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Backups & Recovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "/data/adb/playswitch_backups • 5 manual + 5 auto",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onImportBackup,
                    enabled = !isMutating,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Import ZIP", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onCreateBackup(BackupKind.SYSTEM) },
                    enabled = !isMutating && accountsEngineReady,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Backup Accounts")
                }
                OutlinedButton(
                    onClick = { onCreateBackup(BackupKind.PLAY) },
                    enabled = !isMutating && playEngineReady,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Backup Play Apps")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (backups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No snapshots saved yet — create one or Import ZIP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .nestedScroll(listBlocker)
                ) {
                    items(backups, key = { it.folderName }) { snapshot ->
                        val formattedTime = remember(snapshot.timestampMs) {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(snapshot.timestampMs))
                        }
                        val sizeKb = (snapshot.sizeBytes / 1024L).coerceAtLeast(1L)
                        val isSys = snapshot.kind == BackupKind.SYSTEM
                        val isManual = snapshot.origin == BackupOrigin.MANUAL

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BackupPill(
                                            text = snapshot.kind.label,
                                            bg = if (isSys) statusColors.brandBg else statusColors.successBg,
                                            fg = if (isSys) statusColors.brandFg else statusColors.successFg
                                        )
                                        BackupPill(
                                            text = snapshot.origin.label,
                                            bg = if (isManual) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            fg = if (isManual) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (snapshot.hasRestoreScript) {
                                            BackupPill(
                                                text = "Recovery",
                                                bg = statusColors.successBg,
                                                fg = statusColors.successFg
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${sizeKb} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (snapshot.summary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = snapshot.summary,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$formattedTime • ${snapshot.folderName} • ${snapshot.files.size} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onExportBackup(snapshot.folderName) },
                                            enabled = !isMutating
                                        ) {
                                            Text("Export")
                                        }
                                        TextButton(
                                            onClick = { pendingDelete = snapshot },
                                            enabled = !isMutating,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = statusColors.dangerFg
                                            )
                                        ) {
                                            Text("Delete")
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { pendingRestore = snapshot },
                                        enabled = !isMutating,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore Snapshot?") },
            text = {
                Text(
                    buildString {
                        append("Restore ${target.kind.label} snapshot (${target.folderName})?")
                        if (target.summary.isNotBlank()) {
                            append("\n\n${target.summary}")
                        }
                        if (target.kind == BackupKind.SYSTEM) {
                            append("\n\nA reboot will be required afterwards.")
                        }
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = target.folderName
                        pendingRestore = null
                        onRestoreBackup(name)
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Snapshot?") },
            text = { Text("Permanently remove ${target.folderName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        val name = target.folderName
                        pendingDelete = null
                        onDeleteBackup(name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = statusColors.dangerDot)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun HealthDiagnosticsModalSheet(
    env: EnvironmentStatus?,
    statusMessage: String,
    accountsCount: Int,
    appsCount: Int,
    onDismiss: () -> Unit
) {
    val statusColors = LocalStatusPalette.current

    SmoothModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val healthy = env != null && env.rootReady && env.accountsEngineReady && env.playEngineReady
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (healthy) statusColors.successBg else statusColors.dangerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (healthy) AppIcons.Shield else AppIcons.Warning,
                        contentDescription = null,
                        tint = if (healthy) statusColors.successFg else statusColors.dangerFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Engine & Compatibility Health",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (env == null) {
                val isSnapshotError = statusMessage.startsWith("Snapshot failed:", ignoreCase = true)
                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColors.dangerBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSnapshotError) {
                            "Root daemon connected, but environment snapshot failed: $statusMessage"
                        } else {
                            "Root shell daemon not connected. Grant root access to Play Switch in Magisk, KernelSU, or APatch and tap Refresh."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColors.dangerFg,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HealthRow(
                            label = "System Accounts Engine ($accountsCount accounts)",
                            ok = env.accountsEngineReady,
                            okText = "Compatible & Ready",
                            failText = "Blocked (Safety Lock)"
                        )
                        HorizontalDivider()
                        HealthRow(
                            label = "Play Ownership Engine ($appsCount apps)",
                            ok = env.playEngineReady,
                            okText = "Compatible & Ready",
                            failText = "Blocked (Safety Lock)"
                        )
                        HorizontalDivider()
                        HealthRow(
                            label = "System Account Stores (CE / DE)",
                            ok = env.ceDbExists && env.deDbExists,
                            okText = "Present",
                            failText = "Missing"
                        )
                        HorizontalDivider()
                        HealthRow(
                            label = "Play Store Stores (AppState / Library)",
                            ok = env.localAppStateExists,
                            okText = if (env.libraryDbExists) "Both Present" else "AppState Only",
                            failText = "Missing"
                        )
                    }
                }

                if (env.issues.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statusColors.dangerBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Safety Locks Triggered",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColors.dangerFg
                            )
                            env.issues.forEach { issue ->
                                Text(
                                    text = "• $issue",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColors.dangerFg
                                )
                            }
                        }
                    }
                }

                if (env.compatibilityNotes.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statusColors.warningBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Adaptive Compatibility Notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColors.warningFg
                            )
                            env.compatibilityNotes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColors.warningFg
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRow(
    label: String,
    ok: Boolean,
    okText: String,
    failText: String
) {
    val statusColors = LocalStatusPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        BackupPill(
            text = if (ok) okText else failText,
            bg = if (ok) statusColors.successBg else statusColors.dangerBg,
            fg = if (ok) statusColors.successFg else statusColors.dangerFg
        )
    }
}

@Composable
private fun BackupPill(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
