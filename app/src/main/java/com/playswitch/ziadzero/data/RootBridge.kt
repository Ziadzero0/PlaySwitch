package com.playswitch.ziadzero.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.playswitch.ziadzero.IRootAccountService
import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupSnapshot
import com.playswitch.ziadzero.model.DashboardSnapshot
import com.playswitch.ziadzero.model.EnvironmentStatus
import com.playswitch.ziadzero.model.GoogleAccountInfo
import com.playswitch.ziadzero.model.OperationResult
import com.playswitch.ziadzero.model.OwnershipTransferMode
import com.playswitch.ziadzero.model.PlayAppOwnershipInfo
import com.playswitch.ziadzero.model.RootJsonCodec
import com.playswitch.ziadzero.root.RootAccountService
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RootBridge(private val context: Context) {
    @Volatile
    private var service: IRootAccountService? = null

    @Volatile
    private var activeConnection: ServiceConnection? = null

    suspend fun ensureConnected(): Result<IRootAccountService> = withContext(Dispatchers.IO) {
        val current = service
        if (current != null && current.asBinder().isBinderAlive) {
            return@withContext Result.success(current)
        }

        val hasRoot = runCatching {
            val cached = Shell.getCachedShell()
            if (cached != null && !cached.isRoot) {
                runCatching { cached.close() }
            }
            val shell = Shell.getShell()
            shell.isRoot
        }.getOrDefault(false)

        if (!hasRoot) {
            return@withContext Result.failure(IllegalStateException("Root access not granted or su unavailable"))
        }

        val bound = withTimeoutOrNull(10_000L) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val stub = IRootAccountService.Stub.asInterface(binder)
                            service = stub
                            activeConnection = this
                            if (cont.isActive) cont.resume(stub)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                        }
                    }
                    val intent = Intent(context, RootAccountService::class.java)
                    RootService.bind(intent, conn)
                }
            }
        }

        if (bound != null) {
            Result.success(bound)
        } else {
            Result.failure(IllegalStateException("Timed out binding RootAccountService"))
        }
    }

    suspend fun getDashboardSnapshot(): Result<DashboardSnapshot> = withService { svc ->
        val raw = svc.getDashboardSnapshotJson()
        val obj = org.json.JSONObject(raw)
        if (obj.has("environment")) {
            RootJsonCodec.decodeDashboardSnapshot(raw)
        } else {
            val err = RootJsonCodec.decodeOperationResult(raw)
            error(err.message)
        }
    }

    suspend fun checkEnvironment(): Result<EnvironmentStatus> = withService { svc ->
        val raw = svc.checkEnvironmentJson()
        if (raw.trimStart().startsWith("{") && raw.contains("\"rootReady\"")) {
            RootJsonCodec.decodeEnvironmentStatus(raw)
        } else {
            val err = RootJsonCodec.decodeOperationResult(raw)
            error(err.message)
        }
    }

    suspend fun getGoogleAccounts(): Result<List<GoogleAccountInfo>> = withService { svc ->
        RootJsonCodec.decodeGoogleAccounts(svc.googleAccountsJson)
    }

    suspend fun getPlayAppsOwnership(): Result<List<PlayAppOwnershipInfo>> = withService { svc ->
        RootJsonCodec.decodePlayAppsOwnership(svc.playAppsOwnershipJson)
    }

    suspend fun getPlayActiveAccount(): Result<String> = withService { svc ->
        svc.playActiveAccount
    }

    suspend fun listBackups(): Result<List<BackupSnapshot>> = withService { svc ->
        RootJsonCodec.decodeBackups(svc.listBackupsJson())
    }

    suspend fun createManualBackup(kind: BackupKind): Result<OperationResult> = withService { svc ->
        RootJsonCodec.decodeOperationResult(svc.createManualBackup(kind.name))
    }

    suspend fun restoreBackup(folderName: String): Result<OperationResult> = withService { svc ->
        RootJsonCodec.decodeOperationResult(svc.restoreBackup(folderName))
    }

    suspend fun deleteBackup(folderName: String): Result<Boolean> = withService { svc ->
        svc.deleteBackup(folderName)
    }

    suspend fun exportBackupToUri(
        folderName: String,
        destUri: android.net.Uri
    ): Result<OperationResult> = withService { svc ->
        val pfd = svc.openBackupExportZip(folderName)
            ?: error("Unable to prepare backup archive")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            val output = context.contentResolver.openOutputStream(destUri)
                ?: error("Unable to open destination file for writing")
            output.use { out ->
                input.copyTo(out)
                out.flush()
            }
        }
        OperationResult(true, "Exported snapshot to chosen location")
    }

    suspend fun importBackupFromUri(sourceUri: android.net.Uri): Result<OperationResult> {
        val svc = ensureConnected().getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            runCatching {
                val tempZip = java.io.File.createTempFile("import_", ".zip", context.cacheDir)
                try {
                    val input = context.contentResolver.openInputStream(sourceUri)
                        ?: error("Unable to open selected archive")
                    input.use { src ->
                        java.io.FileOutputStream(tempZip).use { dst ->
                            src.copyTo(dst)
                            dst.flush()
                        }
                    }
                    val pfd = android.os.ParcelFileDescriptor.open(
                        tempZip,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    pfd.use { fd ->
                        RootJsonCodec.decodeOperationResult(svc.importBackupZip(fd))
                    }
                } finally {
                    runCatching { tempZip.delete() }
                }
            }
        }
    }

    suspend fun transferPlayAppOwnership(
        packageNames: List<String>,
        targetEmail: String,
        mode: OwnershipTransferMode = OwnershipTransferMode.BOTH
    ): Result<OperationResult> = withService { svc ->
        RootJsonCodec.decodeOperationResult(
            svc.transferPlayAppOwnership(packageNames, targetEmail, mode.name)
        )
    }

    suspend fun swapPrimaryGoogleAccount(targetId: Int): Result<OperationResult> = withService { svc ->
        RootJsonCodec.decodeOperationResult(svc.swapPrimaryGoogleAccount(targetId))
    }

    suspend fun clearAppOAuthBindings(packageName: String): Result<OperationResult> = withService { svc ->
        RootJsonCodec.decodeOperationResult(svc.clearAppOAuthBindings(packageName))
    }

    suspend fun softReboot(): Result<Unit> = withService { svc ->
        svc.softReboot()
    }

    suspend fun fullReboot(): Result<Unit> = withService { svc ->
        svc.fullReboot()
    }

    fun unbind() {
        val conn = activeConnection ?: return
        activeConnection = null
        service = null
        runCatching { RootService.unbind(conn) }
    }

    private suspend inline fun <T> withService(crossinline block: (IRootAccountService) -> T): Result<T> {
        val svc = ensureConnected().getOrElse { return Result.failure(it) }
        return withContext(Dispatchers.IO) {
            runCatching { block(svc) }
        }
    }
}
