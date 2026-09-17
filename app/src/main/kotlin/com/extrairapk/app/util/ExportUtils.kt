package com.extrairapk.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ExportUtils {

    const val MIME_APK = "application/vnd.android.package-archive"
    const val MIME_BUNDLE = "application/octet-stream"

    fun mimeTypeFor(isSplitBundle: Boolean) = if (isSplitBundle) MIME_BUNDLE else MIME_APK

    fun contentUriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareIntent(context: Context, file: File, isSplitBundle: Boolean): Intent {
        val uri = contentUriFor(context, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(isSplitBundle)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Only valid for a plain, non-split APK; the system installer can't take a zipped bundle. */
    fun installSingleApkIntent(context: Context, file: File): Intent {
        val uri = contentUriFor(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Reinstalls a split app by streaming every component APK into one PackageInstaller session. */
    fun installSplitBundle(context: Context, componentApks: List<File>) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        session.use { s ->
            componentApks.forEach { apk ->
                s.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
            }

            val receiverIntent = Intent(context, InstallResultReceiver::class.java)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) android.app.PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = android.app.PendingIntent.getBroadcast(context, sessionId, receiverIntent, flags)
            s.commit(pendingIntent.intentSender)
        }
    }

    suspend fun saveToDownloads(context: Context, file: File, mimeType: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                        put(MediaStore.Downloads.MIME_TYPE, mimeType)
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ExtrairAPK")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Não foi possível criar o arquivo em Downloads")
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("Não foi possível abrir o arquivo de destino")
                    uri
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "ExtrairAPK",
                    ).apply { mkdirs() }
                    val destination = File(downloadsDir, file.name)
                    file.copyTo(destination, overwrite = true)
                    Uri.fromFile(destination)
                }
            }
        }
}
