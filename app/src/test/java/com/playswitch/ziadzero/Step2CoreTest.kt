package com.playswitch.ziadzero

import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.BackupSnapshot
import com.playswitch.ziadzero.model.DashboardSnapshot
import com.playswitch.ziadzero.model.EnvironmentStatus
import com.playswitch.ziadzero.model.GoogleAccountInfo
import com.playswitch.ziadzero.model.OperationResult
import com.playswitch.ziadzero.model.PlayAppOwnershipInfo
import com.playswitch.ziadzero.model.RootJsonCodec
import com.playswitch.ziadzero.root.BackupManager
import com.playswitch.ziadzero.root.FinskyXmlHelper
import com.playswitch.ziadzero.root.RootShell
import com.playswitch.ziadzero.root.SqliteCompatFixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2CoreTest {
    @Test
    fun jsonCodec_roundTripsModelsIncludingNullFields() {
        val accounts = listOf(
            GoogleAccountInfo(id = 1, name = "primary@gmail.com", deId = 1, isPrimary = true, isDePrimary = true),
            GoogleAccountInfo(id = 4, name = "secondary@gmail.com", deId = null, isPrimary = false, isDePrimary = false)
        )
        val decodedAccounts = RootJsonCodec.decodeGoogleAccounts(
            RootJsonCodec.encodeGoogleAccounts(accounts)
        )
        assertEquals(accounts, decodedAccounts)

        val apps = listOf(
            PlayAppOwnershipInfo(
                packageName = "com.example.sideload",
                account = null,
                accountForUpdate = null,
                libraryAccounts = emptyList()
            ),
            PlayAppOwnershipInfo(
                packageName = "com.whatsapp",
                account = "primary@gmail.com",
                accountForUpdate = "primary@gmail.com",
                libraryAccounts = listOf("primary@gmail.com", "old@gmail.com")
            )
        )
        val decodedApps = RootJsonCodec.decodePlayAppsOwnership(
            RootJsonCodec.encodePlayAppsOwnership(apps)
        )
        assertEquals(apps, decodedApps)

        val env = EnvironmentStatus(
            rootReady = true,
            ceDbExists = true,
            deDbExists = true,
            localAppStateExists = true,
            libraryDbExists = false,
            finskyXmlExists = true,
            ceTables = listOf("accounts", "authtokens", "extras"),
            deTables = listOf("accounts", "grants", "visibility"),
            issues = listOf("Missing library.db"),
            accountsEngineReady = true,
            playEngineReady = false,
            compatibilityNotes = listOf("Note A"),
            cePrimaryEmail = "primary@gmail.com",
            dePrimaryEmail = "primary@gmail.com"
        )
        assertEquals(env, RootJsonCodec.decodeEnvironmentStatus(RootJsonCodec.encodeEnvironmentStatus(env)))

        val backups = listOf(
            BackupSnapshot(
                folderName = "sys_20261125_060000",
                kind = BackupKind.SYSTEM,
                timestampMs = 1795586400000L,
                files = listOf("accounts_ce.db", "accounts_de.db", "restore.sh"),
                sizeBytes = 65536L,
                hasRestoreScript = true,
                origin = BackupOrigin.MANUAL,
                summary = "Manual snapshot"
            )
        )
        assertEquals(backups, RootJsonCodec.decodeBackups(RootJsonCodec.encodeBackups(backups)))

        val snapshot = DashboardSnapshot(
            environment = env,
            accounts = accounts,
            playActiveAccount = "primary@gmail.com",
            apps = apps,
            backups = backups
        )
        assertEquals(
            snapshot,
            RootJsonCodec.decodeDashboardSnapshot(RootJsonCodec.encodeDashboardSnapshot(snapshot))
        )

        val res = OperationResult(true, "Done", "extra", rebootRequired = true)
        assertEquals(res, RootJsonCodec.decodeOperationResult(RootJsonCodec.encodeOperationResult(res)))
    }

    @Test
    fun backupFolderValidation_rejectsTraversalAndAcceptsValidNames() {
        assertTrue(BackupManager.isValidFolderName("sys_20261125_060000"))
        assertTrue(BackupManager.isValidFolderName("play_20261125_060000_1"))
        assertFalse(BackupManager.isValidFolderName("../etc"))
        assertFalse(BackupManager.isValidFolderName("sys_20261125_060000/../../x"))
        assertFalse(BackupManager.isValidFolderName("other_20261125_060000"))
        assertFalse(BackupManager.isValidFolderName(""))
    }

    @Test
    fun backupPruning_prunesOnlyMatchingPrefixBeyondLimit() {
        val folders = listOf(
            "sys_20261125_010000",
            "sys_20261125_020000",
            "sys_20261125_030000",
            "sys_20261125_040000",
            "sys_20261125_050000",
            "sys_20261125_060000",
            "sys_20261125_070000",
            "play_20261125_010000",
            "play_20261125_020000"
        )
        val prunedSys = BackupManager.selectFoldersToPrune(folders, "sys_", maxToKeep = 5)
        assertEquals(listOf("sys_20261125_010000", "sys_20261125_020000"), prunedSys)

        val prunedPlay = BackupManager.selectFoldersToPrune(folders, "play_", maxToKeep = 5)
        assertTrue(prunedPlay.isEmpty())
    }

    @Test
    fun backupPruningByOrigin_protectsManualSnapshotsFromAutoOverflow() {
        val candidates = listOf(
            BackupManager.PruneCandidate("play_20261125_010000", BackupOrigin.MANUAL),
            BackupManager.PruneCandidate("play_20261125_020000", BackupOrigin.AUTO),
            BackupManager.PruneCandidate("play_20261125_030000", BackupOrigin.AUTO),
            BackupManager.PruneCandidate("play_20261125_040000", BackupOrigin.AUTO),
            BackupManager.PruneCandidate("play_20261125_050000", BackupOrigin.AUTO),
            BackupManager.PruneCandidate("play_20261125_060000", BackupOrigin.AUTO),
            BackupManager.PruneCandidate("play_20261125_070000", BackupOrigin.AUTO)
        )
        val pruned = BackupManager.selectFoldersToPruneByOrigin(
            candidates = candidates,
            prefix = "play_",
            maxManual = 5,
            maxAuto = 5
        )
        assertEquals(listOf("play_20261125_020000"), pruned)
        assertFalse("play_20261125_010000" in pruned)
    }

    @Test
    fun backupRestoreScript_containsExpectedOwnershipAndRestorecon() {
        val sysScript = BackupManager.buildSystemRestoreScript(
            backupDirPath = "/data/adb/playswitch_backups/sys_20261125_060000",
            ceOwner = "1000:1000",
            deOwner = "1000:1000",
            finskyOwner = "10173:10173",
            finskyPath = "/data/data/com.android.vending/shared_prefs/finsky.xml"
        )
        assertTrue(sysScript.contains("#!/system/bin/sh"))
        assertTrue(sysScript.contains("chown 1000:1000"))
        assertTrue(sysScript.contains("restorecon -v"))
        assertTrue(sysScript.contains("accounts_ce.db"))
        assertTrue(sysScript.contains("accounts_de.db"))
    }

    @Test
    fun finskyXmlHelper_readsUpdatesAndCreatesAccountSafely() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <boolean name="tos_accepted" value="true" />
                <string name="account">old@gmail.com</string>
            </map>
        """.trimIndent()

        assertEquals("old@gmail.com", FinskyXmlHelper.readAccount(xml))
        val updated = FinskyXmlHelper.updateAccount(xml, "new+tag@gmail.com")
        assertEquals("new+tag@gmail.com", FinskyXmlHelper.readAccount(updated))
        assertEquals("new+tag@gmail.com", FinskyXmlHelper.readAccount(FinskyXmlHelper.updateAccountIfPresent(xml, "new+tag@gmail.com")!!))

        val noAccountXml = "<map><boolean name=\"flag\" value=\"true\" /></map>"
        assertNull(FinskyXmlHelper.readAccount(noAccountXml))
        assertNull(FinskyXmlHelper.updateAccountIfPresent(noAccountXml, "fresh@gmail.com"))
        val inserted = FinskyXmlHelper.updateAccount(noAccountXml, "fresh@gmail.com")
        assertEquals("fresh@gmail.com", FinskyXmlHelper.readAccount(inserted))
    }

    @Test
    fun rootShellHelpers_quoteAndParseSelinuxSafely() {
        assertEquals("'hello'\"'\"'world'", RootShell.quoteArg("hello'world"))
        assertEquals("u:object_r:system_data_file:s0",
            RootShell.parseSelinuxFromLsZ("u:object_r:system_data_file:s0 /data/system_ce/0/accounts_ce.db")
        )
        assertNull(RootShell.parseSelinuxFromLsZ("? /some/file"))
    }

    @Test
    fun backupZipRoundTrip_preservesAllowedFilesAndRejectsZipSlipTraversal() {
        val tempRoot = java.nio.file.Files.createTempDirectory("ps_zip_test").toFile()
        try {
            val srcDir = java.io.File(tempRoot, "sys_20261125_120000").apply { mkdirs() }
            java.io.File(srcDir, "accounts_ce.db").writeBytes(byteArrayOf(1, 2, 3, 4))
            java.io.File(srcDir, "accounts_de.db").writeBytes(byteArrayOf(5, 6, 7, 8))
            java.io.File(srcDir, "meta.properties").writeText("kind=SYSTEM\nsummary=Test\n")
            java.io.File(srcDir, "restore.sh").writeText("#!/system/bin/sh\necho ok\n")

            val zipOut = java.io.ByteArrayOutputStream()
            BackupManager.writeFolderToZipStream(srcDir, zipOut)

            val dstDir = java.io.File(tempRoot, "extracted").apply { mkdirs() }
            val extracted = BackupManager.extractValidatedArchive(
                zipOut.toByteArray().inputStream(),
                dstDir
            )
            assertEquals(
                setOf("accounts_ce.db", "accounts_de.db", "meta.properties", "restore.sh"),
                extracted.toSet()
            )
            assertEquals(BackupKind.SYSTEM, BackupManager.inferAndValidateKind(dstDir))

            // Verify Zip-Slip paths throw cleanly
            val maliciousZipOut = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(maliciousZipOut).use { zos ->
                zos.putNextEntry(java.util.zip.ZipEntry("../../etc/passwd"))
                zos.write("bad".toByteArray())
                zos.closeEntry()
            }
            var rejected = false
            try {
                BackupManager.extractValidatedArchive(
                    maliciousZipOut.toByteArray().inputStream(),
                    java.io.File(tempRoot, "evil").apply { mkdirs() }
                )
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun sqliteCompatFixer_isIdempotentAndSafeOutsideAndroidRuntime() {
        SqliteCompatFixer.apply()
        SqliteCompatFixer.apply()
    }
}
