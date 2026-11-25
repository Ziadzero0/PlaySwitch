package com.playswitch.ziadzero.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.playswitch.ziadzero.BuildConfig
import com.playswitch.ziadzero.model.EnvironmentStatus

private val DIAGNOSTIC_EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

private fun sanitizeDiagnosticText(raw: String): String =
    DIAGNOSTIC_EMAIL_REGEX.replace(raw, "[redacted-email]")

object AppLinks {
    const val GITHUB_URL = "https://github.com/Ziadzero0/PlaySwitch"
    // Add your real Telegram username or channel URL when ready.
    const val TELEGRAM_URL = ""
    const val DONATE_URL = ""
}

@Composable
fun SettingsAboutModalSheet(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    env: EnvironmentStatus?,
    accountsCount: Int,
    appsCount: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenHealth: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val statusColors = LocalStatusPalette.current
    val uriHandler = LocalUriHandler.current

    val launcherIcon = remember(context) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, 128, 128)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }

    fun openUrl(title: String, url: String) {
        if (url.isBlank()) {
            onShowMessage("$title link is not configured yet")
            return
        }
        runCatching { uriHandler.openUri(url) }
            .onFailure { onShowMessage("Could not open $title link") }
    }

    fun copyDiagnostics() {
        val text = buildString {
            appendLine("Play Switch v${BuildConfig.VERSION_NAME} [${BuildConfig.BUILD_CHANNEL}] (${BuildConfig.APPLICATION_ID})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Root Ready: ${env?.rootReady == true}")
            appendLine("Accounts Engine: ${env?.accountsEngineReady == true} ($accountsCount accounts)")
            appendLine("Play Engine: ${env?.playEngineReady == true} ($appsCount apps)")
            if (!env?.issues.isNullOrEmpty()) {
                val safeIssues = env?.issues?.joinToString("; ") { sanitizeDiagnosticText(it) }
                appendLine("Issues: $safeIssues")
            }
        }
        runCatching {
            val cm = context.getSystemService(ClipboardManager::class.java)
            cm?.setPrimaryClip(ClipData.newPlainText("Play Switch Info", text))
            onShowMessage("Copied sanitized summary (no personal data)")
        }.onFailure {
            onShowMessage("Failed to copy info")
        }
    }

    val (channelBg, channelFg) = when (BuildConfig.BUILD_CHANNEL) {
        "STABLE" -> statusColors.successBg to statusColors.successFg
        "DEV" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> statusColors.brandBg to statusColors.brandFg
    }

    SmoothModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Identity header card (clean, uncrowded, single semantic channel badge)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (launcherIcon != null) {
                            Image(
                                bitmap = launcherIcon,
                                contentDescription = "Play Switch",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Play Switch",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = channelBg
                                ) {
                                    Text(
                                        text = BuildConfig.BUILD_CHANNEL,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = channelFg,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} • By ziad zero",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = ::copyDiagnostics,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.ContentCopy,
                                contentDescription = "Copy info",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.size(5.dp))
                            Text("Copy", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Manage your primary Google account and per-app Play Store billing & update ownership cleanly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Appearance settings only — no out-of-place OS/Root labels
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode == ThemeMode.AUTO,
                            onClick = { onThemeModeChange(ThemeMode.AUTO) },
                            label = { Text("System") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text("Dark") }
                        )
                        FilterChip(
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text("Light") }
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Material You",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Dynamic wallpaper colors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }
                }
            }

            // Practical Quick Reference
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "How It Works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Account Swap: Switches default Google account; requires a Soft or Full Reboot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• App Transfer: Reassigns Play billing, updates, or sign-in links immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Backups & Export: Keeps 5 Manual + 5 Auto snapshots, with ZIP export/import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenHealth()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Health", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { openUrl("GitHub", AppLinks.GITHUB_URL) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GitHub", maxLines = 1)
                }
                if (AppLinks.TELEGRAM_URL.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { openUrl("Telegram", AppLinks.TELEGRAM_URL) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Telegram", maxLines = 1)
                    }
                }
                if (AppLinks.DONATE_URL.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { openUrl("Donate", AppLinks.DONATE_URL) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Donate", maxLines = 1)
                    }
                }
            }
        }
    }
}
