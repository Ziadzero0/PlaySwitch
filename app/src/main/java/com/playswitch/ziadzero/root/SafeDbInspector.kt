package com.playswitch.ziadzero.root

import android.database.sqlite.SQLiteDatabase
import com.playswitch.ziadzero.model.DashboardSnapshot
import com.playswitch.ziadzero.model.EnvironmentStatus
import com.playswitch.ziadzero.model.GoogleAccountInfo
import com.playswitch.ziadzero.model.PlayAppOwnershipInfo
import java.io.File
import java.util.UUID

object SafeDbInspector {
    /**
     * Reads clean DBs (no -wal/-shm/-journal sidecars) directly in READONLY mode with zero disk copies,
     * falling back automatically to isolated RW staging when sidecars exist or locking occurs.
     */
    fun <T> withSafeReadDb(sourceDbPath: String, block: (SQLiteDatabase) -> T): T {
        SqliteCompatFixer.apply()
        val src = File(sourceDbPath)
        require(src.exists()) { "Database not found: $sourceDbPath" }
        val hasSidecars = RootPaths.sqliteSidecarPaths(sourceDbPath).any { File(it).exists() }

        if (!hasSidecars) {
            val directAttempt = runCatching {
                SQLiteDatabase.openDatabase(
                    sourceDbPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                ).use(block)
            }
            if (directAttempt.isSuccess) {
                return directAttempt.getOrThrow()
            }
        }
        return withStagedReadDb(sourceDbPath, block)
    }

    fun <T> withStagedReadDb(sourceDbPath: String, block: (SQLiteDatabase) -> T): T {
        val src = File(sourceDbPath)
        require(src.exists()) { "Database not found: $sourceDbPath" }
        RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)
        val stageDir = File(RootPaths.STAGING_ROOT, "read_${UUID.randomUUID()}")
        if (!RootShell.ensurePrivateDir(stageDir.absolutePath)) {
            error("Failed to create staging directory")
        }

        return try {
            val stagedDb = RootShell.copyDbWithSidecars(sourceDbPath, stageDir)
            val hasWal = File(stageDir, "${src.name}-wal").exists()

            SQLiteDatabase.openDatabase(
                stagedDb.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                if (hasWal) {
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
                }
                block(db)
            }
        } finally {
            stageDir.deleteRecursively()
        }
    }

    fun inspectAccountSection(): Triple<EnvironmentStatus, List<GoogleAccountInfo>, String> {
        val issues = mutableListOf<String>()
        val notes = mutableListOf<String>()

        val ceExists = File(RootPaths.ACCOUNTS_CE_DB).exists()
        val deExists = File(RootPaths.ACCOUNTS_DE_DB).exists()
        val localPath = RootPaths.localAppStateDb()
        val libraryPath = RootPaths.libraryDb()
        val finskyPath = RootPaths.finskyXml()

        val localExists = File(localPath).exists()
        val libraryExists = File(libraryPath).exists()
        val finskyExists = File(finskyPath).exists()

        if (!ceExists) issues += "Missing system accounts store (${RootPaths.ACCOUNTS_CE_DB})"
        if (!deExists) issues += "Missing device accounts store (${RootPaths.ACCOUNTS_DE_DB})"
        if (!localExists) issues += "Missing Play Store app state ($localPath)"

        var ceTables = emptyList<String>()
        var ceRows = emptyList<Pair<Int, String>>()
        var ceSchemaSafe = ceExists
        if (ceExists) {
            runCatching {
                withSafeReadDb(RootPaths.ACCOUNTS_CE_DB) { db ->
                    val report = SchemaGuard.validateAccountDb(AndroidSqliteGateway(db), "CE DB")
                    ceTables = report.tables
                    notes += report.adaptiveNotes
                    if (!report.isSafe) {
                        ceSchemaSafe = false
                        issues += report.blockingIssues
                    } else {
                        ceRows = readGoogleAccountRows(db)
                    }
                }
            }.onFailure {
                ceSchemaSafe = false
                issues += "Failed reading CE accounts: ${it.message}"
            }
        }

        var deTables = emptyList<String>()
        var deRows = emptyList<Pair<Int, String>>()
        var deSchemaSafe = deExists
        val deByName = mutableMapOf<String, Int>()
        if (deExists) {
            runCatching {
                withSafeReadDb(RootPaths.ACCOUNTS_DE_DB) { db ->
                    val report = SchemaGuard.validateAccountDb(AndroidSqliteGateway(db), "DE DB")
                    deTables = report.tables
                    notes += report.adaptiveNotes
                    if (!report.isSafe) {
                        deSchemaSafe = false
                        issues += report.blockingIssues
                    } else {
                        deRows = readGoogleAccountRows(db)
                        deRows.forEach { (id, name) -> deByName[name] = id }
                    }
                }
            }.onFailure {
                deSchemaSafe = false
                issues += "Failed reading DE accounts: ${it.message}"
            }
        }

        val cePrimaryEmail = ceRows.firstOrNull()?.second.orEmpty()
        val dePrimaryEmail = deRows.firstOrNull()?.second.orEmpty()
        if (cePrimaryEmail.isNotEmpty() && dePrimaryEmail.isNotEmpty() &&
            !cePrimaryEmail.equals(dePrimaryEmail, ignoreCase = true)
        ) {
            notes += "Account primary order out of sync between system & device store (tap Repair to fix)"
        }

        val accounts = ceRows.mapIndexed { index, (id, name) ->
            GoogleAccountInfo(
                id = id,
                name = name,
                deId = deByName[name],
                isPrimary = index == 0,
                isDePrimary = dePrimaryEmail.isNotEmpty() && name.equals(dePrimaryEmail, ignoreCase = true)
            )
        }

        var localSchemaSafe = localExists
        var librarySchemaSafe = true
        if (localExists) {
            runCatching {
                withSafeReadDb(localPath) { db ->
                    val report = SchemaGuard.validatePlayAppStateDb(AndroidSqliteGateway(db))
                    notes += report.adaptiveNotes
                    if (!report.isSafe) {
                        localSchemaSafe = false
                        issues += report.blockingIssues
                    }
                }
            }.onFailure {
                localSchemaSafe = false
                issues += "Failed reading Play app state: ${it.message}"
            }
        }
        if (libraryExists) {
            runCatching {
                withSafeReadDb(libraryPath) { db ->
                    val report = SchemaGuard.validatePlayLibraryDb(AndroidSqliteGateway(db))
                    notes += report.adaptiveNotes
                    if (!report.isSafe) {
                        librarySchemaSafe = false
                        issues += report.blockingIssues
                    }
                }
            }.onFailure {
                librarySchemaSafe = false
                issues += "Failed reading Play library: ${it.message}"
            }
        } else if (localExists) {
            notes += "Play Library database not created yet (device app state active)"
        }

        val playActiveAccount = readPlayActiveAccount()
        if (finskyExists && playActiveAccount.isBlank()) {
            notes += "Play Store uses per-account configuration layout"
        }

        val env = EnvironmentStatus(
            rootReady = true,
            ceDbExists = ceExists,
            deDbExists = deExists,
            localAppStateExists = localExists,
            libraryDbExists = libraryExists,
            finskyXmlExists = finskyExists,
            ceTables = ceTables,
            deTables = deTables,
            issues = issues.distinct(),
            accountsEngineReady = ceExists && deExists && ceSchemaSafe && deSchemaSafe,
            playEngineReady = localExists && localSchemaSafe && (!libraryExists || librarySchemaSafe),
            compatibilityNotes = notes.distinct(),
            cePrimaryEmail = cePrimaryEmail,
            dePrimaryEmail = dePrimaryEmail
        )
        return Triple(env, accounts, playActiveAccount)
    }

    fun readDashboardSnapshot(): DashboardSnapshot {
        val (env, accounts, playActive) = inspectAccountSection()
        val apps = if (env.localAppStateExists) readPlayAppsOwnership() else emptyList()
        val backups = BackupManager.listBackups()
        return DashboardSnapshot(
            environment = env,
            accounts = accounts,
            playActiveAccount = playActive,
            apps = apps,
            backups = backups
        )
    }

    fun inspectEnvironment(): EnvironmentStatus = inspectAccountSection().first

    fun readGoogleAccounts(): List<GoogleAccountInfo> = inspectAccountSection().second

    fun readPlayActiveAccount(): String {
        val finsky = File(RootPaths.finskyXml())
        if (!finsky.exists()) return ""
        return runCatching {
            FinskyXmlHelper.readAccount(finsky.readText()).orEmpty()
        }.getOrDefault("")
    }

    fun readPlayAppsOwnership(): List<PlayAppOwnershipInfo> {
        val localPath = RootPaths.localAppStateDb()
        if (!File(localPath).exists()) return emptyList()

        val libraryAccountsByPkg = mutableMapOf<String, LinkedHashSet<String>>()
        val libraryPath = RootPaths.libraryDb()
        if (File(libraryPath).exists()) {
            runCatching {
                withSafeReadDb(libraryPath) { db ->
                    val report = SchemaGuard.validatePlayLibraryDb(AndroidSqliteGateway(db))
                    if (report.isSafe) {
                        db.rawQuery(
                            "SELECT DISTINCT doc_id, account FROM ownership WHERE doc_id IS NOT NULL",
                            null
                        ).use { c ->
                            while (c.moveToNext()) {
                                val pkg = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                                val acc = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                                libraryAccountsByPkg.getOrPut(pkg) { linkedSetOf() }.add(acc)
                            }
                        }
                    }
                }
            }
        }

        return withSafeReadDb(localPath) { db ->
            val report = SchemaGuard.validatePlayAppStateDb(AndroidSqliteGateway(db))
            if (!report.isSafe) return@withSafeReadDb emptyList()

            val sql = if (report.hasUpdateAccountColumn) {
                "SELECT package_name, account, account_for_update FROM appstate ORDER BY package_name ASC"
            } else {
                "SELECT package_name, account, NULL FROM appstate ORDER BY package_name ASC"
            }
            buildList {
                db.rawQuery(sql, null).use { c ->
                    while (c.moveToNext()) {
                        val pkg = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                        val acc = c.getString(1)?.takeIf { it.isNotBlank() }
                        val updateAcc = c.getString(2)?.takeIf { it.isNotBlank() }
                        add(
                            PlayAppOwnershipInfo(
                                packageName = pkg,
                                account = acc,
                                accountForUpdate = updateAcc,
                                libraryAccounts = libraryAccountsByPkg[pkg]?.toList() ?: emptyList()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readGoogleAccountRows(db: SQLiteDatabase): List<Pair<Int, String>> = buildList {
        db.rawQuery(
            "SELECT _id, name FROM accounts WHERE type = 'com.google' ORDER BY _id ASC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                add(c.getInt(0) to c.getString(1))
            }
        }
    }
}
