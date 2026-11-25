package com.playswitch.ziadzero

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playswitch.ziadzero.ui.AccountsScreen
import com.playswitch.ziadzero.ui.AppIcons
import com.playswitch.ziadzero.ui.AppsScreen
import com.playswitch.ziadzero.ui.BackupsModalSheet
import com.playswitch.ziadzero.ui.HealthDiagnosticsModalSheet
import com.playswitch.ziadzero.ui.LocalStatusPalette
import com.playswitch.ziadzero.ui.MainViewModel
import com.playswitch.ziadzero.ui.PlaySwitchTheme
import com.playswitch.ziadzero.ui.SettingsAboutModalSheet
import com.playswitch.ziadzero.ui.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("playswitch_prefs", Context.MODE_PRIVATE)
        val savedMode = runCatching {
            ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
        }.getOrDefault(ThemeMode.AUTO)
        val savedDynamic = prefs.getBoolean("dynamic_color", true)

        setContent {
            var themeMode by remember { mutableStateOf(savedMode) }
            var dynamicColor by remember { mutableStateOf(savedDynamic) }
            val isDark = when (themeMode) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
                onDispose {}
            }

            PlaySwitchTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                PlaySwitchRoot(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = { next ->
                        themeMode = next
                        prefs.edit().putString("theme_mode", next.name).apply()
                    },
                    onDynamicColorChange = { enabled ->
                        dynamicColor = enabled
                        prefs.edit().putBoolean("dynamic_color", enabled).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaySwitchRoot(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    vm: MainViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    var showBackupsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showHealthSheet by remember { mutableStateOf(false) }
    var pendingExportFolder by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        val folder = pendingExportFolder
        pendingExportFolder = null
        if (destUri != null && folder != null) {
            vm.exportBackup(folder, destUri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            vm.importBackup(sourceUri)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val statusColors = LocalStatusPalette.current
    val appsListState = rememberLazyListState()
    val pagerState = rememberPagerState(initialPage = state.activeTab) { 2 }

    LaunchedEffect(state.activeTab) {
        if (pagerState.currentPage != state.activeTab) {
            pagerState.animateScrollToPage(state.activeTab)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != state.activeTab) {
                    vm.selectTab(page)
                }
            }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.consumeSnackbar()
    }

    val env = state.envStatus
    val isRootHealthy = env != null && env.rootReady && env.accountsEngineReady && env.playEngineReady && env.issues.isEmpty()
    val isRootFailed = !state.isLoading && (env == null || !env.rootReady || env.issues.isNotEmpty() || !env.accountsEngineReady || !env.playEngineReady)

    val appCountsByAccount = remember(state.apps) {
        val counts = HashMap<String, Int>()
        state.apps.forEach { app ->
            app.account?.lowercase()?.let { email ->
                counts[email] = (counts[email] ?: 0) + 1
            }
        }
        counts
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Play Switch")
                        val pillBg = when {
                            isRootFailed -> statusColors.dangerBg
                            isRootHealthy -> statusColors.successBg
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val pillFg = when {
                            isRootFailed -> statusColors.dangerFg
                            isRootHealthy -> statusColors.successFg
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val dotColor = when {
                            isRootFailed -> statusColors.dangerDot
                            isRootHealthy -> statusColors.successDot
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val pillText = when {
                            isRootFailed -> "Issue"
                            state.isLoading -> "..."
                            else -> "Root"
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = pillBg,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showHealthSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = pillText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pillFg
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showBackupsSheet = true }) {
                        Icon(AppIcons.Backup, contentDescription = "Backups & Recovery")
                    }
                    IconButton(
                        onClick = { vm.refreshAll(silent = false, forceReloadLabels = true) },
                        enabled = !state.isLoading && !state.isMutating
                    ) {
                        Icon(AppIcons.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(AppIcons.Info, contentDescription = "Info & Settings")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { vm.selectTab(0) },
                    text = {
                        val accCount = state.accounts.size
                        Text(if (accCount > 0) "Accounts ($accCount)" else "Accounts")
                    }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { vm.selectTab(1) },
                    text = {
                        val appCount = state.apps.size
                        Text(if (appCount > 0) "Apps ($appCount)" else "Apps")
                    }
                )
            }

            if (state.isLoading || state.isMutating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (isRootFailed) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = statusColors.dangerBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = env?.issues?.firstOrNull() ?: state.statusMessage,
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColors.dangerFg
                            )
                            Text(
                                text = "Affected actions are locked to protect your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColors.dangerFg.copy(alpha = 0.85f)
                            )
                        }
                        TextButton(onClick = { showHealthSheet = true }) {
                            Text("Inspect", color = statusColors.dangerFg)
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    when (page) {
                        0 -> AccountsScreen(
                            accounts = state.accounts,
                            appCountsByAccount = appCountsByAccount,
                            playActiveAccount = state.playActiveAccount,
                            pendingReboot = state.pendingReboot,
                            accountsEngineReady = env?.accountsEngineReady != false,
                            isMutating = state.isMutating,
                            onSwapPrimary = { targetId -> vm.swapPrimaryGoogleAccount(targetId) },
                            onViewAccountApps = { email -> vm.openAppsForAccount(email) },
                            onSoftReboot = { vm.softReboot() },
                            onFullReboot = { vm.fullReboot() }
                        )

                        else -> AppsScreen(
                            apps = state.apps,
                            accounts = state.accounts,
                            searchQuery = state.searchQuery,
                            includeSystemApps = state.includeSystemApps,
                            accountFilter = state.accountFilter,
                            statusFilter = state.appStatusFilter,
                            listState = appsListState,
                            playEngineReady = env?.playEngineReady != false,
                            isMutating = state.isMutating,
                            defaultIcon = vm.getDefaultAppIcon(),
                            getCachedIcon = { pkg -> vm.getCachedIcon(pkg) },
                            loadAppIcon = { pkg -> vm.loadAppIcon(pkg) },
                            onSearchQueryChange = { vm.setSearchQuery(it) },
                            onIncludeSystemAppsChange = { vm.setIncludeSystemApps(it) },
                            onAccountFilterChange = { vm.setAccountFilter(it) },
                            onStatusFilterChange = { vm.setAppStatusFilter(it) },
                            onTransferOwnership = { pkgs, email, mode ->
                                vm.transferPlayOwnership(pkgs, email, mode)
                            },
                            onClearOAuthBindings = { pkg -> vm.clearAppOAuthBindings(pkg) }
                        )
                    }
                }
            }
        }
    }

    if (showBackupsSheet) {
        BackupsModalSheet(
            backups = state.backups,
            isMutating = state.isMutating,
            accountsEngineReady = env?.accountsEngineReady != false,
            playEngineReady = env?.playEngineReady != false,
            onCreateBackup = { kind -> vm.createManualBackup(kind) },
            onRestoreBackup = { name -> vm.restoreBackup(name) },
            onExportBackup = { folderName ->
                pendingExportFolder = folderName
                exportLauncher.launch("PlaySwitch-$folderName.zip")
            },
            onImportBackup = {
                importLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
            },
            onDeleteBackup = { name -> vm.deleteBackup(name) },
            onDismiss = { showBackupsSheet = false }
        )
    }

    if (showHealthSheet) {
        HealthDiagnosticsModalSheet(
            env = env,
            statusMessage = state.statusMessage,
            accountsCount = state.accounts.size,
            appsCount = state.apps.size,
            onDismiss = { showHealthSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsAboutModalSheet(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            env = env,
            accountsCount = state.accounts.size,
            appsCount = state.apps.size,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onOpenHealth = { showHealthSheet = true },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}
