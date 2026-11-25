package com.playswitch.ziadzero

import com.playswitch.ziadzero.root.OAuthCleanerEngine
import com.playswitch.ziadzero.root.SystemAccountEngine
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SystemAccountSqlTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun android16CeAndDeSwap_swapsIdsAcrossAllTablesAndHandlesNoExtrasInDe() {
        val ceFile = File(tempFolder.root, "accounts_ce.db")
        val deFile = File(tempFolder.root, "accounts_de.db")
        val ceDb = CliSqliteGateway(ceFile)
        val deDb = CliSqliteGateway(deFile)

        // Android 16 CE schema
        ceDb.execSql(
            "CREATE TABLE accounts (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL, password TEXT, UNIQUE(name,type));"
        )
        ceDb.execSql(
            "CREATE TABLE authtokens (_id INTEGER PRIMARY KEY AUTOINCREMENT, accounts_id INTEGER NOT NULL, type TEXT NOT NULL, authtoken TEXT, UNIQUE(accounts_id,type));"
        )
        ceDb.execSql(
            "CREATE TABLE extras (_id INTEGER PRIMARY KEY AUTOINCREMENT, accounts_id INTEGER, key TEXT NOT NULL, value TEXT, UNIQUE(accounts_id,key));"
        )

        ceDb.execSql("INSERT INTO accounts(_id, name, type) VALUES (1, 'first@gmail.com', 'com.google')")
        ceDb.execSql("INSERT INTO accounts(_id, name, type) VALUES (2, 'WhatsApp', 'com.whatsapp')")
        ceDb.execSql("INSERT INTO accounts(_id, name, type) VALUES (5, 'target@gmail.com', 'com.google')")
        ceDb.execSql("INSERT INTO authtokens(accounts_id, type, authtoken) VALUES (1, 'oauth2:mail', 'tok_first')")
        ceDb.execSql("INSERT INTO authtokens(accounts_id, type, authtoken) VALUES (5, 'oauth2:mail', 'tok_target')")
        // Orphan row with high accounts_id to verify TEMP_ID avoids PK/FK collision
        ceDb.execSql("INSERT INTO extras(accounts_id, key, value) VALUES (1500, 'orphan', 'keep')")
        ceDb.execSql("INSERT INTO extras(accounts_id, key, value) VALUES (1, 'gaia_id', 'g1')")
        ceDb.execSql("INSERT INTO extras(accounts_id, key, value) VALUES (5, 'gaia_id', 'g5')")

        // Android 16 DE schema (NO extras table, has grants and visibility, plus mismatched IDs vs CE)
        deDb.execSql(
            "CREATE TABLE accounts (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, previous_name TEXT, last_password_entry_time_millis_epoch INTEGER DEFAULT 0, UNIQUE(name,type));"
        )
        deDb.execSql(
            "CREATE TABLE grants (accounts_id INTEGER NOT NULL, auth_token_type TEXT NOT NULL, uid INTEGER NOT NULL, UNIQUE(accounts_id,auth_token_type,uid));"
        )
        deDb.execSql(
            "CREATE TABLE visibility (accounts_id INTEGER NOT NULL, _package TEXT NOT NULL, value INTEGER, PRIMARY KEY(accounts_id,_package));"
        )

        deDb.execSql("INSERT INTO accounts(_id, name, type) VALUES (1, 'first@gmail.com', 'com.google')")
        deDb.execSql("INSERT INTO accounts(_id, name, type) VALUES (3, 'target@gmail.com', 'com.google')")
        deDb.execSql("INSERT INTO grants(accounts_id, auth_token_type, uid) VALUES (1, 'SID', 10100)")
        deDb.execSql("INSERT INTO grants(accounts_id, auth_token_type, uid) VALUES (3, 'SID', 10200)")
        deDb.execSql("INSERT INTO visibility(accounts_id, _package, value) VALUES (1, 'com.pkg.a', 1)")
        deDb.execSql("INSERT INTO visibility(accounts_id, _package, value) VALUES (3, 'com.pkg.b', 2)")

        val cePlan = SystemAccountEngine.swapIdsInAccountDb(ceDb, targetId = 5)
        assertEquals(1, cePlan.firstId)
        assertEquals("first@gmail.com", cePlan.firstEmail)
        assertEquals(5, cePlan.targetId)
        assertEquals("target@gmail.com", cePlan.targetEmail)

        val ceOrdered = ceDb.queryRows(
            "SELECT _id, name FROM accounts WHERE type='com.google' ORDER BY _id ASC"
        )
        assertEquals(listOf(listOf(1L, "target@gmail.com"), listOf(5L, "first@gmail.com")), ceOrdered)

        // Verify authtokens and extras followed the swapped IDs
        assertEquals(
            "tok_target",
            ceDb.queryRows("SELECT authtoken FROM authtokens WHERE accounts_id = 1").single()[0]
        )
        assertEquals(
            "tok_first",
            ceDb.queryRows("SELECT authtoken FROM authtokens WHERE accounts_id = 5").single()[0]
        )
        assertEquals(
            "g5",
            ceDb.queryRows("SELECT value FROM extras WHERE accounts_id = 1").single()[0]
        )
        assertEquals(
            "g1",
            ceDb.queryRows("SELECT value FROM extras WHERE accounts_id = 5").single()[0]
        )
        // Non-Google account untouched
        assertEquals(
            "WhatsApp",
            ceDb.queryRows("SELECT name FROM accounts WHERE _id = 2").single()[0]
        )

        // Now swap DE by targetEmail (where target was _id=3 instead of 5!)
        val dePlan = SystemAccountEngine.swapIdsInAccountDb(deDb, targetEmail = cePlan.targetEmail)
        assertEquals(1, dePlan.firstId)
        assertEquals(3, dePlan.targetId)

        val deOrdered = deDb.queryRows(
            "SELECT _id, name FROM accounts WHERE type='com.google' ORDER BY _id ASC"
        )
        assertEquals(listOf(listOf(1L, "target@gmail.com"), listOf(3L, "first@gmail.com")), deOrdered)
        assertEquals(
            10200L,
            deDb.queryRows("SELECT uid FROM grants WHERE accounts_id = 1").single()[0]
        )
        assertEquals(
            "com.pkg.b",
            deDb.queryRows("SELECT _package FROM visibility WHERE accounts_id = 1").single()[0]
        )
    }

    @Test
    fun legacyDeSchemaWithExtras_swapsExtrasAndGrantsWithoutError() {
        val deFile = File(tempFolder.root, "legacy_de.db")
        val deDb = CliSqliteGateway(deFile)

        deDb.execSql("CREATE TABLE accounts (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL);")
        deDb.execSql("CREATE TABLE extras (accounts_id INTEGER, key TEXT, value TEXT);")
        deDb.execSql("INSERT INTO accounts VALUES (1, 'a@gmail.com', 'com.google')")
        deDb.execSql("INSERT INTO accounts VALUES (2, 'b@gmail.com', 'com.google')")
        deDb.execSql("INSERT INTO extras VALUES (1, 'k', 'valA')")
        deDb.execSql("INSERT INTO extras VALUES (2, 'k', 'valB')")

        SystemAccountEngine.swapIdsInAccountDb(deDb, targetEmail = "b@gmail.com")

        assertEquals(
            "valB",
            deDb.queryRows("SELECT value FROM extras WHERE accounts_id = 1").single()[0]
        )
        assertEquals(
            "valA",
            deDb.queryRows("SELECT value FROM extras WHERE accounts_id = 2").single()[0]
        )
    }

    @Test
    fun oauthCleaner_escapesUnderscoreWildcardAndDeletesOnlyExactPackageTokens() {
        val ceFile = File(tempFolder.root, "oauth_ce.db")
        val ceDb = CliSqliteGateway(ceFile)

        ceDb.execSql("CREATE TABLE authtokens (_id INTEGER PRIMARY KEY, accounts_id INTEGER, type TEXT, authtoken TEXT);")
        ceDb.execSql("INSERT INTO authtokens VALUES (1, 1, 'perm.com.my_app:profile', 't1')")
        ceDb.execSql("INSERT INTO authtokens VALUES (2, 1, 'EXP:com.my_app:email', 't2')")
        // Similar package where '_' would falsely match if not escaped!
        ceDb.execSql("INSERT INTO authtokens VALUES (3, 1, 'perm.com.myXapp:profile', 't3')")
        ceDb.execSql("INSERT INTO authtokens VALUES (4, 1, 'oauth2:mail', 't4')")

        val deleted = OAuthCleanerEngine.mutateOAuthBindings(ceDb, "com.my_app")
        assertEquals(2, deleted)

        val remaining = ceDb.queryRows("SELECT type FROM authtokens ORDER BY _id ASC").map { it[0] }
        assertEquals(listOf("perm.com.myXapp:profile", "oauth2:mail"), remaining)
    }

    @Test
    fun threeAccountSwap_preservesMiddleAccountAndSwapsFirstAndLast() {
        val ceFile = File(tempFolder.root, "three_ce.db")
        val db = CliSqliteGateway(ceFile)

        db.execSql("CREATE TABLE accounts (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL);")
        db.execSql("CREATE TABLE extras (accounts_id INTEGER, key TEXT, value TEXT);")
        db.execSql("INSERT INTO accounts VALUES (1, 'one@gmail.com', 'com.google')")
        db.execSql("INSERT INTO accounts VALUES (2, 'two@gmail.com', 'com.google')")
        db.execSql("INSERT INTO accounts VALUES (4, 'three@gmail.com', 'com.google')")
        db.execSql("INSERT INTO extras VALUES (1, 'tag', 'e1')")
        db.execSql("INSERT INTO extras VALUES (2, 'tag', 'e2')")
        db.execSql("INSERT INTO extras VALUES (4, 'tag', 'e3')")

        SystemAccountEngine.swapIdsInAccountDb(db, targetId = 4)

        val ordered = db.queryRows("SELECT _id, name FROM accounts WHERE type='com.google' ORDER BY _id ASC")
        assertEquals(
            listOf(
                listOf(1L, "three@gmail.com"),
                listOf(2L, "two@gmail.com"),
                listOf(4L, "one@gmail.com")
            ),
            ordered
        )
        assertEquals("e2", db.queryRows("SELECT value FROM extras WHERE accounts_id = 2").single()[0])
    }

    @Test
    fun schemaGuard_blocksAccountSwapWhenTableUsesUnsupportedForeignKeyColumn() {
        val dbFile = File(tempFolder.root, "future_fk_ce.db")
        val db = CliSqliteGateway(dbFile)

        db.execSql("CREATE TABLE accounts (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL);")
        db.execSql(
            """
            CREATE TABLE custom_future_tokens (
                _id INTEGER PRIMARY KEY,
                owner_ref INTEGER NOT NULL REFERENCES accounts(_id)
            );
            """.trimIndent()
        )
        db.execSql("INSERT INTO accounts VALUES (1, 'first@gmail.com', 'com.google')")
        db.execSql("INSERT INTO accounts VALUES (2, 'second@gmail.com', 'com.google')")
        db.execSql("INSERT INTO custom_future_tokens VALUES (10, 1)")

        val report = com.playswitch.ziadzero.root.SchemaGuard.validateAccountDb(db, "Future CE DB")
        assertEquals(false, report.isSafe)
        assertEquals(1, report.blockingIssues.size)

        var threw = false
        try {
            SystemAccountEngine.swapIdsInAccountDb(db, targetId = 2, dbLabel = "Future CE DB")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertEquals(true, threw)
        // Confirm rows remained untouched
        assertEquals(
            "first@gmail.com",
            db.queryRows("SELECT name FROM accounts WHERE _id = 1").single()[0]
        )
    }
}
