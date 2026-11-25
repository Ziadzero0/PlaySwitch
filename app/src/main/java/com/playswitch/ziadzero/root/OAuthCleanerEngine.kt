package com.playswitch.ziadzero.root

import android.database.sqlite.SQLiteDatabase
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.OperationResult
import java.io.File
import java.util.UUID

object OAuthCleanerEngine {
    private val validPackageRegex = Regex("^[a-zA-Z0-9_.]+$")

    fun escapeLikeLiteral(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    fun mutateOAuthBindings(db: SqliteDbGateway, packageName: String): Int {
        val tables = db.listTables()
        if ("authtokens" !in tables) return 0

        val cols = db.listColumns("authtokens")
        require("type" in cols) { "Account tokens table schema changed ('type' column missing)" }

        val escaped = escapeLikeLiteral(packageName)
        val permPattern = "perm.$escaped:%"
        val expPattern = "EXP:$escaped:%"

        db.checkpointWalTruncate()
        var deletedCount = 0
        db.inTransaction {
            val countRow = db.queryRows(
                "SELECT COUNT(*) FROM authtokens WHERE type LIKE ? ESCAPE '\\' OR type LIKE ? ESCAPE '\\'",
                listOf(permPattern, expPattern)
            )
            deletedCount = (countRow.firstOrNull()?.firstOrNull() as? Number)?.toInt() ?: 0
            if (deletedCount > 0) {
                db.execSql(
                    "DELETE FROM authtokens WHERE type LIKE ? ESCAPE '\\' OR type LIKE ? ESCAPE '\\'",
                    listOf(permPattern, expPattern)
                )
            }
        }
        db.checkpointWalTruncate()

        val integrity = db.integrityCheck()
        check(integrity.equals("ok", ignoreCase = true)) {
            "System accounts failed integrity check: $integrity"
        }
        val fkViolations = db.foreignKeyCheck()
        check(fkViolations.isEmpty()) {
            "Foreign key violation after sign-in reset: ${fkViolations.take(3).joinToString(", ")}"
        }

        return deletedCount
    }

    fun clearAppOAuthBindings(rawPackageName: String): OperationResult {
        val packageName = rawPackageName.trim()
        if (!validPackageRegex.matches(packageName)) {
            return OperationResult(false, "Invalid package name: $packageName")
        }

        val ceFile = File(RootPaths.ACCOUNTS_CE_DB)
        if (!ceFile.exists()) {
            return OperationResult(false, "System accounts store not found")
        }

        RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)
        val stageDir = File(RootPaths.STAGING_ROOT, "oauth_${UUID.randomUUID()}")
        if (!RootShell.ensurePrivateDir(stageDir.absolutePath)) {
            return OperationResult(false, "Failed to create staging directory")
        }

        return try {
            val stagedCe = RootShell.copyDbWithSidecars(RootPaths.ACCOUNTS_CE_DB, stageDir)

            val deleted = SQLiteDatabase.openDatabase(
                stagedCe.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                mutateOAuthBindings(AndroidSqliteGateway(db), packageName)
            }

            if (deleted == 0) {
                return OperationResult(
                    success = true,
                    message = "No saved sign-in links found for $packageName",
                    rebootRequired = false
                )
            }

            val finskyPath = RootPaths.finskyXml()
            val capturedMeta = RootShell.captureFilesMeta(
                listOf(RootPaths.ACCOUNTS_CE_DB, RootPaths.ACCOUNTS_DE_DB, finskyPath),
                defaultOwner = "system:system"
            )
            val ceMeta = capturedMeta[RootPaths.ACCOUNTS_CE_DB] ?: FileSecurityMeta("system:system", "660", null)

            val backupRes = BackupManager.createSystemBackup(
                origin = BackupOrigin.AUTO,
                summary = "Before sign-in reset: ${packageName.substringAfterLast('.')}",
                preCapturedMeta = capturedMeta
            )
            if (!backupRes.success) {
                return OperationResult(false, "Aborted before changes: ${backupRes.message}")
            }

            val replaceRes = RootShell.replaceFileSafely(
                stagedFile = stagedCe,
                targetPath = RootPaths.ACCOUNTS_CE_DB,
                meta = ceMeta,
                sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_CE_DB)
            )
            if (!replaceRes.isSuccess) {
                return OperationResult(
                    false,
                    "Failed clearing sign-in binding (${backupRes.details} preserved): ${replaceRes.stderr}"
                )
            }

            RootShell.run("/system/bin/am force-stop ${RootShell.quoteArg(packageName)}")

            OperationResult(
                success = true,
                message = "Cleared $deleted sign-in link(s) for $packageName. Reboot recommended.",
                details = backupRes.details,
                rebootRequired = true
            )
        } catch (t: Throwable) {
            OperationResult(false, "Sign-in reset aborted safely: ${t.message}")
        } finally {
            stageDir.deleteRecursively()
        }
    }
}
