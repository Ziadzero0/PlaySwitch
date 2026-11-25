package com.playswitch.ziadzero.root

import android.database.sqlite.SQLiteDatabase
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.OperationResult
import java.io.File
import java.util.UUID

object SystemAccountEngine {
    data class SwapPlan(
        val firstId: Int,
        val firstEmail: String,
        val targetId: Int,
        val targetEmail: String
    )

    fun swapIdsInAccountDb(
        db: SqliteDbGateway,
        targetId: Int? = null,
        targetEmail: String? = null,
        dbLabel: String = "Account DB"
    ): SwapPlan {
        val report = SchemaGuard.validateAccountDb(db, dbLabel)
        require(report.isSafe) {
            report.blockingIssues.joinToString("; ")
        }

        val googleAccounts = db.queryRows(
            "SELECT _id, name FROM accounts WHERE type = 'com.google' ORDER BY _id ASC"
        ).map { row ->
            (row[0] as Number).toInt() to row[1].toString()
        }
        require(googleAccounts.isNotEmpty()) { "No Google accounts found in database" }

        val (firstId, firstEmail) = googleAccounts.first()
        val targetPair = when {
            targetId != null -> googleAccounts.firstOrNull { it.first == targetId }
                ?: error("Target account id=$targetId not found")
            targetEmail != null -> googleAccounts.firstOrNull { it.second.equals(targetEmail, ignoreCase = true) }
                ?: error("Target account '$targetEmail' not found")
            else -> error("Either targetId or targetEmail must be provided")
        }

        val resolvedTargetId = targetPair.first
        val resolvedTargetEmail = targetPair.second
        if (resolvedTargetId == firstId) {
            return SwapPlan(firstId, firstEmail, resolvedTargetId, resolvedTargetEmail)
        }

        val auxTables = report.accountAuxTables

        val maxAccountId = (db.queryRows("SELECT COALESCE(MAX(_id), 0) FROM accounts")
            .firstOrNull()?.firstOrNull() as? Number)?.toLong() ?: 0L
        var globalMaxId = maxAccountId
        for (aux in auxTables) {
            val auxMax = (db.queryRows("SELECT COALESCE(MAX(accounts_id), 0) FROM $aux")
                .firstOrNull()?.firstOrNull() as? Number)?.toLong() ?: 0L
            if (auxMax > globalMaxId) globalMaxId = auxMax
        }
        val tempId = (globalMaxId + 1000L).toInt()

        db.checkpointWalTruncate()
        db.execSql("PRAGMA foreign_keys = OFF;")
        db.inTransaction {
            db.execSql("UPDATE accounts SET _id = ? WHERE _id = ?", listOf(tempId, firstId))
            db.execSql("UPDATE accounts SET _id = ? WHERE _id = ?", listOf(firstId, resolvedTargetId))
            db.execSql("UPDATE accounts SET _id = ? WHERE _id = ?", listOf(resolvedTargetId, tempId))

            for (aux in auxTables) {
                db.execSql("UPDATE $aux SET accounts_id = ? WHERE accounts_id = ?", listOf(tempId, firstId))
                db.execSql("UPDATE $aux SET accounts_id = ? WHERE accounts_id = ?", listOf(firstId, resolvedTargetId))
                db.execSql("UPDATE $aux SET accounts_id = ? WHERE accounts_id = ?", listOf(resolvedTargetId, tempId))
            }
        }
        db.checkpointWalTruncate()

        val integrity = db.integrityCheck()
        check(integrity.equals("ok", ignoreCase = true)) {
            "Database failed integrity check: $integrity"
        }

        val fkViolations = db.foreignKeyCheck()
        check(fkViolations.isEmpty()) {
            "Foreign key violation after swap: ${fkViolations.take(3).joinToString(", ")}"
        }

        return SwapPlan(firstId, firstEmail, resolvedTargetId, resolvedTargetEmail)
    }

    fun swapPrimaryGoogleAccount(targetCeId: Int): OperationResult {
        val ceFile = File(RootPaths.ACCOUNTS_CE_DB)
        val deFile = File(RootPaths.ACCOUNTS_DE_DB)
        if (!ceFile.exists() || !deFile.exists()) {
            return OperationResult(false, "System account databases not found")
        }

        RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)
        val stageDir = File(RootPaths.STAGING_ROOT, "sys_${UUID.randomUUID()}")
        if (!RootShell.ensurePrivateDir(stageDir.absolutePath)) {
            return OperationResult(false, "Failed to create staging directory")
        }

        return try {
            val stagedCe = RootShell.copyDbWithSidecars(RootPaths.ACCOUNTS_CE_DB, stageDir)
            val stagedDe = RootShell.copyDbWithSidecars(RootPaths.ACCOUNTS_DE_DB, stageDir)

            val cePlan = SQLiteDatabase.openDatabase(
                stagedCe.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                swapIdsInAccountDb(AndroidSqliteGateway(db), targetId = targetCeId, dbLabel = "CE DB")
            }

            val dePlan = SQLiteDatabase.openDatabase(
                stagedDe.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                swapIdsInAccountDb(AndroidSqliteGateway(db), targetEmail = cePlan.targetEmail, dbLabel = "DE DB")
            }

            val ceChanged = cePlan.firstId != cePlan.targetId
            val deChanged = dePlan.firstId != dePlan.targetId
            if (!ceChanged && !deChanged) {
                return OperationResult(
                    success = true,
                    message = "${cePlan.targetEmail} is already active as primary account",
                    rebootRequired = false
                )
            }

            val finskyPath = RootPaths.finskyXml()
            val finskyFile = File(finskyPath)
            val vendingOwner = RootShell.captureDirectoryOwner(RootPaths.vendingDbDir()) ?: "system:system"
            val capturedMeta = RootShell.captureFilesMeta(
                listOf(RootPaths.ACCOUNTS_CE_DB, RootPaths.ACCOUNTS_DE_DB, finskyPath),
                defaultOwner = "system:system"
            )
            val ceMeta = capturedMeta[RootPaths.ACCOUNTS_CE_DB] ?: FileSecurityMeta("system:system", "660", null)
            val deMeta = capturedMeta[RootPaths.ACCOUNTS_DE_DB] ?: FileSecurityMeta("system:system", "660", null)
            val finskyMeta = capturedMeta[finskyPath] ?: FileSecurityMeta(vendingOwner, "660", null)

            val actionLabel = if (!ceChanged) {
                "Repair DE sync → ${cePlan.targetEmail}"
            } else {
                "Before swap: ${cePlan.firstEmail} → ${cePlan.targetEmail}"
            }
            val backupRes = BackupManager.createSystemBackup(
                origin = BackupOrigin.AUTO,
                summary = actionLabel,
                preCapturedMeta = capturedMeta,
                preResolvedVendingOwner = vendingOwner
            )
            if (!backupRes.success) {
                return OperationResult(false, "Aborted before changes: ${backupRes.message}")
            }

            var stagedFinsky: File? = null
            if (finskyFile.exists()) {
                val updatedXml = FinskyXmlHelper.updateAccountIfPresent(finskyFile.readText(), cePlan.targetEmail)
                if (updatedXml != null) {
                    RootShell.forceStopVending()
                    stagedFinsky = File(stageDir, "finsky.xml").apply { writeText(updatedXml) }
                }
            }

            if (ceChanged) {
                val ceReplace = RootShell.replaceFileSafely(
                    stagedFile = stagedCe,
                    targetPath = RootPaths.ACCOUNTS_CE_DB,
                    meta = ceMeta,
                    sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_CE_DB)
                )
                if (!ceReplace.isSuccess) {
                    return OperationResult(
                        false,
                        "Failed updating system accounts (${backupRes.details} preserved): ${ceReplace.stderr}"
                    )
                }
            }

            if (deChanged) {
                val deReplace = RootShell.replaceFileSafely(
                    stagedFile = stagedDe,
                    targetPath = RootPaths.ACCOUNTS_DE_DB,
                    meta = deMeta,
                    sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_DE_DB)
                )
                if (!deReplace.isSuccess) {
                    val rolledBack = if (ceChanged) {
                        BackupManager.restoreCeOnlyFromBackup(backupRes.details, ceMeta)
                    } else {
                        true
                    }
                    val statusNote = if (rolledBack) "Changes rolled back cleanly." else "Restore backup ${backupRes.details}."
                    return OperationResult(false, "Sync update failed; $statusNote")
                }
            }

            if (stagedFinsky != null) {
                RootShell.replaceFileSafely(stagedFinsky, finskyPath, finskyMeta)
                RootShell.forceStopVending()
            }

            val successMsg = if (!ceChanged && deChanged) {
                "Repaired account order sync for ${cePlan.targetEmail}. Reboot to apply."
            } else {
                "Primary account set to ${cePlan.targetEmail}. Reboot to apply."
            }
            OperationResult(
                success = true,
                message = successMsg,
                details = backupRes.details,
                rebootRequired = true
            )
        } catch (t: Throwable) {
            OperationResult(
                false,
                "Swap aborted safely (no files changed): ${t.message}"
            )
        } finally {
            stageDir.deleteRecursively()
        }
    }

    fun triggerSoftReboot() {
        RootShell.run(
            "sync && nohup /system/bin/sh -c 'sleep 0.4; sync; /system/bin/setprop ctl.restart zygote || (/system/bin/stop && /system/bin/start)' >/dev/null 2>&1 </dev/null &"
        )
    }

    fun triggerFullReboot() {
        RootShell.run(
            "sync && nohup /system/bin/sh -c 'sleep 0.3; sync; /system/bin/svc power reboot || /system/bin/reboot' >/dev/null 2>&1 </dev/null &"
        )
    }
}
