package com.playswitch.ziadzero.model

import org.json.JSONArray
import org.json.JSONObject

enum class BackupKind(val prefix: String, val label: String) {
    SYSTEM("sys_", "System Accounts"),
    PLAY("play_", "Play Store Apps");

    companion object {
        fun fromFolderName(name: String): BackupKind? =
            entries.firstOrNull { name.startsWith(it.prefix) }
    }
}

enum class BackupOrigin(val label: String) {
    MANUAL("Manual"),
    AUTO("Auto");

    companion object {
        fun fromValue(raw: String?): BackupOrigin =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

enum class OwnershipTransferMode(val label: String, val shortLabel: String) {
    BOTH("Billing & Updates", "All-in-One"),
    BILLING_ONLY("Billing Only", "Billing Only"),
    UPDATE_ONLY("Updates Only", "Updates Only");

    companion object {
        fun fromValue(raw: String?): OwnershipTransferMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: BOTH
    }
}

data class GoogleAccountInfo(
    val id: Int,
    val name: String,
    val deId: Int?,
    val isPrimary: Boolean,
    val isDePrimary: Boolean = isPrimary
)

data class PlayAppOwnershipInfo(
    val packageName: String,
    val account: String?,
    val accountForUpdate: String?,
    val libraryAccounts: List<String>
)

data class BackupSnapshot(
    val folderName: String,
    val kind: BackupKind,
    val timestampMs: Long,
    val files: List<String>,
    val sizeBytes: Long,
    val hasRestoreScript: Boolean,
    val origin: BackupOrigin = BackupOrigin.AUTO,
    val summary: String = ""
)

data class EnvironmentStatus(
    val rootReady: Boolean,
    val ceDbExists: Boolean,
    val deDbExists: Boolean,
    val localAppStateExists: Boolean,
    val libraryDbExists: Boolean,
    val finskyXmlExists: Boolean,
    val ceTables: List<String>,
    val deTables: List<String>,
    val issues: List<String>,
    val accountsEngineReady: Boolean = true,
    val playEngineReady: Boolean = true,
    val compatibilityNotes: List<String> = emptyList(),
    val cePrimaryEmail: String = "",
    val dePrimaryEmail: String = ""
)

data class OperationResult(
    val success: Boolean,
    val message: String,
    val details: String = "",
    val rebootRequired: Boolean = false
)

data class DashboardSnapshot(
    val environment: EnvironmentStatus,
    val accounts: List<GoogleAccountInfo>,
    val playActiveAccount: String,
    val apps: List<PlayAppOwnershipInfo>,
    val backups: List<BackupSnapshot>
)

private fun JSONObject.optNullableString(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun JSONArray.toStringList(): List<String> =
    List(length()) { idx -> optString(idx) }.filter { it.isNotEmpty() }

object RootJsonCodec {
    fun encodeOperationResult(result: OperationResult): String = JSONObject().apply {
        put("success", result.success)
        put("message", result.message)
        put("details", result.details)
        put("rebootRequired", result.rebootRequired)
    }.toString()

    fun decodeOperationResult(raw: String): OperationResult {
        val obj = JSONObject(raw)
        return OperationResult(
            success = obj.optBoolean("success", false),
            message = obj.optString("message", "Unknown response"),
            details = obj.optString("details", ""),
            rebootRequired = obj.optBoolean("rebootRequired", false)
        )
    }

    fun encodeEnvironmentStatus(status: EnvironmentStatus): String = JSONObject().apply {
        put("rootReady", status.rootReady)
        put("ceDbExists", status.ceDbExists)
        put("deDbExists", status.deDbExists)
        put("localAppStateExists", status.localAppStateExists)
        put("libraryDbExists", status.libraryDbExists)
        put("finskyXmlExists", status.finskyXmlExists)
        put("ceTables", JSONArray(status.ceTables))
        put("deTables", JSONArray(status.deTables))
        put("issues", JSONArray(status.issues))
        put("accountsEngineReady", status.accountsEngineReady)
        put("playEngineReady", status.playEngineReady)
        put("compatibilityNotes", JSONArray(status.compatibilityNotes))
        put("cePrimaryEmail", status.cePrimaryEmail)
        put("dePrimaryEmail", status.dePrimaryEmail)
    }.toString()

    fun decodeEnvironmentStatus(raw: String): EnvironmentStatus {
        val obj = JSONObject(raw)
        return EnvironmentStatus(
            rootReady = obj.optBoolean("rootReady", false),
            ceDbExists = obj.optBoolean("ceDbExists", false),
            deDbExists = obj.optBoolean("deDbExists", false),
            localAppStateExists = obj.optBoolean("localAppStateExists", false),
            libraryDbExists = obj.optBoolean("libraryDbExists", false),
            finskyXmlExists = obj.optBoolean("finskyXmlExists", false),
            ceTables = obj.optJSONArray("ceTables")?.toStringList() ?: emptyList(),
            deTables = obj.optJSONArray("deTables")?.toStringList() ?: emptyList(),
            issues = obj.optJSONArray("issues")?.toStringList() ?: emptyList(),
            accountsEngineReady = obj.optBoolean("accountsEngineReady", true),
            playEngineReady = obj.optBoolean("playEngineReady", true),
            compatibilityNotes = obj.optJSONArray("compatibilityNotes")?.toStringList() ?: emptyList(),
            cePrimaryEmail = obj.optString("cePrimaryEmail", ""),
            dePrimaryEmail = obj.optString("dePrimaryEmail", "")
        )
    }

    fun encodeGoogleAccounts(accounts: List<GoogleAccountInfo>): String =
        JSONArray().apply {
            accounts.forEach { acc ->
                put(JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("deId", acc.deId ?: JSONObject.NULL)
                    put("isPrimary", acc.isPrimary)
                    put("isDePrimary", acc.isDePrimary)
                })
            }
        }.toString()

    fun decodeGoogleAccounts(raw: String): List<GoogleAccountInfo> {
        val arr = JSONArray(raw)
        return List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            val isPrimary = obj.optBoolean("isPrimary", false)
            GoogleAccountInfo(
                id = obj.getInt("id"),
                name = obj.getString("name"),
                deId = if (obj.isNull("deId")) null else obj.getInt("deId"),
                isPrimary = isPrimary,
                isDePrimary = obj.optBoolean("isDePrimary", isPrimary)
            )
        }
    }

    fun encodePlayAppsOwnership(apps: List<PlayAppOwnershipInfo>): String =
        JSONArray().apply {
            apps.forEach { app ->
                put(JSONObject().apply {
                    put("packageName", app.packageName)
                    put("account", app.account ?: JSONObject.NULL)
                    put("accountForUpdate", app.accountForUpdate ?: JSONObject.NULL)
                    put("libraryAccounts", JSONArray(app.libraryAccounts))
                })
            }
        }.toString()

    fun decodePlayAppsOwnership(raw: String): List<PlayAppOwnershipInfo> {
        val arr = JSONArray(raw)
        return List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            PlayAppOwnershipInfo(
                packageName = obj.getString("packageName"),
                account = obj.optNullableString("account"),
                accountForUpdate = obj.optNullableString("accountForUpdate"),
                libraryAccounts = obj.optJSONArray("libraryAccounts")?.toStringList() ?: emptyList()
            )
        }
    }

    fun encodeBackups(backups: List<BackupSnapshot>): String =
        JSONArray().apply {
            backups.forEach { b ->
                put(JSONObject().apply {
                    put("folderName", b.folderName)
                    put("kind", b.kind.name)
                    put("timestampMs", b.timestampMs)
                    put("files", JSONArray(b.files))
                    put("sizeBytes", b.sizeBytes)
                    put("hasRestoreScript", b.hasRestoreScript)
                    put("origin", b.origin.name)
                    put("summary", b.summary)
                })
            }
        }.toString()

    fun decodeBackups(raw: String): List<BackupSnapshot> {
        val arr = JSONArray(raw)
        return List(arr.length()) { idx ->
            val obj = arr.getJSONObject(idx)
            BackupSnapshot(
                folderName = obj.getString("folderName"),
                kind = BackupKind.valueOf(obj.getString("kind")),
                timestampMs = obj.getLong("timestampMs"),
                files = obj.optJSONArray("files")?.toStringList() ?: emptyList(),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                hasRestoreScript = obj.optBoolean("hasRestoreScript", false),
                origin = BackupOrigin.fromValue(obj.optString("origin", BackupOrigin.AUTO.name)),
                summary = obj.optString("summary", "")
            )
        }
    }

    fun encodeDashboardSnapshot(snapshot: DashboardSnapshot): String = JSONObject().apply {
        put("environment", JSONObject(encodeEnvironmentStatus(snapshot.environment)))
        put("accounts", JSONArray(encodeGoogleAccounts(snapshot.accounts)))
        put("playActiveAccount", snapshot.playActiveAccount)
        put("apps", JSONArray(encodePlayAppsOwnership(snapshot.apps)))
        put("backups", JSONArray(encodeBackups(snapshot.backups)))
    }.toString()

    fun decodeDashboardSnapshot(raw: String): DashboardSnapshot {
        val obj = JSONObject(raw)
        return DashboardSnapshot(
            environment = decodeEnvironmentStatus(obj.getJSONObject("environment").toString()),
            accounts = decodeGoogleAccounts(obj.getJSONArray("accounts").toString()),
            playActiveAccount = obj.optString("playActiveAccount", ""),
            apps = decodePlayAppsOwnership(obj.getJSONArray("apps").toString()),
            backups = decodeBackups(obj.getJSONArray("backups").toString())
        )
    }
}
