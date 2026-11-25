package com.playswitch.ziadzero.root

import android.annotation.SuppressLint
import java.io.File

@SuppressLint("SdCardPath")
object RootPaths {
    const val SYSTEM_CE_DIR = "/data/system_ce/0"
    const val SYSTEM_DE_DIR = "/data/system_de/0"
    const val ACCOUNTS_CE_DB = "$SYSTEM_CE_DIR/accounts_ce.db"
    const val ACCOUNTS_DE_DB = "$SYSTEM_DE_DIR/accounts_de.db"

    const val VENDING_PKG = "com.android.vending"
    const val VENDING_DATA_PRIMARY = "/data/data/$VENDING_PKG"
    const val VENDING_DATA_FALLBACK = "/data/user/0/$VENDING_PKG"

    const val BACKUP_ROOT = "/data/adb/playswitch_backups"
    const val STAGING_ROOT = "/data/adb/playswitch_tmp"
    const val MAX_BACKUPS_PER_KIND = 5

    fun resolveVendingDataDir(): String {
        val primary = File(VENDING_DATA_PRIMARY)
        if (primary.exists()) {
            return runCatching { primary.canonicalPath }.getOrDefault(VENDING_DATA_PRIMARY)
        }
        return VENDING_DATA_FALLBACK
    }

    fun vendingDbDir(): String = "${resolveVendingDataDir()}/databases"
    fun localAppStateDb(): String = "${vendingDbDir()}/localappstate.db"
    fun libraryDb(): String = "${vendingDbDir()}/library.db"
    fun finskyXml(): String = "${resolveVendingDataDir()}/shared_prefs/finsky.xml"

    fun sqliteSidecarPaths(dbPath: String): List<String> = listOf(
        "$dbPath-wal",
        "$dbPath-shm",
        "$dbPath-journal"
    )
}
