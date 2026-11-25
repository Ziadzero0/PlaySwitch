package com.playswitch.ziadzero.root

import android.database.sqlite.SQLiteDatabase
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.OperationResult
import com.playswitch.ziadzero.model.OwnershipTransferMode
import java.io.File
import java.util.UUID

object PlayOwnershipEngine {
    fun mutateLocalAppStateDb(
        db: SqliteDbGateway,
        packageNames: List<String>,
        targetEmail: String,
        mode: OwnershipTransferMode = OwnershipTransferMode.BOTH
    ): Pair<Int, List<String>> {
        val report = SchemaGuard.validatePlayAppStateDb(db)
        require(report.isSafe) {
            report.blockingIssues.joinToString("; ")
        }
        val hasUpdateAccount = report.hasUpdateAccountColumn
        if (mode == OwnershipTransferMode.UPDATE_ONLY) {
            require(hasUpdateAccount) {
                "This Play Store build does not separate update accounts"
            }
        }

        val missing = mutableListOf<String>()
        var matched = 0

        db.checkpointWalTruncate()
        db.inTransaction {
            for (pkg in packageNames) {
                val existing = db.queryRows(
                    "SELECT 1 FROM appstate WHERE package_name = ? LIMIT 1",
                    listOf(pkg)
                )
                if (existing.isEmpty()) {
                    missing += pkg
                    continue
                }
                matched++
                when (mode) {
                    OwnershipTransferMode.BOTH -> {
                        if (hasUpdateAccount) {
                            db.execSql(
                                """
                                UPDATE appstate
                                SET account = ?,
                                    account_for_update = CASE
                                        WHEN account_for_update IS NOT NULL AND account_for_update != ''
                                        THEN ?
                                        ELSE account_for_update
                                    END
                                WHERE package_name = ?
                                """.trimIndent(),
                                listOf(targetEmail, targetEmail, pkg)
                            )
                        } else {
                            db.execSql(
                                "UPDATE appstate SET account = ? WHERE package_name = ?",
                                listOf(targetEmail, pkg)
                            )
                        }
                    }

                    OwnershipTransferMode.BILLING_ONLY -> {
                        db.execSql(
                            "UPDATE appstate SET account = ? WHERE package_name = ?",
                            listOf(targetEmail, pkg)
                        )
                    }

                    OwnershipTransferMode.UPDATE_ONLY -> {
                        db.execSql(
                            "UPDATE appstate SET account_for_update = ? WHERE package_name = ?",
                            listOf(targetEmail, pkg)
                        )
                    }
                }
            }
        }
        db.checkpointWalTruncate()

        val integrity = db.integrityCheck()
        check(integrity.equals("ok", ignoreCase = true)) {
            "Play app state failed integrity check: $integrity"
        }

        return matched to missing
    }

    fun mutateLibraryDb(
        db: SqliteDbGateway,
        packageNames: List<String>,
        targetEmail: String
    ) {
        val report = SchemaGuard.validatePlayLibraryDb(db)
        require(report.isSafe) {
            report.blockingIssues.joinToString("; ")
        }

        db.checkpointWalTruncate()
        db.inTransaction {
            for (pkg in packageNames) {
                db.execSql(
                    """
                    UPDATE OR IGNORE ownership
                    SET account = ?
                    WHERE doc_id = ?
                    """.trimIndent(),
                    listOf(targetEmail, pkg)
                )
                db.execSql(
                    """
                    DELETE FROM ownership
                    WHERE doc_id = ?
                      AND (account IS NULL OR account != ?)
                    """.trimIndent(),
                    listOf(pkg, targetEmail)
                )
            }
        }
        db.checkpointWalTruncate()

        val integrity = db.integrityCheck()
        check(integrity.equals("ok", ignoreCase = true)) {
            "Play library failed integrity check: $integrity"
        }
    }

    fun transferOwnership(
        rawPackages: List<String>,
        rawTargetEmail: String,
        mode: OwnershipTransferMode = OwnershipTransferMode.BOTH
    ): OperationResult {
        val targetEmail = rawTargetEmail.trim()
        if (targetEmail.isEmpty() || !targetEmail.contains("@")) {
            return OperationResult(false, "Invalid Google account email")
        }

        val packages = rawPackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (packages.isEmpty()) {
            return OperationResult(false, "No apps selected")
        }

        val localPath = RootPaths.localAppStateDb()
        val libraryPath = RootPaths.libraryDb()
        val localFile = File(localPath)
        val libraryFile = File(libraryPath)

        if (!localFile.exists()) {
            return OperationResult(false, "Play Store database not found")
        }

        val vendingOwner = RootShell.captureDirectoryOwner(RootPaths.vendingDbDir())
            ?: return OperationResult(false, "Unable to read Play Store permissions")

        RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)
        val stageDir = File(RootPaths.STAGING_ROOT, "play_${UUID.randomUUID()}")
        if (!RootShell.ensurePrivateDir(stageDir.absolutePath)) {
            return OperationResult(false, "Failed to create workspace directory")
        }

        return try {
            RootShell.forceStopVending()

            val stagedLocal = RootShell.copyDbWithSidecars(localPath, stageDir)
            val touchLibrary = mode != OwnershipTransferMode.UPDATE_ONLY && libraryFile.exists()
            val stagedLibrary = if (touchLibrary) {
                RootShell.copyDbWithSidecars(libraryPath, stageDir)
            } else {
                null
            }

            // Stage & verify all schema guards BEFORE creating backups or modifying live files.
            val (matchedCount, missingPkgs) = SQLiteDatabase.openDatabase(
                stagedLocal.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                mutateLocalAppStateDb(AndroidSqliteGateway(db), packages, targetEmail, mode)
            }

            if (matchedCount == 0) {
                return OperationResult(
                    false,
                    "Selected app(s) are not registered in Play Store yet"
                )
            }

            var libraryUpdated = false
            if (stagedLibrary != null) {
                SQLiteDatabase.openDatabase(
                    stagedLibrary.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                ).use { db ->
                    mutateLibraryDb(AndroidSqliteGateway(db), packages, targetEmail)
                    libraryUpdated = true
                }
            }

            val finskyPath = RootPaths.finskyXml()
            val capturedMeta = RootShell.captureFilesMeta(
                listOf(localPath, libraryPath, finskyPath),
                defaultOwner = vendingOwner
            )
            val localMeta = capturedMeta[localPath] ?: FileSecurityMeta(vendingOwner, "660", null)
            val libraryMeta = capturedMeta[libraryPath]

            val backupSummary = if (packages.size == 1) {
                "${mode.shortLabel}: ${packages.first().substringAfterLast('.')} → ${targetEmail.substringBefore('@')}"
            } else {
                "${mode.shortLabel}: ${matchedCount} apps → ${targetEmail.substringBefore('@')}"
            }
            val backupRes = BackupManager.createPlayBackup(
                origin = BackupOrigin.AUTO,
                summary = backupSummary,
                preCapturedMeta = capturedMeta,
                preResolvedVendingOwner = vendingOwner
            )
            if (!backupRes.success) {
                return OperationResult(false, "Aborted before changes: ${backupRes.message}")
            }

            val localReplace = RootShell.replaceFileSafely(
                stagedFile = stagedLocal,
                targetPath = localPath,
                meta = localMeta,
                sidecarsToRemove = RootPaths.sqliteSidecarPaths(localPath)
            )
            if (!localReplace.isSuccess) {
                return OperationResult(
                    false,
                    "Failed updating Play app state (backup preserved): ${localReplace.stderr}"
                )
            }

            if (stagedLibrary != null && libraryMeta != null && libraryUpdated) {
                val libReplace = RootShell.replaceFileSafely(
                    stagedFile = stagedLibrary,
                    targetPath = libraryPath,
                    meta = libraryMeta,
                    sidecarsToRemove = RootPaths.sqliteSidecarPaths(libraryPath)
                )
                if (!libReplace.isSuccess) {
                    return OperationResult(
                        false,
                        "Updated billing state, but Play library failed (restore backup ${backupRes.details})"
                    )
                }
            }

            RootShell.forceStopVending()

            val summary = buildString {
                if (packages.size == 1 && matchedCount == 1) {
                    append("Assigned $targetEmail (${mode.shortLabel})")
                } else {
                    append("Updated $matchedCount/${packages.size} apps → $targetEmail (${mode.shortLabel})")
                }
                if (missingPkgs.isNotEmpty()) {
                    append(" • ${missingPkgs.size} not in Play Store")
                }
            }
            OperationResult(true, summary, backupRes.details)
        } catch (t: Throwable) {
            OperationResult(
                false,
                "Transfer aborted safely (no files changed): ${t.message}"
            )
        } finally {
            stageDir.deleteRecursively()
        }
    }
}
