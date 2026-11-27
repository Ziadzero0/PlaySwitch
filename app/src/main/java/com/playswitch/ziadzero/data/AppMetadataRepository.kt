package com.playswitch.ziadzero.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.playswitch.ziadzero.model.PlayAppOwnershipInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppInstallStatus {
    INSTALLED,
    DISABLED,
    UNINSTALLED
}

data class EnrichedPlayApp(
    val packageName: String,
    val label: String,
    val installStatus: AppInstallStatus,
    val isSystemApp: Boolean,
    val account: String?,
    val accountForUpdate: String?,
    val libraryAccounts: List<String>
)

class AppMetadataRepository(private val context: Context) {
    private val iconCache = HashMap<String, ImageBitmap>()
    private val labelCache = HashMap<String, String>()
    private val appInfoCache = HashMap<String, ApplicationInfo?>()
    private val unresolvedPackages = HashSet<String>()

    @Volatile
    private var lastInstalledSnapshot: Map<String, ApplicationInfo>? = null

    val defaultAppIcon: ImageBitmap by lazy {
        context.packageManager.defaultActivityIcon.toNormalizedIconBitmap(ICON_SIZE_PX)
    }

    suspend fun preloadInstalledMap(): Map<String, ApplicationInfo> = withContext(Dispatchers.IO) {
        val map = loadInstalledMap(context.packageManager)
        synchronized(appInfoCache) {
            appInfoCache.clear()
            appInfoCache.putAll(map)
        }
        lastInstalledSnapshot = map
        map
    }

    suspend fun enrichAppsMetadata(
        rawApps: List<PlayAppOwnershipInfo>,
        preloadedInstalled: Map<String, ApplicationInfo>? = null,
        forceRefreshLabels: Boolean = false
    ): List<EnrichedPlayApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        if (forceRefreshLabels) {
            synchronized(labelCache) { labelCache.clear() }
            synchronized(unresolvedPackages) { unresolvedPackages.clear() }
            synchronized(iconCache) { iconCache.clear() }
        }

        val installedMap = preloadedInstalled
            ?: lastInstalledSnapshot?.takeIf { !forceRefreshLabels }
            ?: preloadInstalledMap()

        rawApps.map { raw ->
            val appInfo = installedMap[raw.packageName] ?: run {
                val cachedMissing = synchronized(unresolvedPackages) { raw.packageName in unresolvedPackages }
                val cachedEntry = synchronized(appInfoCache) { appInfoCache[raw.packageName] }
                if (cachedEntry != null) {
                    cachedEntry
                } else if (cachedMissing) {
                    null
                } else {
                    resolveFallbackAppInfo(pm, raw.packageName).also { resolved ->
                        if (resolved != null) {
                            synchronized(appInfoCache) { appInfoCache[raw.packageName] = resolved }
                        } else {
                            synchronized(unresolvedPackages) { unresolvedPackages.add(raw.packageName) }
                        }
                    }
                }
            }

            val label = synchronized(labelCache) { labelCache[raw.packageName] } ?: run {
                val resolved = appInfo?.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: raw.packageName
                synchronized(labelCache) { labelCache[raw.packageName] = resolved }
                resolved
            }

            val isSystem = if (appInfo != null) {
                (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            } else {
                false
            }
            val status = classifyInstallStatus(pm, appInfo, raw.packageName)

            EnrichedPlayApp(
                packageName = raw.packageName,
                label = label,
                installStatus = status,
                isSystemApp = isSystem,
                account = raw.account,
                accountForUpdate = raw.accountForUpdate,
                libraryAccounts = raw.libraryAccounts
            )
        }.sortedWith(
            compareBy<EnrichedPlayApp> { it.isSystemApp }
                .thenBy { it.label.lowercase() }
        )
    }

    fun getCachedIcon(packageName: String): ImageBitmap? =
        synchronized(iconCache) { iconCache[packageName] }

    suspend fun loadAppIcon(packageName: String): ImageBitmap {
        getCachedIcon(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            getCachedIcon(packageName)?.let { return@withContext it }
            val pm = context.packageManager
            val appInfo = synchronized(appInfoCache) { appInfoCache[packageName] }
                ?: run {
                    val skipped = synchronized(unresolvedPackages) { packageName in unresolvedPackages }
                    if (skipped) null else resolveFallbackAppInfo(pm, packageName)
                }

            val bitmap = if (appInfo != null) {
                runCatching {
                    appInfo.loadUnbadgedIcon(pm).toNormalizedIconBitmap(ICON_SIZE_PX)
                }.getOrElse { defaultAppIcon }
            } else {
                defaultAppIcon
            }

            synchronized(iconCache) {
                iconCache[packageName] = bitmap
            }
            bitmap
        }
    }

    suspend fun warmUpIcons(packageNames: List<String>) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        for (pkg in packageNames) {
            if (getCachedIcon(pkg) != null) continue
            val skipped = synchronized(unresolvedPackages) { pkg in unresolvedPackages }
            if (skipped) {
                synchronized(iconCache) { iconCache[pkg] = defaultAppIcon }
                continue
            }
            val appInfo = synchronized(appInfoCache) { appInfoCache[pkg] }
                ?: resolveFallbackAppInfo(pm, pkg)
            val bitmap = if (appInfo != null) {
                runCatching {
                    appInfo.loadUnbadgedIcon(pm).toNormalizedIconBitmap(ICON_SIZE_PX)
                }.getOrElse { defaultAppIcon }
            } else {
                defaultAppIcon
            }
            synchronized(iconCache) {
                iconCache[pkg] = bitmap
            }
        }
    }

    private fun classifyInstallStatus(
        pm: PackageManager,
        appInfo: ApplicationInfo?,
        packageName: String
    ): AppInstallStatus {
        if (appInfo == null) return AppInstallStatus.UNINSTALLED
        val isFlagInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        val enabledSetting = runCatching { pm.getApplicationEnabledSetting(packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val explicitlyDisabled = enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        val apkExistsOnDisk = appInfo.sourceDir?.let { File(it).exists() } == true

        return when {
            isFlagInstalled && appInfo.enabled && !explicitlyDisabled -> AppInstallStatus.INSTALLED
            (isFlagInstalled && (!appInfo.enabled || explicitlyDisabled)) || apkExistsOnDisk -> AppInstallStatus.DISABLED
            else -> AppInstallStatus.UNINSTALLED
        }
    }

    private fun baseQueryFlags(): Int =
        PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS or
            PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_APEX

    private fun longQueryFlags(includeArchived: Boolean): Long {
        val base = baseQueryFlags().toLong()
        return if (includeArchived && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            base or PackageManager.MATCH_ARCHIVED_PACKAGES
        } else {
            base
        }
    }

    private fun loadInstalledMap(pm: PackageManager): Map<String, ApplicationInfo> {
        val legacyFlags = baseQueryFlags()
        val map = HashMap<String, ApplicationInfo>()

        val appList = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(longQueryFlags(includeArchived = true)))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(legacyFlags)
            }
        }.getOrDefault(emptyList())
        for (app in appList) {
            map[app.packageName] = app
        }

        // getInstalledPackages with MATCH_APEX captures active Mainline APEX modules omitted by getInstalledApplications.
        val pkgList = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(longQueryFlags(includeArchived = false)))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(legacyFlags)
            }
        }.getOrDefault(emptyList())
        for (pkg in pkgList) {
            pkg.applicationInfo?.let { app ->
                map.putIfAbsent(pkg.packageName, app)
            }
        }

        return map
    }

    private fun resolveFallbackAppInfo(pm: PackageManager, packageName: String): ApplicationInfo? {
        val legacyFlags = baseQueryFlags()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(longQueryFlags(includeArchived = true)))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, legacyFlags)
            }
        }.recoverCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(longQueryFlags(includeArchived = false))).applicationInfo
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, legacyFlags).applicationInfo
            }
        }.getOrNull()
    }

    private fun Drawable.toNormalizedIconBitmap(targetPx: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val srcW = intrinsicWidth
        val srcH = intrinsicHeight
        val oldBounds = Rect(bounds)
        if (srcW > 0 && srcH > 0 && srcW != srcH) {
            val scale = minOf(targetPx.toFloat() / srcW, targetPx.toFloat() / srcH)
            val drawW = (srcW * scale).toInt().coerceAtLeast(1)
            val drawH = (srcH * scale).toInt().coerceAtLeast(1)
            val left = (targetPx - drawW) / 2
            val top = (targetPx - drawH) / 2
            setBounds(left, top, left + drawW, top + drawH)
        } else {
            setBounds(0, 0, targetPx, targetPx)
        }
        draw(canvas)
        bounds = oldBounds
        return bmp.asImageBitmap()
    }

    private companion object {
        const val ICON_SIZE_PX = 128
    }
}
