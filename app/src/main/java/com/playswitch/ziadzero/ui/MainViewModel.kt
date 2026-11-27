package com.playswitch.ziadzero.ui

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.playswitch.ziadzero.data.AppInstallStatus
import com.playswitch.ziadzero.data.AppMetadataRepository
import com.playswitch.ziadzero.data.EnrichedPlayApp
import com.playswitch.ziadzero.data.RootBridge
import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupSnapshot
import com.playswitch.ziadzero.model.EnvironmentStatus
import com.playswitch.ziadzero.model.GoogleAccountInfo
import com.playswitch.ziadzero.model.OwnershipTransferMode
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AccountFilter {
    data object All : AccountFilter
    data object Unassigned : AccountFilter
    data class Email(val email: String) : AccountFilter
}

enum class AppStatusFilter(val label: String, val shortLabel: String) {
    ALL("All Status", "All Status"),
    INSTALLED("Installed", "Installed"),
    DISABLED("Disabled", "Disabled"),
    UNINSTALLED("Uninstalled", "Uninstalled"),
    SPLIT_ACCOUNTS("Split Billing / Update", "Split")
}

data class MainUiState(
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val statusMessage: String = "Connecting...",
    val envStatus: EnvironmentStatus? = null,
    val accounts: List<GoogleAccountInfo> = emptyList(),
    val playActiveAccount: String = "",
    val apps: List<EnrichedPlayApp> = emptyList(),
    val backups: List<BackupSnapshot> = emptyList(),
    val pendingReboot: Boolean = false,
    val snackbarMessage: String? = null,
    val activeTab: Int = 0,
    val searchQuery: String = "",
    val includeSystemApps: Boolean = false,
    val accountFilter: AccountFilter = AccountFilter.All,
    val appStatusFilter: AppStatusFilter = AppStatusFilter.ALL
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val rootBridge = RootBridge(application.applicationContext)
    private val appRepo = AppMetadataRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("playswitch_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MainUiState(pendingReboot = isPendingRebootOnCurrentBoot())
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshAll(silent = false, forceReloadLabels = false)
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(activeTab = index.coerceIn(0, 1)) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setIncludeSystemApps(include: Boolean) {
        _uiState.update { it.copy(includeSystemApps = include) }
    }

    fun setAccountFilter(filter: AccountFilter) {
        _uiState.update { it.copy(accountFilter = filter) }
    }

    fun setAppStatusFilter(statusFilter: AppStatusFilter) {
        _uiState.update { it.copy(appStatusFilter = statusFilter) }
    }

    fun openAppsForAccount(email: String) {
        _uiState.update {
            it.copy(
                activeTab = 1,
                includeSystemApps = true,
                accountFilter = AccountFilter.Email(email),
                appStatusFilter = AppStatusFilter.ALL
            )
        }
    }

    fun getCachedIcon(packageName: String) = appRepo.getCachedIcon(packageName)

    fun getDefaultAppIcon() = appRepo.defaultAppIcon

    suspend fun loadAppIcon(packageName: String) = appRepo.loadAppIcon(packageName)

    fun refreshAll(silent: Boolean = false, forceReloadLabels: Boolean = true) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, statusMessage = "Loading...") }
            }

            val installedDeferred = async {
                if (forceReloadLabels || !silent) appRepo.preloadInstalledMap() else null
            }
            val snapshotRes = rootBridge.getDashboardSnapshot()
            if (snapshotRes.isFailure) {
                val msg = snapshotRes.exceptionOrNull()?.message ?: "Root unavailable"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = msg
                    )
                }
                return@launch
            }

            val snapshot = snapshotRes.getOrThrow()
            val preloadedPm = installedDeferred.await()
            val enrichedApps = appRepo.enrichAppsMetadata(
                rawApps = snapshot.apps,
                preloadedInstalled = preloadedPm,
                forceRefreshLabels = forceReloadLabels
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    envStatus = snapshot.environment,
                    accounts = snapshot.accounts,
                    playActiveAccount = snapshot.playActiveAccount,
                    apps = enrichedApps,
                    backups = snapshot.backups,
                    pendingReboot = isPendingRebootOnCurrentBoot(),
                    statusMessage = "Root ready • ${snapshot.accounts.size} accounts • ${enrichedApps.size} apps"
                )
            }

            // Quietly pre-warm icons off the UI thread so list flinging stays 120fps with zero pop-in.
            launch {
                val prioritized = enrichedApps.sortedBy { it.isSystemApp }.map { it.packageName }
                appRepo.warmUpIcons(prioritized)
            }
        }
    }

    private suspend fun refreshBackupsOnly() {
        rootBridge.listBackups().onSuccess { updated ->
            _uiState.update { it.copy(backups = updated) }
        }
    }

    private suspend fun refreshAppsAndBackups() {
        val appsDeferred = viewModelScope.async { rootBridge.getPlayAppsOwnership() }
        val backupsDeferred = viewModelScope.async { rootBridge.listBackups() }
        val appsRes = appsDeferred.await()
        val backupsRes = backupsDeferred.await()

        val enriched = appsRes.getOrNull()?.let { raw ->
            appRepo.enrichAppsMetadata(rawApps = raw, forceRefreshLabels = false)
        }

        _uiState.update { cur ->
            val nextApps = enriched ?: cur.apps
            cur.copy(
                apps = nextApps,
                backups = backupsRes.getOrNull() ?: cur.backups,
                statusMessage = "Root ready • ${cur.accounts.size} accounts • ${nextApps.size} apps"
            )
        }
    }

    private suspend fun refreshAccountsEnvAndBackups() {
        val envDeferred = viewModelScope.async { rootBridge.checkEnvironment() }
        val accDeferred = viewModelScope.async { rootBridge.getGoogleAccounts() }
        val playActiveDeferred = viewModelScope.async { rootBridge.getPlayActiveAccount() }
        val backupsDeferred = viewModelScope.async { rootBridge.listBackups() }

        val env = envDeferred.await().getOrNull()
        val accounts = accDeferred.await().getOrNull()
        val playActive = playActiveDeferred.await().getOrNull()
        val backups = backupsDeferred.await().getOrNull()

        _uiState.update { cur ->
            val nextAcc = accounts ?: cur.accounts
            cur.copy(
                envStatus = env ?: cur.envStatus,
                accounts = nextAcc,
                playActiveAccount = playActive ?: cur.playActiveAccount,
                backups = backups ?: cur.backups,
                statusMessage = "Root ready • ${nextAcc.size} accounts • ${cur.apps.size} apps"
            )
        }
    }

    fun transferPlayOwnership(
        packageNames: List<String>,
        targetEmail: String,
        mode: OwnershipTransferMode = OwnershipTransferMode.BOTH
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.transferPlayAppOwnership(packageNames, targetEmail, mode)
            val result = res.getOrNull()
            val message = result?.message
                ?: res.exceptionOrNull()?.message
                ?: "Transfer failed"

            if (result?.success == true) {
                val targetSet = packageNames.toSet()
                _uiState.update { cur ->
                    val optimisticApps = cur.apps.map { app ->
                        if (app.packageName !in targetSet) {
                            app
                        } else {
                            when (mode) {
                                OwnershipTransferMode.BOTH -> app.copy(
                                    account = targetEmail,
                                    accountForUpdate = if (!app.accountForUpdate.isNullOrBlank()) targetEmail else app.accountForUpdate
                                )
                                OwnershipTransferMode.BILLING_ONLY -> app.copy(account = targetEmail)
                                OwnershipTransferMode.UPDATE_ONLY -> app.copy(accountForUpdate = targetEmail)
                            }
                        }
                    }
                    cur.copy(
                        isMutating = false,
                        apps = optimisticApps,
                        snackbarMessage = message
                    )
                }
                refreshAppsAndBackups()
            } else {
                _uiState.update { it.copy(isMutating = false, snackbarMessage = message) }
            }
        }
    }

    fun swapPrimaryGoogleAccount(targetId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.swapPrimaryGoogleAccount(targetId)
            val result = res.getOrNull()
            val message = result?.message
                ?: res.exceptionOrNull()?.message
                ?: "Account swap failed"
            if (result?.rebootRequired == true) {
                markPendingRebootCurrentBoot()
            }
            _uiState.update {
                it.copy(
                    isMutating = false,
                    pendingReboot = it.pendingReboot || (result?.rebootRequired == true),
                    snackbarMessage = message
                )
            }
            refreshAccountsEnvAndBackups()
        }
    }

    fun clearAppOAuthBindings(packageName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.clearAppOAuthBindings(packageName)
            val result = res.getOrNull()
            val message = result?.message
                ?: res.exceptionOrNull()?.message
                ?: "Sign-in reset failed"
            if (result?.rebootRequired == true) {
                markPendingRebootCurrentBoot()
            }
            _uiState.update {
                it.copy(
                    isMutating = false,
                    pendingReboot = it.pendingReboot || (result?.rebootRequired == true),
                    snackbarMessage = message
                )
            }
            refreshBackupsOnly()
        }
    }

    fun softReboot() {
        viewModelScope.launch {
            clearPendingRebootFlag()
            val res = rootBridge.softReboot()
            if (res.isFailure) {
                markPendingRebootCurrentBoot()
                _uiState.update {
                    it.copy(
                        pendingReboot = true,
                        snackbarMessage = res.exceptionOrNull()?.message ?: "Soft reboot failed"
                    )
                }
            }
        }
    }

    fun fullReboot() {
        viewModelScope.launch {
            clearPendingRebootFlag()
            val res = rootBridge.fullReboot()
            if (res.isFailure) {
                markPendingRebootCurrentBoot()
                _uiState.update {
                    it.copy(
                        pendingReboot = true,
                        snackbarMessage = res.exceptionOrNull()?.message ?: "Full reboot failed"
                    )
                }
            }
        }
    }

    fun createManualBackup(kind: BackupKind) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.createManualBackup(kind)
            val message = res.getOrNull()?.message
                ?: res.exceptionOrNull()?.message
                ?: "Backup failed"
            _uiState.update { it.copy(isMutating = false, snackbarMessage = message) }
            refreshBackupsOnly()
        }
    }

    fun restoreBackup(folderName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.restoreBackup(folderName)
            val result = res.getOrNull()
            val message = result?.message
                ?: res.exceptionOrNull()?.message
                ?: "Restore failed"
            if (result?.rebootRequired == true) {
                markPendingRebootCurrentBoot()
            }
            _uiState.update {
                it.copy(
                    isMutating = false,
                    pendingReboot = it.pendingReboot || (result?.rebootRequired == true),
                    snackbarMessage = message
                )
            }
            if (folderName.startsWith(BackupKind.SYSTEM.prefix)) {
                refreshAccountsEnvAndBackups()
            } else {
                refreshAppsAndBackups()
            }
        }
    }

    fun deleteBackup(folderName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val ok = rootBridge.deleteBackup(folderName).getOrDefault(false)
            _uiState.update {
                it.copy(
                    isMutating = false,
                    snackbarMessage = if (ok) "Backup deleted" else "Failed to delete backup"
                )
            }
            refreshBackupsOnly()
        }
    }

    fun exportBackup(folderName: String, destUri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.exportBackupToUri(folderName, destUri)
            val msg = res.getOrNull()?.message
                ?: res.exceptionOrNull()?.message
                ?: "Export failed"
            _uiState.update { it.copy(isMutating = false, snackbarMessage = msg) }
        }
    }

    fun importBackup(sourceUri: android.net.Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMutating = true) }
            val res = rootBridge.importBackupFromUri(sourceUri)
            val result = res.getOrNull()
            val msg = result?.message
                ?: res.exceptionOrNull()?.message
                ?: "Import failed"
            _uiState.update { it.copy(isMutating = false, snackbarMessage = msg) }
            if (result?.success == true) {
                refreshBackupsOnly()
            }
        }
    }

    private fun currentBootSignature(): String {
        val bootCount = runCatching {
            Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.BOOT_COUNT, -1)
        }.getOrDefault(-1)
        val bootId = runCatching {
            File("/proc/sys/kernel/random/boot_id").readText().trim()
        }.getOrDefault("")
        return "$bootCount:$bootId"
    }

    private fun isPendingRebootOnCurrentBoot(): Boolean {
        val saved = prefs.getString(KEY_PENDING_REBOOT_BOOT_SIG, null) ?: return false
        val current = currentBootSignature()
        if (saved == current) return true
        prefs.edit().remove(KEY_PENDING_REBOOT_BOOT_SIG).apply()
        return false
    }

    private fun markPendingRebootCurrentBoot() {
        prefs.edit().putString(KEY_PENDING_REBOOT_BOOT_SIG, currentBootSignature()).apply()
    }

    private fun clearPendingRebootFlag() {
        prefs.edit().remove(KEY_PENDING_REBOOT_BOOT_SIG).apply()
    }

    override fun onCleared() {
        rootBridge.unbind()
        super.onCleared()
    }

    companion object {
        private const val KEY_PENDING_REBOOT_BOOT_SIG = "pending_reboot_boot_sig"

        fun filterApps(
            apps: List<EnrichedPlayApp>,
            query: String,
            includeSystemApps: Boolean,
            accountFilter: AccountFilter,
            statusFilter: AppStatusFilter = AppStatusFilter.ALL
        ): List<EnrichedPlayApp> {
            val q = query.trim().lowercase()
            return apps.filter { app ->
                val matchesScope = includeSystemApps || !app.isSystemApp
                val matchesAccount = when (accountFilter) {
                    AccountFilter.All -> true
                    AccountFilter.Unassigned -> app.account.isNullOrBlank()
                    is AccountFilter.Email -> app.account.equals(accountFilter.email, ignoreCase = true)
                }
                val matchesStatus = when (statusFilter) {
                    AppStatusFilter.ALL -> true
                    AppStatusFilter.INSTALLED -> app.installStatus == AppInstallStatus.INSTALLED
                    AppStatusFilter.DISABLED -> app.installStatus == AppInstallStatus.DISABLED
                    AppStatusFilter.UNINSTALLED -> app.installStatus == AppInstallStatus.UNINSTALLED
                    AppStatusFilter.SPLIT_ACCOUNTS -> !app.accountForUpdate.isNullOrBlank() &&
                        !app.accountForUpdate.equals(app.account, ignoreCase = true)
                }
                val matchesQuery = q.isEmpty() ||
                    app.label.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q) ||
                    (app.account?.lowercase()?.contains(q) == true) ||
                    (app.accountForUpdate?.lowercase()?.contains(q) == true)
                matchesScope && matchesAccount && matchesStatus && matchesQuery
            }
        }
    }
}
