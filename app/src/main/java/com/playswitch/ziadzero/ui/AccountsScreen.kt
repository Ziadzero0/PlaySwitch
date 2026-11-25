package com.playswitch.ziadzero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playswitch.ziadzero.model.GoogleAccountInfo

private enum class RebootAction {
    SOFT,
    FULL
}

@Composable
fun AccountsScreen(
    accounts: List<GoogleAccountInfo>,
    appCountsByAccount: Map<String, Int>,
    playActiveAccount: String,
    pendingReboot: Boolean,
    accountsEngineReady: Boolean,
    isMutating: Boolean,
    onSwapPrimary: (targetId: Int) -> Unit,
    onViewAccountApps: (email: String) -> Unit,
    onSoftReboot: () -> Unit,
    onFullReboot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingSwapTarget by remember { mutableStateOf<GoogleAccountInfo?>(null) }
    var pendingRebootAction by remember { mutableStateOf<RebootAction?>(null) }
    var showTechnicalDetails by remember { mutableStateOf(false) }

    val statusColors = LocalStatusPalette.current
    val orderedEmails = remember(accounts) { accounts.map { it.name } }
    val primaryAccount = accounts.firstOrNull { it.isPrimary } ?: accounts.firstOrNull()
    val otherAccounts = remember(accounts, primaryAccount) {
        if (primaryAccount == null) accounts else accounts.filter { it.id != primaryAccount.id }
    }
    val playMatches = primaryAccount != null &&
        primaryAccount.name.equals(playActiveAccount, ignoreCase = true)

    if (accounts.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No Google accounts found on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (primaryAccount != null) {
                item(key = "primary_hero", contentType = "hero") {
                    val primaryTone = accountToneFor(primaryAccount.name, orderedEmails, statusColors.isDark)
                    val primaryAppCount = appCountsByAccount[primaryAccount.name.lowercase()] ?: 0
                    val deOutOfSync = !primaryAccount.isDePrimary || primaryAccount.deId == null

                    Card(
                        colors = CardDefaults.cardColors(containerColor = statusColors.brandBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(primaryTone.dot),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = primaryAccount.name.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Primary System Account",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = statusColors.brandFg.copy(alpha = 0.85f)
                                        )
                                        Text(
                                            text = primaryAccount.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColors.brandFg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                AccountStatusChip(
                                    text = if (pendingReboot) "Reboot Needed" else "Active",
                                    bg = if (pendingReboot) statusColors.warningBg else statusColors.successBg,
                                    fg = if (pendingReboot) statusColors.warningFg else statusColors.successFg
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppCountLinkChip(
                                    count = primaryAppCount,
                                    bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    fg = MaterialTheme.colorScheme.onSurface,
                                    onClick = { onViewAccountApps(primaryAccount.name) }
                                )

                                if (deOutOfSync) {
                                    AccountStatusChip(
                                        text = if (primaryAccount.deId == null) "Sync Missing" else "Order Out of Sync",
                                        bg = statusColors.warningBg,
                                        fg = statusColors.warningFg
                                    )
                                }

                                if (playActiveAccount.isNotBlank()) {
                                    AccountStatusChip(
                                        text = if (playMatches) "Play Synced" else "Play: ${playActiveAccount.substringBefore('@')}",
                                        bg = if (playMatches) statusColors.successBg else statusColors.warningBg,
                                        fg = if (playMatches) statusColors.successFg else statusColors.warningFg
                                    )
                                }
                            }

                            if (deOutOfSync && accountsEngineReady) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { pendingSwapTarget = primaryAccount },
                                    enabled = !isMutating,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = statusColors.warningFg,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Repair Account Order Sync")
                                }
                            }

                            AnimatedVisibility(visible = pendingReboot) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider(color = statusColors.brandFg.copy(alpha = 0.18f))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Reboot device or system UI now so Android reloads the new primary account.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColors.brandFg
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Button(
                                            onClick = { pendingRebootAction = RebootAction.SOFT },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = statusColors.dangerDot,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text("Soft Reboot")
                                        }
                                        OutlinedButton(onClick = { pendingRebootAction = RebootAction.FULL }) {
                                            Text("Full Reboot")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showTechnicalDetails = !showTechnicalDetails }
                                    .padding(vertical = 2.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (showTechnicalDetails) "Hide IDs & order details" else "Show IDs & order details",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColors.brandFg.copy(alpha = 0.8f)
                                )
                                Icon(
                                    imageVector = if (showTechnicalDetails) AppIcons.ExpandLess else AppIcons.ExpandMore,
                                    contentDescription = null,
                                    tint = statusColors.brandFg.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            AnimatedVisibility(visible = showTechnicalDetails) {
                                Text(
                                    text = buildString {
                                        append("CE Slot #${primaryAccount.id}")
                                        append(" • ")
                                        append(if (primaryAccount.deId != null) "DE Slot #${primaryAccount.deId}" else "DE Missing")
                                        if (playActiveAccount.isNotBlank()) {
                                            append(" • Play Active: $playActiveAccount")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColors.brandFg.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (otherAccounts.isEmpty()) {
                item(key = "single_account_note", contentType = "note") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Only one Google account is signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item(key = "switchable_header", contentType = "header") {
                    Text(
                        text = "Switchable Accounts (${otherAccounts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(otherAccounts, key = { it.id }, contentType = { "account_row" }) { acc ->
                    val tone = accountToneFor(acc.name, orderedEmails, statusColors.isDark)
                    val appCount = appCountsByAccount[acc.name.lowercase()] ?: 0
                    val isSyncIssue = acc.deId == null || (acc.isDePrimary && !acc.isPrimary)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tone.chipBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = acc.name.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tone.chipFg
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppCountLinkChip(
                                        count = appCount,
                                        bg = tone.chipBg,
                                        fg = tone.chipFg,
                                        onClick = { onViewAccountApps(acc.name) }
                                    )

                                    if (showTechnicalDetails) {
                                        AccountStatusChip(
                                            text = "ID #${acc.id}",
                                            bg = MaterialTheme.colorScheme.surfaceVariant,
                                            fg = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (acc.deId == null) {
                                        AccountStatusChip(
                                            text = "Sync Missing",
                                            bg = statusColors.dangerBg,
                                            fg = statusColors.dangerFg
                                        )
                                    } else if (acc.isDePrimary && !acc.isPrimary) {
                                        AccountStatusChip(
                                            text = "DE Primary Mismatch",
                                            bg = statusColors.warningBg,
                                            fg = statusColors.warningFg
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { pendingSwapTarget = acc },
                                enabled = !isMutating && accountsEngineReady,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(if (isSyncIssue) "Fix & Set" else "Set Primary")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingSwapTarget?.let { target ->
        val isRepairOnly = target.isPrimary && !target.isDePrimary
        AlertDialog(
            onDismissRequest = { pendingSwapTarget = null },
            title = {
                Text(if (isRepairOnly) "Repair Account Sync?" else "Swap Primary Account?")
            },
            text = {
                Text(
                    if (isRepairOnly) {
                        "Align device account order so ${target.name} is primary across both system stores. A backup is saved automatically and a reboot is required."
                    } else {
                        "Set ${target.name} as your primary Google account? Play Switch saves an automatic backup first, keeps all accounts signed in, and requires a reboot to apply."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        pendingSwapTarget = null
                        onSwapPrimary(id)
                    }
                ) {
                    Text(if (isRepairOnly) "Repair Sync" else "Swap Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwapTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingRebootAction?.let { action ->
        val title = if (action == RebootAction.SOFT) "Soft Reboot Device?" else "Full Reboot Device?"
        val body = if (action == RebootAction.SOFT) {
            "Restarts Android system services so your updated primary account takes effect right away."
        } else {
            "Performs a full device reboot."
        }
        AlertDialog(
            onDismissRequest = { pendingRebootAction = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRebootAction = null
                        if (action == RebootAction.SOFT) onSoftReboot() else onFullReboot()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = statusColors.dangerDot)
                ) {
                    Text(if (action == RebootAction.SOFT) "Soft Reboot" else "Reboot")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRebootAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppCountLinkChip(
    count: Int,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (count == 1) "1 Play app" else "$count Play apps",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = fg
            )
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = "Open apps",
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AccountStatusChip(text: String, bg: Color, fg: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
