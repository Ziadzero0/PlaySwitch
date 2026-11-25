package com.playswitch.ziadzero.root

import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.system.Os
import com.playswitch.ziadzero.model.BackupKind
import com.playswitch.ziadzero.model.BackupOrigin
import com.playswitch.ziadzero.model.BackupSnapshot
import com.playswitch.ziadzero.model.OperationResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    data class PruneCandidate(
        val folderName: String,
        val origin: BackupOrigin
    )

    private val validFolderRegex = Regex("^(sys|play)_[0-9]{8}_[0-9]{6}(_[0-9]{1,3})?$")
    private val allowedBackupFileRegex = Regex(
        "^(meta\\.properties|restore\\.sh|finsky\\.xml|(accounts_ce|accounts_de|localappstate|library)\\.db(-wal|-shm|-journal)?)$"
    )
    private const val META_FILE = "meta.properties"
    private const val RESTORE_SCRIPT = "restore.sh"
    private const val MAX_IMPORT_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L

    fun isValidFolderName(name: String): Boolean = validFolderRegex.matches(name)

    fun selectFoldersToPrune(
        folderNames: List<String>,
        prefix: String,
        maxToKeep: Int = RootPaths.MAX_BACKUPS_PER_KIND
    ): List<String> {
        val matching = folderNames
            .filter { it.startsWith(prefix) && isValidFolderName(it) }
            .sorted()
        val excess = matching.size - maxToKeep
        return if (excess > 0) matching.take(excess) else emptyList()
    }

    /**
     * Prunes MANUAL and AUTO snapshots separately so automated pre-edit backups
     * never evict deliberate manual user snapshots.
     */
    fun selectFoldersToPruneByOrigin(
        candidates: List<PruneCandidate>,
        prefix: String,
        maxManual: Int = RootPaths.MAX_BACKUPS_PER_KIND,
        maxAuto: Int = RootPaths.MAX_BACKUPS_PER_KIND
    ): List<String> {
        val byOrigin = candidates
            .filter { it.folderName.startsWith(prefix) && isValidFolderName(it.folderName) }
            .groupBy { it.origin }

        val toRemove = mutableListOf<String>()
        listOf(
            BackupOrigin.MANUAL to maxManual,
            BackupOrigin.AUTO to maxAuto
        ).forEach { (origin, limit) ->
            val sorted = byOrigin[origin].orEmpty().map { it.folderName }.sorted()
            val excess = sorted.size - limit
            if (excess > 0) {
                toRemove += sorted.take(excess)
            }
        }
        return toRemove
    }

    fun buildSystemRestoreScript(
        backupDirPath: String,
        ceOwner: String,
        deOwner: String,
        finskyOwner: String,
        finskyPath: String
    ): String = """
        |#!/system/bin/sh
        |set -e
        |BKP=${RootShell.quoteArg(backupDirPath)}
        |/system/bin/am force-stop ${RootPaths.VENDING_PKG} || true
        |if [ -f "${'$'}BKP/accounts_ce.db" ]; then
        |  cp -f "${'$'}BKP/accounts_ce.db" ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}.ps_tmp")}
        |  chown $ceOwner ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}.ps_tmp")}
        |  chmod 660 ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}.ps_tmp")}
        |  mv -f ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}.ps_tmp")} ${RootShell.quoteArg(RootPaths.ACCOUNTS_CE_DB)}
        |  rm -f ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}-wal")} ${RootShell.quoteArg("${RootPaths.ACCOUNTS_CE_DB}-shm")}
        |  for ext in -wal -shm; do
        |    if [ -f "${'$'}BKP/accounts_ce.db${'$'}ext" ]; then
        |      cp -f "${'$'}BKP/accounts_ce.db${'$'}ext" "${RootPaths.ACCOUNTS_CE_DB}${'$'}ext"
        |      chown $ceOwner "${RootPaths.ACCOUNTS_CE_DB}${'$'}ext"
        |      chmod 660 "${RootPaths.ACCOUNTS_CE_DB}${'$'}ext"
        |    fi
        |  done
        |  restorecon -v ${RootShell.quoteArg(RootPaths.ACCOUNTS_CE_DB)} >/dev/null 2>&1 || true
        |fi
        |if [ -f "${'$'}BKP/accounts_de.db" ]; then
        |  cp -f "${'$'}BKP/accounts_de.db" ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}.ps_tmp")}
        |  chown $deOwner ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}.ps_tmp")}
        |  chmod 660 ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}.ps_tmp")}
        |  mv -f ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}.ps_tmp")} ${RootShell.quoteArg(RootPaths.ACCOUNTS_DE_DB)}
        |  rm -f ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}-wal")} ${RootShell.quoteArg("${RootPaths.ACCOUNTS_DE_DB}-shm")}
        |  for ext in -wal -shm; do
        |    if [ -f "${'$'}BKP/accounts_de.db${'$'}ext" ]; then
        |      cp -f "${'$'}BKP/accounts_de.db${'$'}ext" "${RootPaths.ACCOUNTS_DE_DB}${'$'}ext"
        |      chown $deOwner "${RootPaths.ACCOUNTS_DE_DB}${'$'}ext"
        |      chmod 660 "${RootPaths.ACCOUNTS_DE_DB}${'$'}ext"
        |    fi
        |  done
        |  restorecon -v ${RootShell.quoteArg(RootPaths.ACCOUNTS_DE_DB)} >/dev/null 2>&1 || true
        |fi
        |if [ -f "${'$'}BKP/finsky.xml" ]; then
        |  cp -f "${'$'}BKP/finsky.xml" ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  chown $finskyOwner ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  chmod 660 ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  mv -f ${RootShell.quoteArg("$finskyPath.ps_tmp")} ${RootShell.quoteArg(finskyPath)}
        |  restorecon -v ${RootShell.quoteArg(finskyPath)} >/dev/null 2>&1 || true
        |fi
        |sync
        |echo "Restore complete. Reboot recommended."
    """.trimMargin() + "\n"

    fun buildPlayRestoreScript(
        backupDirPath: String,
        vendingOwner: String,
        localAppStatePath: String,
        libraryPath: String,
        finskyPath: String
    ): String = """
        |#!/system/bin/sh
        |set -e
        |BKP=${RootShell.quoteArg(backupDirPath)}
        |/system/bin/am force-stop ${RootPaths.VENDING_PKG} || true
        |for name_target in "localappstate.db:$localAppStatePath" "library.db:$libraryPath"; do
        |  fname=${'$'}{name_target%%:*}
        |  target=${'$'}{name_target#*:}
        |  if [ -f "${'$'}BKP/${'$'}fname" ]; then
        |    cp -f "${'$'}BKP/${'$'}fname" "${'$'}target.ps_tmp"
        |    chown $vendingOwner "${'$'}target.ps_tmp"
        |    chmod 660 "${'$'}target.ps_tmp"
        |    mv -f "${'$'}target.ps_tmp" "${'$'}target"
        |    rm -f "${'$'}target-journal" "${'$'}target-wal" "${'$'}target-shm"
        |    if [ -f "${'$'}BKP/${'$'}fname-journal" ]; then
        |      cp -f "${'$'}BKP/${'$'}fname-journal" "${'$'}target-journal"
        |      chown $vendingOwner "${'$'}target-journal"
        |      chmod 660 "${'$'}target-journal"
        |    fi
        |    restorecon -v "${'$'}target" >/dev/null 2>&1 || true
        |  fi
        |done
        |if [ -f "${'$'}BKP/finsky.xml" ]; then
        |  cp -f "${'$'}BKP/finsky.xml" ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  chown $vendingOwner ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  chmod 660 ${RootShell.quoteArg("$finskyPath.ps_tmp")}
        |  mv -f ${RootShell.quoteArg("$finskyPath.ps_tmp")} ${RootShell.quoteArg(finskyPath)}
        |  restorecon -v ${RootShell.quoteArg(finskyPath)} >/dev/null 2>&1 || true
        |fi
        |/system/bin/am force-stop ${RootPaths.VENDING_PKG} || true
        |sync
        |echo "Play Store restore complete."
    """.trimMargin() + "\n"

    fun createSystemBackup(
        origin: BackupOrigin = BackupOrigin.AUTO,
        summary: String = "",
        preCapturedMeta: Map<String, FileSecurityMeta> = emptyMap(),
        preResolvedVendingOwner: String? = null
    ): OperationResult {
        if (!RootShell.ensurePrivateDir(RootPaths.BACKUP_ROOT)) {
            return OperationResult(false, "Failed to initialize backup directory")
        }
        val ceDb = File(RootPaths.ACCOUNTS_CE_DB)
        val deDb = File(RootPaths.ACCOUNTS_DE_DB)
        if (!ceDb.exists() || !deDb.exists()) {
            return OperationResult(false, "System account files not found")
        }

        val folder = allocateFolder(BackupKind.SYSTEM.prefix)
        if (!RootShell.ensurePrivateDir(folder.absolutePath)) {
            return OperationResult(false, "Failed to create backup folder")
        }

        return runCatching {
            val finskyPath = RootPaths.finskyXml()
            val finskyFile = File(finskyPath)
            val vendingOwner = preResolvedVendingOwner
                ?: RootShell.captureDirectoryOwner(RootPaths.vendingDbDir())
                ?: "system:system"

            val missingMetaPaths = listOf(RootPaths.ACCOUNTS_CE_DB, RootPaths.ACCOUNTS_DE_DB, finskyPath)
                .filter { it !in preCapturedMeta && File(it).exists() }
            val metaMap = preCapturedMeta + if (missingMetaPaths.isNotEmpty()) {
                RootShell.captureFilesMeta(missingMetaPaths, "system:system")
            } else {
                emptyMap()
            }

            val ceMeta = metaMap[RootPaths.ACCOUNTS_CE_DB] ?: FileSecurityMeta("system:system", "660", null)
            val deMeta = metaMap[RootPaths.ACCOUNTS_DE_DB] ?: FileSecurityMeta("system:system", "660", null)
            val finskyMeta = metaMap[finskyPath] ?: FileSecurityMeta(vendingOwner, "660", null)

            RootShell.copyDbWithSidecars(RootPaths.ACCOUNTS_CE_DB, folder)
            RootShell.copyDbWithSidecars(RootPaths.ACCOUNTS_DE_DB, folder)
            if (finskyFile.exists()) {
                finskyFile.copyTo(File(folder, "finsky.xml"), overwrite = true)
            }

            val resolvedSummary = summary.ifBlank {
                if (origin == BackupOrigin.MANUAL) "Manual system accounts snapshot" else "System accounts snapshot"
            }

            val props = Properties().apply {
                setProperty("kind", BackupKind.SYSTEM.name)
                setProperty("origin", origin.name)
                setProperty("summary", resolvedSummary)
                setProperty("created_ms", System.currentTimeMillis().toString())
                setProperty("ce_owner", ceMeta.ownerUidGid)
                setProperty("ce_mode", ceMeta.mode)
                ceMeta.selinuxContext?.let { setProperty("ce_selinux", it) }
                setProperty("de_owner", deMeta.ownerUidGid)
                setProperty("de_mode", deMeta.mode)
                deMeta.selinuxContext?.let { setProperty("de_selinux", it) }
                setProperty("finsky_path", finskyPath)
                setProperty("finsky_owner", finskyMeta.ownerUidGid)
                setProperty("finsky_mode", finskyMeta.mode)
                finskyMeta.selinuxContext?.let { setProperty("finsky_selinux", it) }
            }
            File(folder, META_FILE).outputStream().use { props.store(it, null) }

            val script = buildSystemRestoreScript(
                backupDirPath = folder.absolutePath,
                ceOwner = ceMeta.ownerUidGid,
                deOwner = deMeta.ownerUidGid,
                finskyOwner = finskyMeta.ownerUidGid,
                finskyPath = finskyPath
            )
            val scriptFile = File(folder, RESTORE_SCRIPT)
            scriptFile.writeText(script)

            lockDownBackupFolder(folder, scriptFile)
            pruneOldBackups(BackupKind.SYSTEM.prefix)
            OperationResult(true, "Saved system backup", folder.name)
        }.getOrElse { err ->
            folder.deleteRecursively()
            OperationResult(false, "System backup failed: ${err.message}")
        }
    }

    fun createPlayBackup(
        origin: BackupOrigin = BackupOrigin.AUTO,
        summary: String = "",
        preCapturedMeta: Map<String, FileSecurityMeta> = emptyMap(),
        preResolvedVendingOwner: String? = null
    ): OperationResult {
        if (!RootShell.ensurePrivateDir(RootPaths.BACKUP_ROOT)) {
            return OperationResult(false, "Failed to initialize backup directory")
        }
        val localPath = RootPaths.localAppStateDb()
        val libraryPath = RootPaths.libraryDb()
        if (!File(localPath).exists() && !File(libraryPath).exists()) {
            return OperationResult(false, "Play Store databases not found")
        }

        val folder = allocateFolder(BackupKind.PLAY.prefix)
        if (!RootShell.ensurePrivateDir(folder.absolutePath)) {
            return OperationResult(false, "Failed to create Play backup folder")
        }

        return runCatching {
            val finskyPath = RootPaths.finskyXml()
            val vendingOwner = preResolvedVendingOwner
                ?: RootShell.captureDirectoryOwner(RootPaths.vendingDbDir())
                ?: "0:0"

            val missingMetaPaths = listOf(localPath, libraryPath, finskyPath)
                .filter { it !in preCapturedMeta && File(it).exists() }
            val metaMap = preCapturedMeta + if (missingMetaPaths.isNotEmpty()) {
                RootShell.captureFilesMeta(missingMetaPaths, vendingOwner)
            } else {
                emptyMap()
            }

            val localMeta = metaMap[localPath] ?: FileSecurityMeta(vendingOwner, "660", null)
            val libraryMeta = metaMap[libraryPath] ?: FileSecurityMeta(vendingOwner, "660", null)
            val finskyMeta = metaMap[finskyPath] ?: FileSecurityMeta(vendingOwner, "660", null)

            if (File(localPath).exists()) RootShell.copyDbWithSidecars(localPath, folder)
            if (File(libraryPath).exists()) RootShell.copyDbWithSidecars(libraryPath, folder)
            if (File(finskyPath).exists()) {
                File(finskyPath).copyTo(File(folder, "finsky.xml"), overwrite = true)
            }

            val resolvedSummary = summary.ifBlank {
                if (origin == BackupOrigin.MANUAL) "Manual Play Store snapshot" else "Play Store snapshot"
            }

            val props = Properties().apply {
                setProperty("kind", BackupKind.PLAY.name)
                setProperty("origin", origin.name)
                setProperty("summary", resolvedSummary)
                setProperty("created_ms", System.currentTimeMillis().toString())
                setProperty("vending_owner", vendingOwner)
                setProperty("local_path", localPath)
                setProperty("local_mode", localMeta.mode)
                localMeta.selinuxContext?.let { setProperty("local_selinux", it) }
                setProperty("library_path", libraryPath)
                setProperty("library_mode", libraryMeta.mode)
                libraryMeta.selinuxContext?.let { setProperty("library_selinux", it) }
                setProperty("finsky_path", finskyPath)
                setProperty("finsky_mode", finskyMeta.mode)
                finskyMeta.selinuxContext?.let { setProperty("finsky_selinux", it) }
            }
            File(folder, META_FILE).outputStream().use { props.store(it, null) }

            val script = buildPlayRestoreScript(
                backupDirPath = folder.absolutePath,
                vendingOwner = vendingOwner,
                localAppStatePath = localPath,
                libraryPath = libraryPath,
                finskyPath = finskyPath
            )
            val scriptFile = File(folder, RESTORE_SCRIPT)
            scriptFile.writeText(script)

            lockDownBackupFolder(folder, scriptFile)
            pruneOldBackups(BackupKind.PLAY.prefix)
            OperationResult(true, "Saved Play Store backup", folder.name)
        }.getOrElse { err ->
            folder.deleteRecursively()
            OperationResult(false, "Play backup failed: ${err.message}")
        }
    }

    fun listBackups(): List<BackupSnapshot> {
        val root = File(RootPaths.BACKUP_ROOT)
        if (!root.exists()) return emptyList()
        val dirs = root.listFiles()?.filter { it.isDirectory && isValidFolderName(it.name) } ?: return emptyList()

        return dirs.mapNotNull { dir ->
            val kind = BackupKind.fromFolderName(dir.name) ?: return@mapNotNull null
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            val props = loadProperties(File(dir, META_FILE))
            val timestampMs = props.getProperty("created_ms")?.toLongOrNull() ?: dir.lastModified()
            val origin = BackupOrigin.fromValue(props.getProperty("origin"))
            val summary = props.getProperty("summary").orEmpty()
            BackupSnapshot(
                folderName = dir.name,
                kind = kind,
                timestampMs = timestampMs,
                files = files.map { it.name }.sorted(),
                sizeBytes = files.sumOf { it.length() },
                hasRestoreScript = File(dir, RESTORE_SCRIPT).exists(),
                origin = origin,
                summary = summary
            )
        }.sortedByDescending { it.timestampMs }
    }

    fun restoreBackup(folderName: String): OperationResult {
        if (!isValidFolderName(folderName)) {
            return OperationResult(false, "Invalid backup name")
        }
        val dir = File(RootPaths.BACKUP_ROOT, folderName)
        if (!dir.exists() || !dir.isDirectory) {
            return OperationResult(false, "Backup not found: $folderName")
        }

        val kind = BackupKind.fromFolderName(folderName)
            ?: return OperationResult(false, "Unknown backup type")

        val props = loadProperties(File(dir, META_FILE))
        return when (kind) {
            BackupKind.SYSTEM -> restoreSystemBackup(dir, props)
            BackupKind.PLAY -> restorePlayBackup(dir, props)
        }
    }

    fun restoreCeOnlyFromBackup(folderName: String, ceMeta: FileSecurityMeta): Boolean {
        val dir = File(RootPaths.BACKUP_ROOT, folderName)
        val ceBackup = File(dir, "accounts_ce.db")
        if (!ceBackup.exists()) return false
        val ceRes = RootShell.replaceFileSafely(
            stagedFile = ceBackup,
            targetPath = RootPaths.ACCOUNTS_CE_DB,
            meta = ceMeta,
            sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_CE_DB)
        )
        if (!ceRes.isSuccess) return false
        restoreSidecarsIfPresent(dir, "accounts_ce.db", RootPaths.ACCOUNTS_CE_DB, ceMeta)
        return true
    }

    fun deleteBackup(folderName: String): Boolean {
        if (!isValidFolderName(folderName)) return false
        val dir = File(RootPaths.BACKUP_ROOT, folderName)
        if (!dir.exists() || !dir.isDirectory) return false
        return dir.deleteRecursively()
    }

    private fun restoreSystemBackup(dir: File, props: Properties): OperationResult {
        val ceFile = File(dir, "accounts_ce.db")
        val deFile = File(dir, "accounts_de.db")
        if (!ceFile.exists() || !deFile.exists()) {
            return OperationResult(false, "Backup is incomplete")
        }

        val ceMeta = FileSecurityMeta(
            ownerUidGid = props.getProperty("ce_owner", "system:system"),
            mode = props.getProperty("ce_mode", "660"),
            selinuxContext = props.getProperty("ce_selinux")
        )
        val deMeta = FileSecurityMeta(
            ownerUidGid = props.getProperty("de_owner", "system:system"),
            mode = props.getProperty("de_mode", "660"),
            selinuxContext = props.getProperty("de_selinux")
        )

        RootShell.forceStopVending()

        val ceRes = RootShell.replaceFileSafely(
            stagedFile = ceFile,
            targetPath = RootPaths.ACCOUNTS_CE_DB,
            meta = ceMeta,
            sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_CE_DB)
        )
        if (!ceRes.isSuccess) {
            return OperationResult(false, "Failed to restore accounts: ${ceRes.stderr}")
        }
        restoreSidecarsIfPresent(dir, "accounts_ce.db", RootPaths.ACCOUNTS_CE_DB, ceMeta)

        val deRes = RootShell.replaceFileSafely(
            stagedFile = deFile,
            targetPath = RootPaths.ACCOUNTS_DE_DB,
            meta = deMeta,
            sidecarsToRemove = RootPaths.sqliteSidecarPaths(RootPaths.ACCOUNTS_DE_DB)
        )
        if (!deRes.isSuccess) {
            return OperationResult(false, "Failed to restore device accounts: ${deRes.stderr}")
        }
        restoreSidecarsIfPresent(dir, "accounts_de.db", RootPaths.ACCOUNTS_DE_DB, deMeta)

        val finskyBackup = File(dir, "finsky.xml")
        if (finskyBackup.exists()) {
            val finskyTarget = props.getProperty("finsky_path", RootPaths.finskyXml())
            val finskyMeta = FileSecurityMeta(
                ownerUidGid = props.getProperty("finsky_owner", "system:system"),
                mode = props.getProperty("finsky_mode", "660"),
                selinuxContext = props.getProperty("finsky_selinux")
            )
            RootShell.replaceFileSafely(finskyBackup, finskyTarget, finskyMeta)
        }

        return OperationResult(
            success = true,
            message = "System accounts restored. Reboot to apply.",
            rebootRequired = true
        )
    }

    private fun restorePlayBackup(dir: File, props: Properties): OperationResult {
        val vendingOwner = props.getProperty("vending_owner")
            ?: RootShell.captureDirectoryOwner(RootPaths.vendingDbDir())
            ?: return OperationResult(false, "Unable to resolve Play Store file owner")

        RootShell.forceStopVending()

        val localBackup = File(dir, "localappstate.db")
        if (localBackup.exists()) {
            val target = props.getProperty("local_path", RootPaths.localAppStateDb())
            val meta = FileSecurityMeta(
                ownerUidGid = vendingOwner,
                mode = props.getProperty("local_mode", "660"),
                selinuxContext = props.getProperty("local_selinux")
            )
            val res = RootShell.replaceFileSafely(
                stagedFile = localBackup,
                targetPath = target,
                meta = meta,
                sidecarsToRemove = RootPaths.sqliteSidecarPaths(target)
            )
            if (!res.isSuccess) {
                return OperationResult(false, "Failed to restore Play app state: ${res.stderr}")
            }
            restoreSidecarsIfPresent(dir, "localappstate.db", target, meta)
        }

        val libraryBackup = File(dir, "library.db")
        if (libraryBackup.exists()) {
            val target = props.getProperty("library_path", RootPaths.libraryDb())
            val meta = FileSecurityMeta(
                ownerUidGid = vendingOwner,
                mode = props.getProperty("library_mode", "660"),
                selinuxContext = props.getProperty("library_selinux")
            )
            val res = RootShell.replaceFileSafely(
                stagedFile = libraryBackup,
                targetPath = target,
                meta = meta,
                sidecarsToRemove = RootPaths.sqliteSidecarPaths(target)
            )
            if (!res.isSuccess) {
                return OperationResult(false, "Failed to restore Play library: ${res.stderr}")
            }
            restoreSidecarsIfPresent(dir, "library.db", target, meta)
        }

        val finskyBackup = File(dir, "finsky.xml")
        if (finskyBackup.exists()) {
            val target = props.getProperty("finsky_path", RootPaths.finskyXml())
            val meta = FileSecurityMeta(
                ownerUidGid = vendingOwner,
                mode = props.getProperty("finsky_mode", "660"),
                selinuxContext = props.getProperty("finsky_selinux")
            )
            RootShell.replaceFileSafely(finskyBackup, target, meta)
        }

        RootShell.forceStopVending()
        return OperationResult(true, "Play Store backup restored.")
    }

    fun sanitizeZipEntryName(rawName: String): String? {
        if (rawName.isBlank() || "\\" in rawName || ".." in rawName || rawName.startsWith("/")) {
            throw IllegalArgumentException("Unsafe archive path: $rawName")
        }
        val clean = rawName.trimEnd('/')
        if (clean.isEmpty()) return null
        val parts = clean.split('/')
        if (parts.size > 2 || (parts.size == 2 && !isValidFolderName(parts[0]))) {
            throw IllegalArgumentException("Unexpected directory depth in archive: $rawName")
        }
        val fileName = parts.last()
        return if (allowedBackupFileRegex.matches(fileName)) fileName else null
    }

    fun writeFolderToZipStream(folder: File, out: OutputStream) {
        val files = folder.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
        require(files.isNotEmpty()) { "Backup folder is empty" }
        ZipOutputStream(out).use { zos ->
            val buf = ByteArray(16 * 1024)
            for (file in files) {
                val entry = ZipEntry(file.name).apply { time = file.lastModified() }
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    while (true) {
                        val read = fis.read(buf)
                        if (read <= 0) break
                        zos.write(buf, 0, read)
                    }
                }
                zos.closeEntry()
            }
            zos.finish()
        }
    }

    fun extractValidatedArchive(
        zipStream: InputStream,
        destDir: File,
        maxTotalBytes: Long = MAX_IMPORT_UNCOMPRESSED_BYTES
    ): List<String> {
        val extracted = mutableListOf<String>()
        var totalBytes = 0L
        val buf = ByteArray(16 * 1024)

        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = sanitizeZipEntryName(entry.name)
                    if (safeName != null) {
                        val outFile = File(destDir, safeName)
                        FileOutputStream(outFile, false).use { fos ->
                            while (true) {
                                val read = zis.read(buf)
                                if (read <= 0) break
                                totalBytes += read
                                if (totalBytes > maxTotalBytes) {
                                    error("Archive exceeds safety size limit")
                                }
                                fos.write(buf, 0, read)
                            }
                            fos.flush()
                        }
                        extracted += safeName
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return extracted
    }

    fun inferAndValidateKind(stageDir: File): BackupKind {
        val props = loadProperties(File(stageDir, META_FILE))
        val explicitKind = props.getProperty("kind")?.let { raw ->
            BackupKind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
        val hasCe = File(stageDir, "accounts_ce.db").let { it.exists() && it.length() > 0L }
        val hasDe = File(stageDir, "accounts_de.db").let { it.exists() && it.length() > 0L }
        val hasLocal = File(stageDir, "localappstate.db").let { it.exists() && it.length() > 0L }
        val hasLibrary = File(stageDir, "library.db").let { it.exists() && it.length() > 0L }

        val kind = explicitKind ?: when {
            hasCe && hasDe -> BackupKind.SYSTEM
            hasLocal || hasLibrary -> BackupKind.PLAY
            else -> error("Archive does not contain a valid Play Switch backup")
        }

        when (kind) {
            BackupKind.SYSTEM -> require(hasCe && hasDe) {
                "System backup archive missing accounts_ce.db or accounts_de.db"
            }
            BackupKind.PLAY -> require(hasLocal || hasLibrary) {
                "Play Store archive missing localappstate.db and library.db"
            }
        }
        return kind
    }

    fun openExportZipFd(folderName: String): ParcelFileDescriptor? {
        if (!isValidFolderName(folderName)) return null
        val dir = File(RootPaths.BACKUP_ROOT, folderName)
        if (!dir.exists() || !dir.isDirectory) return null
        if (!RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)) return null

        val tempZip = File(RootPaths.STAGING_ROOT, "export_${UUID.randomUUID()}.zip")
        return try {
            FileOutputStream(tempZip, false).use { out ->
                writeFolderToZipStream(dir, out)
            }
            val pfd = ParcelFileDescriptor.open(tempZip, ParcelFileDescriptor.MODE_READ_ONLY)
            tempZip.delete() // Linux keeps inode open until client closes ParcelFileDescriptor
            pfd
        } catch (t: Throwable) {
            tempZip.delete()
            null
        }
    }

    fun importBackupZip(zipFd: ParcelFileDescriptor): OperationResult {
        if (!RootShell.ensurePrivateDir(RootPaths.BACKUP_ROOT) ||
            !RootShell.ensurePrivateDir(RootPaths.STAGING_ROOT)
        ) {
            runCatching { zipFd.close() }
            return OperationResult(false, "Unable to initialize backup workspace")
        }

        val stageDir = File(RootPaths.STAGING_ROOT, "import_${UUID.randomUUID()}")
        if (!RootShell.ensurePrivateDir(stageDir.absolutePath)) {
            runCatching { zipFd.close() }
            return OperationResult(false, "Failed to create import workspace")
        }

        return try {
            ParcelFileDescriptor.AutoCloseInputStream(zipFd).use { input ->
                val extracted = extractValidatedArchive(input, stageDir)
                if (extracted.isEmpty()) {
                    return OperationResult(false, "Selected ZIP does not contain valid backup files")
                }
            }

            val kind = inferAndValidateKind(stageDir)
            validateImportedDatabases(stageDir, kind)

            val origProps = loadProperties(File(stageDir, META_FILE))
            val origSummary = origProps.getProperty("summary").orEmpty().trim()
            val finskyPath = RootPaths.finskyXml()
            val vendingOwner = RootShell.captureDirectoryOwner(RootPaths.vendingDbDir()) ?: "0:0"

            val ceMeta = if (File(RootPaths.ACCOUNTS_CE_DB).exists()) {
                RootShell.captureFileMeta(RootPaths.ACCOUNTS_CE_DB, "system:system")
            } else {
                FileSecurityMeta("system:system", "660", null)
            }
            val deMeta = if (File(RootPaths.ACCOUNTS_DE_DB).exists()) {
                RootShell.captureFileMeta(RootPaths.ACCOUNTS_DE_DB, "system:system")
            } else {
                FileSecurityMeta("system:system", "660", null)
            }
            val finskyMeta = if (File(finskyPath).exists()) {
                RootShell.captureFileMeta(finskyPath, vendingOwner)
            } else {
                FileSecurityMeta(vendingOwner, "660", null)
            }

            val summary = if (origSummary.isNotBlank() && !origSummary.startsWith("Imported", ignoreCase = true)) {
                "Imported • $origSummary"
            } else if (origSummary.isNotBlank()) {
                origSummary
            } else {
                "Imported ${kind.label}"
            }

            val destFolder = allocateFolder(kind.prefix)
            if (!RootShell.ensurePrivateDir(destFolder.absolutePath)) {
                return OperationResult(false, "Failed creating destination backup folder")
            }

            stageDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                if (file.name != RESTORE_SCRIPT) {
                    file.copyTo(File(destFolder, file.name), overwrite = true)
                }
            }

            val updatedProps = Properties().apply {
                setProperty("kind", kind.name)
                setProperty("origin", BackupOrigin.MANUAL.name)
                setProperty("summary", summary)
                setProperty("created_ms", System.currentTimeMillis().toString())
                if (kind == BackupKind.SYSTEM) {
                    setProperty("ce_owner", ceMeta.ownerUidGid)
                    setProperty("ce_mode", ceMeta.mode)
                    ceMeta.selinuxContext?.let { setProperty("ce_selinux", it) }
                    setProperty("de_owner", deMeta.ownerUidGid)
                    setProperty("de_mode", deMeta.mode)
                    deMeta.selinuxContext?.let { setProperty("de_selinux", it) }
                    setProperty("finsky_path", finskyPath)
                    setProperty("finsky_owner", finskyMeta.ownerUidGid)
                    setProperty("finsky_mode", finskyMeta.mode)
                    finskyMeta.selinuxContext?.let { setProperty("finsky_selinux", it) }
                } else {
                    setProperty("vending_owner", vendingOwner)
                    setProperty("local_path", RootPaths.localAppStateDb())
                    setProperty("library_path", RootPaths.libraryDb())
                    setProperty("finsky_path", finskyPath)
                }
            }
            File(destFolder, META_FILE).outputStream().use { updatedProps.store(it, null) }

            val scriptText = if (kind == BackupKind.SYSTEM) {
                buildSystemRestoreScript(
                    backupDirPath = destFolder.absolutePath,
                    ceOwner = ceMeta.ownerUidGid,
                    deOwner = deMeta.ownerUidGid,
                    finskyOwner = finskyMeta.ownerUidGid,
                    finskyPath = finskyPath
                )
            } else {
                buildPlayRestoreScript(
                    backupDirPath = destFolder.absolutePath,
                    vendingOwner = vendingOwner,
                    localAppStatePath = RootPaths.localAppStateDb(),
                    libraryPath = RootPaths.libraryDb(),
                    finskyPath = finskyPath
                )
            }
            val scriptFile = File(destFolder, RESTORE_SCRIPT)
            scriptFile.writeText(scriptText)
            lockDownBackupFolder(destFolder, scriptFile)
            pruneOldBackups(kind.prefix)

            OperationResult(true, "Imported ${kind.label} snapshot", destFolder.name)
        } catch (t: Throwable) {
            OperationResult(false, "Import rejected: ${t.message}")
        } finally {
            stageDir.deleteRecursively()
        }
    }

    private fun validateImportedDatabases(stageDir: File, kind: BackupKind) {
        val dbNames = when (kind) {
            BackupKind.SYSTEM -> listOf("accounts_ce.db", "accounts_de.db")
            BackupKind.PLAY -> listOf("localappstate.db", "library.db")
        }
        for (name in dbNames) {
            val file = File(stageDir, name)
            if (!file.exists()) continue
            val hasWal = File(stageDir, "$name-wal").exists()
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { db ->
                if (hasWal) {
                    db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
                }
                val gw = AndroidSqliteGateway(db)
                val integrity = gw.integrityCheck()
                check(integrity.equals("ok", ignoreCase = true)) {
                    "$name in archive failed integrity check: $integrity"
                }
                val report = when (name) {
                    "accounts_ce.db" -> SchemaGuard.validateAccountDb(gw, "Imported CE")
                    "accounts_de.db" -> SchemaGuard.validateAccountDb(gw, "Imported DE")
                    "localappstate.db" -> SchemaGuard.validatePlayAppStateDb(gw)
                    "library.db" -> SchemaGuard.validatePlayLibraryDb(gw)
                    else -> null
                }
                if (report != null) {
                    require(report.isSafe) { report.blockingIssues.joinToString("; ") }
                }
            }
        }
    }

    private fun restoreSidecarsIfPresent(
        backupDir: File,
        baseName: String,
        targetDbPath: String,
        meta: FileSecurityMeta
    ) {
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            val sidecarBackup = File(backupDir, "$baseName$suffix")
            if (sidecarBackup.exists()) {
                RootShell.replaceFileSafely(sidecarBackup, "$targetDbPath$suffix", meta)
            }
        }
    }

    private fun lockDownBackupFolder(folder: File, scriptFile: File) {
        val appliedInProcess = runCatching {
            folder.listFiles()?.forEach { file ->
                val mode = if (file == scriptFile) 448 else 384 // 0700 or 0600
                Os.chmod(file.absolutePath, mode)
            }
            true
        }.getOrDefault(false)
        if (!appliedInProcess) {
            RootShell.run("chmod 600 ${RootShell.quoteArg(folder.absolutePath)}/* && chmod 700 ${RootShell.quoteArg(scriptFile.absolutePath)}")
        }
    }

    private fun loadProperties(metaFile: File): Properties = Properties().apply {
        if (metaFile.exists()) {
            runCatching { metaFile.inputStream().use { load(it) } }
        }
    }

    private fun allocateFolder(prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var candidate = File(RootPaths.BACKUP_ROOT, "$prefix$stamp")
        var idx = 1
        while (candidate.exists()) {
            candidate = File(RootPaths.BACKUP_ROOT, "${prefix}${stamp}_$idx")
            idx++
        }
        return candidate
    }

    private fun pruneOldBackups(prefix: String) {
        val root = File(RootPaths.BACKUP_ROOT)
        val candidates = root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(prefix) && isValidFolderName(it.name) }
            ?.map { dir ->
                val origin = BackupOrigin.fromValue(loadProperties(File(dir, META_FILE)).getProperty("origin"))
                PruneCandidate(dir.name, origin)
            }
            ?: return

        selectFoldersToPruneByOrigin(
            candidates = candidates,
            prefix = prefix,
            maxManual = RootPaths.MAX_BACKUPS_PER_KIND,
            maxAuto = RootPaths.MAX_BACKUPS_PER_KIND
        ).forEach { oldName ->
            File(root, oldName).deleteRecursively()
        }
    }
}
