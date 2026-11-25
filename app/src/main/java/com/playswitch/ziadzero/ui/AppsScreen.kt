package com.playswitch.ziadzero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.playswitch.ziadzero.data.AppInstallStatus
import com.playswitch.ziadzero.data.EnrichedPlayApp
import com.playswitch.ziadzero.model.GoogleAccountInfo
import com.playswitch.ziadzero.model.OwnershipTransferMode

@Composable
fun AppsScreen(
    apps: List<EnrichedPlayApp>,
    accounts: List<GoogleAccountInfo>,
    searchQuery: String,
    includeSystemApps: Boolean,
    accountFilter: AccountFilter,
    statusFilter: AppStatusFilter,
    listState: LazyListState,
    playEngineReady: Boolean,
    isMutating: Boolean,
    defaultIcon: ImageBitmap,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap,
    onSearchQueryChange: (String) -> Unit,
    onIncludeSystemAppsChange: (Boolean) -> Unit,
    onAccountFilterChange: (AccountFilter) -> Unit,
    onStatusFilterChange: (AppStatusFilter) -> Unit,
    onTransferOwnership: (packageNames: List<String>, targetEmail: String, mode: OwnershipTransferMode) -> Unit,
    onClearOAuthBindings: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var singleAppDialogTarget by remember { mutableStateOf<EnrichedPlayApp?>(null) }
    var showBatchSheet by remember { mutableStateOf(false) }
    var pendingOAuthResetApp by remember { mutableStateOf<EnrichedPlayApp?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val statusColors = LocalStatusPalette.current
    val focusManager = LocalFocusManager.current
    val orderedEmails = remember(accounts) { accounts.map { it.name } }

    // Dismiss search keyboard once scrolling begins (never per touch-move pixel).
    LaunchedEffect(listState.isScrollInProgress, isSearchFocused) {
        if (listState.isScrollInProgress && isSearchFocused) {
            focusManager.clearFocus()
        }
    }

    val scopeApps = remember(apps, includeSystemApps) {
        if (includeSystemApps) apps else apps.filter { !it.isSystemApp }
    }

    val scopeStats = remember(scopeApps, apps) {
        val userTotal = apps.count { !it.isSystemApp }
        val unassignedCount = scopeApps.count { it.account.isNullOrBlank() }
        val disabledCount = scopeApps.count { it.installStatus == AppInstallStatus.DISABLED }
        val uninstalledCount = scopeApps.count { it.installStatus == AppInstallStatus.UNINSTALLED }
        val splitCount = scopeApps.count {
            !it.accountForUpdate.isNullOrBlank() && !it.accountForUpdate.equals(it.account, ignoreCase = true)
        }
        val perAccount = HashMap<String, Int>()
        scopeApps.forEach { app ->
            app.account?.lowercase()?.let { email ->
                perAccount[email] = (perAccount[email] ?: 0) + 1
            }
        }
        ScopeStats(
            userTotal = userTotal,
            unassignedCount = unassignedCount,
            disabledCount = disabledCount,
            uninstalledCount = uninstalledCount,
            splitCount = splitCount,
            perAccount = perAccount
        )
    }

    val filteredApps = remember(apps, searchQuery, includeSystemApps, accountFilter, statusFilter) {
        MainViewModel.filterApps(apps, searchQuery, includeSystemApps, accountFilter, statusFilter)
    }

    val allVisibleSelected = remember(filteredApps, selectedPackages) {
        filteredApps.isNotEmpty() && filteredApps.all { it.packageName in selectedPackages }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search name, package, or account...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSearchFocused = it.isFocused }
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            FilledTonalIconButton(
                onClick = {
                    multiSelectMode = !multiSelectMode
                    if (!multiSelectMode) selectedPackages = emptySet()
                },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Checklist,
                    contentDescription = "Toggle multi-select",
                    tint = if (multiSelectMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Clean 3-chip orthogonal filter row: everything stays visible in place, never clipped offscreen.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !includeSystemApps,
                onClick = { onIncludeSystemAppsChange(!includeSystemApps) },
                label = {
                    Text(
                        text = if (includeSystemApps) "All (${apps.size})" else "User (${scopeStats.userTotal})",
                        maxLines = 1
                    )
                }
            )

            Box(modifier = Modifier.weight(1f, fill = false)) {
                val activeAccountEmail = (accountFilter as? AccountFilter.Email)?.email
                val activeAccountTone = activeAccountEmail?.let {
                    accountToneFor(it, orderedEmails, statusColors.isDark)
                }
                val accountChipLabel = when (accountFilter) {
                    AccountFilter.All -> "Account"
                    AccountFilter.Unassigned -> "Unassigned (${scopeStats.unassignedCount})"
                    is AccountFilter.Email -> {
                        val cnt = scopeStats.perAccount[accountFilter.email.lowercase()] ?: 0
                        "${accountFilter.email.substringBefore('@')} ($cnt)"
                    }
                }
                FilterChip(
                    selected = accountFilter != AccountFilter.All,
                    onClick = { showAccountMenu = true },
                    colors = if (activeAccountTone != null) {
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = activeAccountTone.chipBg,
                            selectedLabelColor = activeAccountTone.chipFg
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    },
                    leadingIcon = activeAccountTone?.let { tone ->
                        {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(tone.dot)
                            )
                        }
                    },
                    trailingIcon = {
                        if (accountFilter != AccountFilter.All) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Clear account filter",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onAccountFilterChange(AccountFilter.All) }
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = accountChipLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )

                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Accounts (${scopeApps.size})") },
                        trailingIcon = if (accountFilter == AccountFilter.All) {
                            { Icon(AppIcons.Check, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = {
                            showAccountMenu = false
                            onAccountFilterChange(AccountFilter.All)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Unassigned (${scopeStats.unassignedCount})") },
                        trailingIcon = if (accountFilter == AccountFilter.Unassigned) {
                            { Icon(AppIcons.Check, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = {
                            showAccountMenu = false
                            onAccountFilterChange(AccountFilter.Unassigned)
                        }
                    )
                    if (accounts.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    accounts.forEach { acc ->
                        val isPicked = (accountFilter as? AccountFilter.Email)?.email?.equals(acc.name, ignoreCase = true) == true
                        val cnt = scopeStats.perAccount[acc.name.lowercase()] ?: 0
                        val tone = accountToneFor(acc.name, orderedEmails, statusColors.isDark)
                        DropdownMenuItem(
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(tone.dot)
                                )
                            },
                            text = {
                                Text(
                                    text = "${acc.name} ($cnt)",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingIcon = if (isPicked) {
                                { Icon(AppIcons.Check, null, Modifier.size(18.dp), tone.dot) }
                            } else {
                                null
                            },
                            onClick = {
                                showAccountMenu = false
                                onAccountFilterChange(AccountFilter.Email(acc.name))
                            }
                        )
                    }
                }
            }

            Box {
                val statusLabel = when (statusFilter) {
                    AppStatusFilter.ALL -> "Status"
                    AppStatusFilter.DISABLED -> "Disabled (${scopeStats.disabledCount})"
                    AppStatusFilter.UNINSTALLED -> "Uninstalled (${scopeStats.uninstalledCount})"
                    AppStatusFilter.SPLIT_ACCOUNTS -> "Split (${scopeStats.splitCount})"
                }
                FilterChip(
                    selected = statusFilter != AppStatusFilter.ALL,
                    onClick = { showStatusMenu = true },
                    trailingIcon = {
                        if (statusFilter != AppStatusFilter.ALL) {
                            Icon(
                                imageVector = AppIcons.Close,
                                contentDescription = "Clear status filter",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onStatusFilterChange(AppStatusFilter.ALL) }
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = { Text(statusLabel, maxLines = 1) }
                )

                DropdownMenu(
                    expanded = showStatusMenu,
                    onDismissRequest = { showStatusMenu = false }
                ) {
                    val entries = listOf(
                        AppStatusFilter.ALL to "All Status (${scopeApps.size})",
                        AppStatusFilter.DISABLED to "Disabled (${scopeStats.disabledCount})",
                        AppStatusFilter.UNINSTALLED to "Uninstalled (${scopeStats.uninstalledCount})",
                        AppStatusFilter.SPLIT_ACCOUNTS to "Split Billing / Update (${scopeStats.splitCount})"
                    )
                    entries.forEach { (option, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            trailingIcon = if (statusFilter == option) {
                                { Icon(AppIcons.Check, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            onClick = {
                                showStatusMenu = false
                                onStatusFilterChange(option)
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = multiSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedPackages.size} selected",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = filteredApps.isNotEmpty(),
                        onClick = {
                            val visibleIds = filteredApps.map { it.packageName }.toSet()
                            selectedPackages = if (allVisibleSelected) {
                                selectedPackages - visibleIds
                            } else {
                                selectedPackages + visibleIds
                            }
                        }
                    ) {
                        Text(if (allVisibleSelected) "Deselect All" else "Select All")
                    }
                    TextButton(onClick = {
                        selectedPackages = emptySet()
                        multiSelectMode = false
                    }) {
                        Text("Done")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No matching apps found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (statusFilter != AppStatusFilter.ALL ||
                            accountFilter != AccountFilter.All ||
                            searchQuery.isNotEmpty()
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = {
                                onSearchQueryChange("")
                                onAccountFilterChange(AccountFilter.All)
                                onStatusFilterChange(AppStatusFilter.ALL)
                            }) {
                                Text("Reset Filters")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName },
                        contentType = { "app_row" }
                    ) { app ->
                        val isSelected = app.packageName in selectedPackages
                        AppListRow(
                            app = app,
                            orderedEmails = orderedEmails,
                            isSelected = isSelected,
                            defaultIcon = defaultIcon,
                            getCachedIcon = getCachedIcon,
                            loadAppIcon = loadAppIcon,
                            onClick = {
                                if (multiSelectMode) {
                                    selectedPackages = if (isSelected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                } else {
                                    singleAppDialogTarget = app
                                }
                            },
                            onLongClick = {
                                multiSelectMode = true
                                selectedPackages = if (isSelected) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = multiSelectMode && selectedPackages.isNotEmpty()) {
            Button(
                onClick = { showBatchSheet = true },
                enabled = !isMutating && playEngineReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text("Transfer ${selectedPackages.size} Selected App(s)")
            }
        }
    }

    singleAppDialogTarget?.let { targetApp ->
        var sheetIcon by remember(targetApp.packageName) {
            mutableStateOf(getCachedIcon(targetApp.packageName))
        }
        if (sheetIcon == null) {
            LaunchedEffect(targetApp.packageName) {
                sheetIcon = loadAppIcon(targetApp.packageName)
            }
        }

        OwnerPickerBottomSheet(
            title = targetApp.label,
            subtitle = targetApp.packageName,
            installStatus = targetApp.installStatus,
            appIcon = sheetIcon ?: defaultIcon,
            currentOwner = targetApp.account,
            currentUpdateOwner = targetApp.accountForUpdate,
            libraryAccounts = targetApp.libraryAccounts,
            accounts = accounts,
            isBusy = isMutating || !playEngineReady,
            onDismiss = { singleAppDialogTarget = null },
            onSelectEmail = { email, mode ->
                singleAppDialogTarget = null
                onTransferOwnership(listOf(targetApp.packageName), email, mode)
            },
            onClearOAuth = {
                val appCopy = targetApp
                singleAppDialogTarget = null
                pendingOAuthResetApp = appCopy
            }
        )
    }

    if (showBatchSheet) {
        val batchList = selectedPackages.toList()
        OwnerPickerBottomSheet(
            title = "Transfer ${batchList.size} Apps",
            subtitle = "Assigns selected apps to one Play Store account immediately.",
            installStatus = null,
            appIcon = null,
            currentOwner = null,
            currentUpdateOwner = null,
            libraryAccounts = emptyList(),
            accounts = accounts,
            isBusy = isMutating || !playEngineReady,
            onDismiss = { showBatchSheet = false },
            onSelectEmail = { email, mode ->
                showBatchSheet = false
                multiSelectMode = false
                selectedPackages = emptySet()
                onTransferOwnership(batchList, email, mode)
            },
            onClearOAuth = null
        )
    }

    pendingOAuthResetApp?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingOAuthResetApp = null },
            title = { Text("Reset Sign-In Binding?") },
            text = {
                Text("Clears saved Google Sign-In (OAuth) links for ${target.label} (${target.packageName}) so it prompts cleanly. Reboot afterwards if needed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkg = target.packageName
                        pendingOAuthResetApp = null
                        onClearOAuthBindings(pkg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = statusColors.dangerDot)
                ) {
                    Text("Reset Tokens")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOAuthResetApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class ScopeStats(
    val userTotal: Int,
    val unassignedCount: Int,
    val disabledCount: Int,
    val uninstalledCount: Int,
    val splitCount: Int,
    val perAccount: Map<String, Int>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(
    app: EnrichedPlayApp,
    orderedEmails: List<String>,
    isSelected: Boolean,
    defaultIcon: ImageBitmap,
    getCachedIcon: (String) -> ImageBitmap?,
    loadAppIcon: suspend (String) -> ImageBitmap,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val statusColors = LocalStatusPalette.current
    var iconBitmap by remember(app.packageName) {
        mutableStateOf(getCachedIcon(app.packageName))
    }
    if (iconBitmap == null) {
        LaunchedEffect(app.packageName) {
            iconBitmap = loadAppIcon(app.packageName)
        }
    }

    val cardShape = CardDefaults.shape
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp)) {
                Image(
                    bitmap = iconBitmap ?: defaultIcon,
                    contentDescription = app.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val assignedAccount = app.account?.takeIf { it.isNotBlank() }
                    val tone = assignedAccount?.let {
                        accountToneFor(it, orderedEmails, statusColors.isDark)
                    }
                    AppBadge(
                        label = assignedAccount ?: "Unassigned",
                        bg = tone?.chipBg ?: MaterialTheme.colorScheme.surfaceVariant,
                        fg = tone?.chipFg ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!app.accountForUpdate.isNullOrBlank() &&
                        !app.accountForUpdate.equals(app.account, ignoreCase = true)
                    ) {
                        AppBadge(
                            label = "Update: ${app.accountForUpdate.substringBefore("@")}",
                            bg = statusColors.warningBg,
                            fg = statusColors.warningFg
                        )
                    }
                    when (app.installStatus) {
                        AppInstallStatus.DISABLED -> AppBadge(
                            label = "Disabled",
                            bg = statusColors.warningBg,
                            fg = statusColors.warningFg
                        )
                        AppInstallStatus.UNINSTALLED -> AppBadge(
                            label = "Uninstalled",
                            bg = statusColors.dangerBg,
                            fg = statusColors.dangerFg
                        )
                        AppInstallStatus.INSTALLED -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBadge(
    label: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OwnerPickerBottomSheet(
    title: String,
    subtitle: String,
    installStatus: AppInstallStatus?,
    appIcon: ImageBitmap?,
    currentOwner: String?,
    currentUpdateOwner: String?,
    libraryAccounts: List<String>,
    accounts: List<GoogleAccountInfo>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectEmail: (String, OwnershipTransferMode) -> Unit,
    onClearOAuth: (() -> Unit)?
) {
    var transferMode by remember { mutableStateOf(OwnershipTransferMode.BOTH) }
    val statusColors = LocalStatusPalette.current
    val orderedEmails = remember(accounts) { accounts.map { it.name } }

    val activeEmailForScope = when (transferMode) {
        OwnershipTransferMode.UPDATE_ONLY -> currentUpdateOwner ?: currentOwner
        OwnershipTransferMode.BOTH, OwnershipTransferMode.BILLING_ONLY -> currentOwner
    }

    val hasDifferentUpdate = !currentUpdateOwner.isNullOrBlank() &&
        !currentUpdateOwner.equals(currentOwner, ignoreCase = true)
    val otherLibraryAccounts = libraryAccounts.filter { libAcc ->
        !libAcc.equals(currentOwner, ignoreCase = true)
    }

    SmoothModalBottomSheet(onDismissRequest = onDismiss, scrollable = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (installStatus != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    when (installStatus) {
                        AppInstallStatus.INSTALLED -> AppBadge("Installed", statusColors.successBg, statusColors.successFg)
                        AppInstallStatus.DISABLED -> AppBadge("Disabled", statusColors.warningBg, statusColors.warningFg)
                        AppInstallStatus.UNINSTALLED -> AppBadge("Uninstalled", statusColors.dangerBg, statusColors.dangerFg)
                    }
                }
            }

            if (hasDifferentUpdate || otherLibraryAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (hasDifferentUpdate) {
                            Text(
                                text = "Update account differs: $currentUpdateOwner",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColors.warningFg
                            )
                        }
                        if (otherLibraryAccounts.isNotEmpty()) {
                            Text(
                                text = "Also in Play Library: ${otherLibraryAccounts.joinToString(", ") { it.substringBefore('@') }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OwnershipTransferMode.entries.forEach { mode ->
                    FilterChip(
                        selected = transferMode == mode,
                        onClick = { transferMode = mode },
                        label = { Text(mode.shortLabel, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose Play Store Account",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (accounts.isEmpty()) {
                Text(
                    text = "No Google accounts found.",
                    color = statusColors.dangerFg
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    accounts.forEach { acc ->
                        val isCurrent = activeEmailForScope.equals(acc.name, ignoreCase = true)
                        val tone = accountToneFor(acc.name, orderedEmails, statusColors.isDark)
                        val initial = acc.name.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) tone.chipBg else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isCurrent) BorderStroke(1.5.dp, tone.dot) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isBusy) { onSelectEmail(acc.name, transferMode) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) tone.dot else tone.chipBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrent) Color.White else tone.chipFg
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isCurrent) tone.chipFg else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (acc.isPrimary) {
                                        AppBadge("Primary", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isCurrent) {
                                        AppBadge("Current", statusColors.successBg, statusColors.successFg)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onClearOAuth != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.dangerBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isBusy, onClick = onClearOAuth)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = "Reset Sign-In Binding (OAuth)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColors.dangerFg
                        )
                        Text(
                            text = "Clear cached Google Sign-In link if this app sticks to an old account",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColors.dangerFg.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
