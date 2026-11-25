package com.playswitch.ziadzero

import com.playswitch.ziadzero.data.AppInstallStatus
import com.playswitch.ziadzero.data.EnrichedPlayApp
import com.playswitch.ziadzero.root.PlayOwnershipEngine
import com.playswitch.ziadzero.ui.AccountFilter
import com.playswitch.ziadzero.ui.MainViewModel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayOwnershipSqlTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun localAppState_updatesPlayAndSideloadedAppsCorrectly() {
        val dbFile = File(tempFolder.root, "localappstate.db")
        val db = CliSqliteGateway(dbFile)

        db.execSql(
            """
            CREATE TABLE appstate (
                package_name TEXT PRIMARY KEY,
                account TEXT,
                account_for_update TEXT
            );
            """.trimIndent()
        )
        db.execSql(
            "INSERT INTO appstate(package_name, account, account_for_update) VALUES (?, ?, ?)",
            listOf("com.whatsapp", "old@gmail.com", "old@gmail.com")
        )
        db.execSql(
            "INSERT INTO appstate(package_name, account, account_for_update) VALUES (?, ?, ?)",
            listOf("com.sideload.app", null, "")
        )
        db.execSql(
            "INSERT INTO appstate(package_name, account, account_for_update) VALUES (?, ?, ?)",
            listOf("com.untouched.app", "keep@gmail.com", "keep@gmail.com")
        )

        val (matched, missing) = PlayOwnershipEngine.mutateLocalAppStateDb(
            db = db,
            packageNames = listOf("com.whatsapp", "com.sideload.app", "com.missing.app"),
            targetEmail = "target@gmail.com"
        )

        assertEquals(2, matched)
        assertEquals(listOf("com.missing.app"), missing)

        val whatsapp = db.queryRows(
            "SELECT account, account_for_update FROM appstate WHERE package_name = ?",
            listOf("com.whatsapp")
        ).single()
        assertEquals("target@gmail.com", whatsapp[0])
        assertEquals("target@gmail.com", whatsapp[1])

        // Sideloaded app gets account assigned, while empty account_for_update stays untouched
        val sideload = db.queryRows(
            "SELECT account, account_for_update FROM appstate WHERE package_name = ?",
            listOf("com.sideload.app")
        ).single()
        assertEquals("target@gmail.com", sideload[0])
        assertEquals("", sideload[1])

        val untouched = db.queryRows(
            "SELECT account, account_for_update FROM appstate WHERE package_name = ?",
            listOf("com.untouched.app")
        ).single()
        assertEquals("keep@gmail.com", untouched[0])
        assertEquals("keep@gmail.com", untouched[1])
    }

    @Test
    fun libraryDb_handlesCompositePkDuplicatesAndNullRowsWithoutConstraintFailure() {
        val dbFile = File(tempFolder.root, "library.db")
        val db = CliSqliteGateway(dbFile)

        db.execSql(
            """
            CREATE TABLE ownership (
                account TEXT,
                library_id TEXT,
                backend INTEGER,
                doc_id TEXT,
                doc_type INTEGER,
                offer_type INTEGER,
                PRIMARY KEY (account, library_id, backend, doc_id, doc_type, offer_type)
            );
            """.trimIndent()
        )

        // Case 1: app already owned by BOTH old@ and target@ (would crash plain UPDATE)
        db.execSql(
            "INSERT INTO ownership VALUES (?, 'u-wl', 3, 'com.whatsapp', 1, 1)",
            listOf("old@gmail.com")
        )
        db.execSql(
            "INSERT INTO ownership VALUES (?, 'u-wl', 3, 'com.whatsapp', 1, 1)",
            listOf("target@gmail.com")
        )

        // Case 2: app owned by two old accounts (neither is target@) plus a NULL account row
        db.execSql(
            "INSERT INTO ownership VALUES (?, 'u-wl', 3, 'com.game', 1, 1)",
            listOf("first@gmail.com")
        )
        db.execSql(
            "INSERT INTO ownership VALUES (?, 'u-wl', 3, 'com.game', 1, 1)",
            listOf("second@gmail.com")
        )
        db.execSql(
            "INSERT INTO ownership VALUES (NULL, 'u-wl', 3, 'com.game', 1, 1)"
        )

        // Case 3: unrelated app must stay untouched
        db.execSql(
            "INSERT INTO ownership VALUES (?, 'u-wl', 3, 'com.other', 1, 1)",
            listOf("old@gmail.com")
        )

        PlayOwnershipEngine.mutateLibraryDb(
            db = db,
            packageNames = listOf("com.whatsapp", "com.game"),
            targetEmail = "target@gmail.com"
        )

        val whatsappAccounts = db.queryRows(
            "SELECT account FROM ownership WHERE doc_id = 'com.whatsapp'"
        ).map { it[0] }
        assertEquals(listOf("target@gmail.com"), whatsappAccounts)

        val gameAccounts = db.queryRows(
            "SELECT account FROM ownership WHERE doc_id = 'com.game'"
        ).map { it[0] }
        assertEquals(listOf("target@gmail.com"), gameAccounts)

        val otherAccounts = db.queryRows(
            "SELECT account FROM ownership WHERE doc_id = 'com.other'"
        ).map { it[0] }
        assertEquals(listOf("old@gmail.com"), otherAccounts)
    }

    @Test
    fun splitBillingAndUpdateModes_modifyOnlyTargetColumn() {
        val dbFile = File(tempFolder.root, "split_appstate.db")
        val db = CliSqliteGateway(dbFile)

        db.execSql(
            """
            CREATE TABLE appstate (
                package_name TEXT PRIMARY KEY,
                account TEXT,
                account_for_update TEXT
            );
            """.trimIndent()
        )
        db.execSql(
            "INSERT INTO appstate VALUES (?, ?, ?)",
            listOf("com.split.app", "billing@gmail.com", "update@gmail.com")
        )

        PlayOwnershipEngine.mutateLocalAppStateDb(
            db = db,
            packageNames = listOf("com.split.app"),
            targetEmail = "newbilling@gmail.com",
            mode = com.playswitch.ziadzero.model.OwnershipTransferMode.BILLING_ONLY
        )
        var row = db.queryRows("SELECT account, account_for_update FROM appstate WHERE package_name = 'com.split.app'").single()
        assertEquals("newbilling@gmail.com", row[0])
        assertEquals("update@gmail.com", row[1])

        PlayOwnershipEngine.mutateLocalAppStateDb(
            db = db,
            packageNames = listOf("com.split.app"),
            targetEmail = "newupdate@gmail.com",
            mode = com.playswitch.ziadzero.model.OwnershipTransferMode.UPDATE_ONLY
        )
        row = db.queryRows("SELECT account, account_for_update FROM appstate WHERE package_name = 'com.split.app'").single()
        assertEquals("newbilling@gmail.com", row[0])
        assertEquals("newupdate@gmail.com", row[1])
    }

    @Test
    fun schemaGuard_blocksPlayLibraryIfOwnershipTableMissingOrChanged() {
        val dbFile = File(tempFolder.root, "changed_library.db")
        val db = CliSqliteGateway(dbFile)
        db.execSql("CREATE TABLE new_entitlements (id INTEGER PRIMARY KEY, pkg TEXT);")

        val report = com.playswitch.ziadzero.root.SchemaGuard.validatePlayLibraryDb(db)
        assertEquals(false, report.isSafe)
        assertTrue(report.blockingIssues.isNotEmpty())
    }

    @Test
    fun appFilter_filtersByUserUnassignedAccountStatusAndSearchQuery() {
        val sample = listOf(
            EnrichedPlayApp("com.whatsapp", "WhatsApp", AppInstallStatus.INSTALLED, false, "a@gmail.com", "a@gmail.com", emptyList()),
            EnrichedPlayApp("com.local.mod", "Mod Tool", AppInstallStatus.DISABLED, false, null, null, emptyList()),
            EnrichedPlayApp("com.split.pkg", "Split App", AppInstallStatus.INSTALLED, false, "a@gmail.com", "b@gmail.com", emptyList()),
            EnrichedPlayApp("com.android.vending", "Google Play Store", AppInstallStatus.INSTALLED, true, "a@gmail.com", "a@gmail.com", emptyList())
        )

        assertEquals(
            listOf("com.whatsapp", "com.local.mod", "com.split.pkg"),
            MainViewModel.filterApps(sample, "", includeSystemApps = false, accountFilter = AccountFilter.All).map { it.packageName }
        )
        assertEquals(
            listOf("com.local.mod"),
            MainViewModel.filterApps(sample, "", includeSystemApps = false, accountFilter = AccountFilter.Unassigned).map { it.packageName }
        )
        assertEquals(
            listOf("com.whatsapp", "com.split.pkg", "com.android.vending"),
            MainViewModel.filterApps(sample, "", includeSystemApps = true, accountFilter = AccountFilter.Email("a@gmail.com")).map { it.packageName }
        )
        assertEquals(
            listOf("com.whatsapp", "com.split.pkg"),
            MainViewModel.filterApps(sample, "", includeSystemApps = false, accountFilter = AccountFilter.Email("a@gmail.com")).map { it.packageName }
        )
        assertEquals(
            listOf("com.local.mod"),
            MainViewModel.filterApps(sample, "", includeSystemApps = true, accountFilter = AccountFilter.All, com.playswitch.ziadzero.ui.AppStatusFilter.DISABLED).map { it.packageName }
        )
        assertEquals(
            listOf("com.split.pkg"),
            MainViewModel.filterApps(sample, "", includeSystemApps = true, accountFilter = AccountFilter.All, com.playswitch.ziadzero.ui.AppStatusFilter.SPLIT_ACCOUNTS).map { it.packageName }
        )
        assertTrue(
            MainViewModel.filterApps(sample, "whats", includeSystemApps = false, accountFilter = AccountFilter.All).map { it.packageName } == listOf("com.whatsapp")
        )
    }
}
