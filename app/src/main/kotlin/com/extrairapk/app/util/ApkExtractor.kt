package com.extrairapk.app.util

import android.content.Context
import com.extrairapk.app.data.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Result of pulling an installed app's APK(s) out of the system.
 *
 * [outputFile] is always a single file ready to share or save:
 * a plain .apk when the app has no split APKs, or a .apks zip
 * bundling the base + every split when it does.
 *
 * [componentApks] keeps the individual, uncompressed files around too,
 * since installing a split app back onto a device needs each split
 * written into its own PackageInstaller session entry rather than the
 * zipped bundle.
 */
data class ExtractResult(
    val outputFile: File,
    val isSplitBundle: Boolean,
    val componentApks: List<File>,
)

object ApkExtractor {

    private const val WORK_DIR = "apks"

    suspend fun extract(context: Context, app: InstalledAppInfo): Result<ExtractResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val workDir = File(context.filesDir, "$WORK_DIR/${app.packageName}").apply {
                    deleteRecursively()
                    mkdirs()
                }

                val baseFile = File(workDir, "base.apk")
                File(app.sourceDir).copyTo(baseFile, overwrite = true)

                val splitFiles = app.splitSourceDirs.mapIndexed { index, splitPath ->
                    val name = File(splitPath).name.ifBlank { "split_$index.apk" }
                    val target = File(workDir, if (name.endsWith(".apk")) name else "$name.apk")
                    File(splitPath).copyTo(target, overwrite = true)
                }

                val allApks = listOf(baseFile) + splitFiles
                val safeName = sanitizeFileName("${app.label}_${app.versionName ?: app.versionCode}")

                val outputFile = if (splitFiles.isEmpty()) {
                    val destination = File(context.filesDir, "$WORK_DIR/$safeName.apk")
                    baseFile.copyTo(destination, overwrite = true)
                    destination
                } else {
                    val destination = File(context.filesDir, "$WORK_DIR/$safeName.apks")
                    zipFiles(allApks, destination)
                    destination
                }

                ExtractResult(
                    outputFile = outputFile,
                    isSplitBundle = splitFiles.isNotEmpty(),
                    componentApks = allApks,
                )
            }
        }

    private fun zipFiles(files: List<File>, destination: File) {
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}
