package com.playswitch.ziadzero.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.playswitch.ziadzero.IRootAccountService
import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.OperationResult
import com.playswitch.ziadzero.model.OwnershipTransferMode
import com.playswitch.ziadzero.model.RootJsonCodec
import com.topjohnwu.superuser.ipc.RootService
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RootAccountService : RootService() {
    init {
        SqliteCompatFixer.apply()
    }

    private val serviceLock = ReentrantLock()

    private val binder = object : IRootAccountService.Stub() {
        override fun getDashboardSnapshotJson(): String = serviceLock.withLock {
            runCatching {
                RootJsonCodec.encodeDashboardSnapshot(SafeDbInspector.readDashboardSnapshot())
            }.getOrElse { err ->
                RootJsonCodec.encodeOperationResult(
                    OperationResult(false, "Snapshot failed: ${err.message}")
                )
            }
        }

        override fun checkEnvironmentJson(): String = serviceLock.withLock {
            runCatching {
                RootJsonCodec.encodeEnvironmentStatus(SafeDbInspector.inspectEnvironment())
            }.getOrElse { err ->
                RootJsonCodec.encodeOperationResult(
                    OperationResult(false, "Environment check failed: ${err.message}")
                )
            }
        }

        override fun getGoogleAccountsJson(): String = serviceLock.withLock {
            runCatching {
                RootJsonCodec.encodeGoogleAccounts(SafeDbInspector.readGoogleAccounts())
            }.getOrDefault("[]")
        }

        override fun getPlayAppsOwnershipJson(): String = serviceLock.withLock {
            runCatching {
                RootJsonCodec.encodePlayAppsOwnership(SafeDbInspector.readPlayAppsOwnership())
            }.getOrDefault("[]")
        }

        override fun getPlayActiveAccount(): String = serviceLock.withLock {
            runCatching {
                SafeDbInspector.readPlayActiveAccount()
            }.getOrDefault("")
        }

        override fun listBackupsJson(): String = serviceLock.withLock {
            runCatching {
                RootJsonCodec.encodeBackups(BackupManager.listBackups())
            }.getOrDefault("[]")
        }

        override fun createManualBackup(kind: String?): String = serviceLock.withLock {
            val res = runCatching {
                when (kind?.uppercase()) {
                    BackupKind.SYSTEM.name -> BackupManager.createSystemBackup(origin = BackupOrigin.MANUAL)
                    BackupKind.PLAY.name -> BackupManager.createPlayBackup(origin = BackupOrigin.MANUAL)
                    else -> OperationResult(false, "Invalid backup kind: $kind")
                }
            }.getOrElse { err ->
                OperationResult(false, "Backup error: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun restoreBackup(backupFolderName: String?): String = serviceLock.withLock {
            val res = runCatching {
                val name = backupFolderName?.trim().orEmpty()
                BackupManager.restoreBackup(name)
            }.getOrElse { err ->
                OperationResult(false, "Restore error: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun deleteBackup(backupFolderName: String?): Boolean = serviceLock.withLock {
            runCatching {
                BackupManager.deleteBackup(backupFolderName?.trim().orEmpty())
            }.getOrDefault(false)
        }

        override fun openBackupExportZip(backupFolderName: String?): ParcelFileDescriptor? = serviceLock.withLock {
            runCatching {
                BackupManager.openExportZipFd(backupFolderName?.trim().orEmpty())
            }.getOrNull()
        }

        override fun importBackupZip(zipFd: ParcelFileDescriptor?): String = serviceLock.withLock {
            val res = runCatching {
                if (zipFd == null) {
                    OperationResult(false, "No backup archive received")
                } else {
                    BackupManager.importBackupZip(zipFd)
                }
            }.getOrElse { err ->
                runCatching { zipFd?.close() }
                OperationResult(false, "Import failed: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun swapPrimaryGoogleAccount(targetId: Int): String = serviceLock.withLock {
            val res = runCatching {
                SystemAccountEngine.swapPrimaryGoogleAccount(targetId)
            }.getOrElse { err ->
                OperationResult(false, "Swap failed: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun transferPlayAppOwnership(
            packageNames: MutableList<String>?,
            targetEmail: String?,
            mode: String?
        ): String = serviceLock.withLock {
            val res = runCatching {
                PlayOwnershipEngine.transferOwnership(
                    rawPackages = packageNames ?: emptyList(),
                    rawTargetEmail = targetEmail.orEmpty(),
                    mode = OwnershipTransferMode.fromValue(mode)
                )
            }.getOrElse { err ->
                OperationResult(false, "Transfer failed: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun clearAppOAuthBindings(packageName: String?): String = serviceLock.withLock {
            val res = runCatching {
                OAuthCleanerEngine.clearAppOAuthBindings(packageName.orEmpty())
            }.getOrElse { err ->
                OperationResult(false, "Sign-in reset failed: ${err.message}")
            }
            RootJsonCodec.encodeOperationResult(res)
        }

        override fun softReboot() {
            SystemAccountEngine.triggerSoftReboot()
        }

        override fun fullReboot() {
            SystemAccountEngine.triggerFullReboot()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
