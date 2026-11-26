package com.sportassistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    /** Optional lowercase hex SHA-256 of the APK; verified before installing when present. */
    val sha256: String?,
    val notes: String?,
)

class HashMismatchException : Exception()

/**
 * Self-update from a static `version.json`:
 * `{"versionCode":3,"versionName":"0.3.0","apkUrl":"https://.../app.apk","sha256":"...","notes":"..."}`.
 * Android itself refuses to install an APK signed with a different key than the installed app.
 */
object Updater {
    private const val TIMEOUT_MS = 15_000

    suspend fun fetch(url: String): UpdateInfo = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "update url must be https" }
        val conn = open(url)
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val apk = json.getString("apkUrl")
            require(apk.startsWith("https://")) { "apk url must be https" }
            UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = apk,
                sha256 = json.optString("sha256").takeIf { it.isNotBlank() }?.lowercase(),
                notes = json.optString("notes").takeIf { it.isNotBlank() },
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val conn = open(info.apkUrl)
        try {
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (info.sha256 != null && info.sha256 != actual) {
            file.delete()
            throw HashMismatchException()
        }
        file
    }

    /** Returns false (after opening the relevant settings page) if installing is not yet allowed. */
    fun install(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
        }
}
