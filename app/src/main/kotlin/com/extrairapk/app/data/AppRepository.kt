package com.extrairapk.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Talks to [PackageManager] to build the list of installed apps.
 * All calls run on [Dispatchers.IO]: PackageManager IPCs and file
 * length() lookups are both blocking.
 */
class AppRepository(private val context: Context) {

    suspend fun loadInstalledApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        } else null

        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(flags!!)
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }

        packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            runCatching { toInstalledAppInfo(pm, packageInfo, appInfo) }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    private fun toInstalledAppInfo(
        pm: PackageManager,
        packageInfo: android.content.pm.PackageInfo,
        appInfo: ApplicationInfo,
    ): InstalledAppInfo {
        val sourceDir = appInfo.sourceDir
        val splitDirs = appInfo.splitSourceDirs?.toList().orEmpty()

        var totalBytes = File(sourceDir).length()
        splitDirs.forEach { totalBytes += File(it).length() }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return InstalledAppInfo(
            packageName = packageInfo.packageName,
            label = appInfo.loadLabel(pm).toString(),
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            icon = appInfo.loadIcon(pm),
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            installedAt = packageInfo.firstInstallTime,
            updatedAt = packageInfo.lastUpdateTime,
            sourceDir = sourceDir,
            splitSourceDirs = splitDirs,
            totalApkBytes = totalBytes,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0,
            targetSdk = appInfo.targetSdkVersion,
        )
    }
}
