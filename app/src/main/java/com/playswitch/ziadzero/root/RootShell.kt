package com.playswitch.ziadzero.root

import android.system.Os
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ShellExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

data class FileSecurityMeta(
    val ownerUidGid: String,
    val mode: String,
    val selinuxContext: String?
)

object RootShell {
    fun quoteArg(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    fun run(command: String, timeoutSeconds: Long = 30): ShellExecResult {
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
        // Strip library overrides so system tools (am, restorecon, stat, chown) use system libs cleanly.
        val env = pb.environment()
        env.remove("LD_LIBRARY_PATH")
        env.remove("LD_PRELOAD")

        val process = pb.start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ShellExecResult(-1, "", "Command timed out after ${timeoutSeconds}s")
        }
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val err = process.errorStream.bufferedReader().use { it.readText() }.trim()
        return ShellExecResult(process.exitValue(), out, err)
    }

    fun forceStopVending() {
        run("/system/bin/am force-stop ${RootPaths.VENDING_PKG}")
    }

    fun captureFileMeta(path: String, defaultOwner: String, defaultMode: String = "660"): FileSecurityMeta =
        captureFilesMeta(listOf(path), defaultOwner, defaultMode)[path]
            ?: FileSecurityMeta(defaultOwner, defaultMode, null)

    /**
     * Captures UID:GID and octal mode in-process via Os.stat (0 process forks),
     * and batches SELinux context lookup across all paths into a single stat invocation.
     */
    fun captureFilesMeta(
        paths: List<String>,
        defaultOwner: String,
        defaultMode: String = "660"
    ): Map<String, FileSecurityMeta> {
        val existing = paths.filter { File(it).exists() }
        if (existing.isEmpty()) return emptyMap()

        val selinuxByPath = HashMap<String, String?>(existing.size)
        val qPaths = existing.joinToString(" ") { quoteArg(it) }
        val seRes = run("stat -c '%n|%C' $qPaths")
        if (seRes.isSuccess && seRes.stdout.isNotBlank()) {
            seRes.stdout.lineSequence().forEach { line ->
                val sep = line.lastIndexOf('|')
                if (sep > 0) {
                    val p = line.substring(0, sep)
                    val ctx = line.substring(sep + 1).trim()
                    if (ctx.isNotEmpty() && ctx != "?") {
                        selinuxByPath[p] = ctx
                    }
                }
            }
        }

        val out = LinkedHashMap<String, FileSecurityMeta>(existing.size)
        for (p in existing) {
            var owner = defaultOwner
            var mode = defaultMode
            val st = runCatching { Os.stat(p) }.getOrNull()
            if (st != null) {
                owner = "${st.st_uid}:${st.st_gid}"
                mode = (st.st_mode and 0x0FFF).toString(8).padStart(3, '0')
            } else {
                val q = quoteArg(p)
                val fallback = run("stat -c '%u:%g|%a' $q")
                if (fallback.isSuccess && '|' in fallback.stdout) {
                    val parts = fallback.stdout.split('|', limit = 2)
                    if (parts[0].matches(Regex("^[0-9]+:[0-9]+$"))) owner = parts[0]
                    if (parts[1].matches(Regex("^[0-7]{3,4}$"))) mode = parts[1]
                }
            }

            val ctx = selinuxByPath[p] ?: run {
                val lsRes = run("ls -Z ${quoteArg(p)}")
                parseSelinuxFromLsZ(lsRes.stdout)
            }
            out[p] = FileSecurityMeta(ownerUidGid = owner, mode = mode, selinuxContext = ctx)
        }
        return out
    }

    fun parseSelinuxFromLsZ(lsZOutput: String): String? {
        val token = lsZOutput.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        return if (token.contains(":") && token != "?") token else null
    }

    fun captureDirectoryOwner(dirPath: String): String? {
        val st = runCatching { Os.stat(dirPath) }.getOrNull()
        if (st != null) return "${st.st_uid}:${st.st_gid}"
        val res = run("stat -c '%u:%g' ${quoteArg(dirPath)}")
        return if (res.isSuccess && res.stdout.matches(Regex("^[0-9]+:[0-9]+$"))) {
            res.stdout
        } else {
            null
        }
    }

    fun ensurePrivateDir(dirPath: String): Boolean {
        val dir = File(dirPath)
        if (!dir.exists() && !dir.mkdirs()) return false
        return runCatching {
            Os.chmod(dirPath, 448) // 0700
            true
        }.getOrElse {
            run("chmod 700 ${quoteArg(dirPath)}").isSuccess
        }
    }

    /**
     * Copies a staged file to a sibling temp path in Kotlin, applies durable fsync + UID/GID + mode,
     * applies SELinux label, atomically renames over the target, and removes stale sidecars.
     */
    fun replaceFileSafely(
        stagedFile: File,
        targetPath: String,
        meta: FileSecurityMeta,
        sidecarsToRemove: List<String> = emptyList()
    ): ShellExecResult {
        val tempPath = "$targetPath.ps_tmp"
        val tempFile = File(tempPath)

        val ownerParts = meta.ownerUidGid.split(':')
        val numericUid = ownerParts.getOrNull(0)?.toIntOrNull()
        val numericGid = ownerParts.getOrNull(1)?.toIntOrNull()
        val numericMode = meta.mode.toIntOrNull(8)

        if (numericUid != null && numericGid != null && numericMode != null) {
            val fastAttempt = runCatching {
                FileInputStream(stagedFile).use { input ->
                    FileOutputStream(tempFile, false).use { output ->
                        input.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
                }
                Os.chown(tempPath, numericUid, numericGid)
                Os.chmod(tempPath, numericMode)
                if (!meta.selinuxContext.isNullOrBlank()) {
                    run("chcon ${quoteArg(meta.selinuxContext)} ${quoteArg(tempPath)} >/dev/null 2>&1 || true")
                }
                Os.rename(tempPath, targetPath)
                sidecarsToRemove.forEach { sidecar ->
                    runCatching { File(sidecar).delete() }
                }
                run("restorecon -v ${quoteArg(targetPath)} >/dev/null 2>&1 || true")
                ShellExecResult(0, "", "")
            }
            if (fastAttempt.isSuccess) {
                return fastAttempt.getOrThrow()
            }
            runCatching { tempFile.delete() }
        }

        val qStaged = quoteArg(stagedFile.absolutePath)
        val qTemp = quoteArg(tempPath)
        val qTarget = quoteArg(targetPath)

        val steps = buildList {
            add("cp -f $qStaged $qTemp")
            add("chown ${meta.ownerUidGid} $qTemp")
            add("chmod ${meta.mode} $qTemp")
            if (!meta.selinuxContext.isNullOrBlank()) {
                add("(chcon ${quoteArg(meta.selinuxContext)} $qTemp || true)")
            }
            add("mv -f $qTemp $qTarget")
            sidecarsToRemove.forEach { sidecar ->
                add("rm -f ${quoteArg(sidecar)}")
            }
            add("(restorecon -v $qTarget >/dev/null 2>&1 || true)")
        }

        val result = run(steps.joinToString(" && "))
        if (!result.isSuccess) {
            run("rm -f $qTemp")
        }
        return result
    }

    fun copyDbWithSidecars(dbPath: String, destDir: File): File {
        val main = File(dbPath)
        val copiedMain = File(destDir, main.name)
        main.copyTo(copiedMain, overwrite = true)
        RootPaths.sqliteSidecarPaths(dbPath).forEach { sidecarPath ->
            val sidecar = File(sidecarPath)
            if (sidecar.exists()) {
                sidecar.copyTo(File(destDir, sidecar.name), overwrite = true)
            }
        }
        return copiedMain
    }
}
