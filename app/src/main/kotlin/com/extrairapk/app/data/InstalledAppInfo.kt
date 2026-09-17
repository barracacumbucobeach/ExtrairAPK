package com.extrairapk.app.data

import android.graphics.drawable.Drawable

/**
 * Snapshot of one installed application, gathered once at load time so the
 * UI never has to touch PackageManager off the main scan.
 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val icon: Drawable,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val installedAt: Long,
    val updatedAt: Long,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val totalApkBytes: Long,
    val minSdk: Int,
    val targetSdk: Int,
) {
    val hasSplits: Boolean get() = splitSourceDirs.isNotEmpty()
    val isUserApp: Boolean get() = !isSystemApp || isUpdatedSystemApp
}
