package com.voxelhorizon.nativegame

import android.app.AlarmManager
import android.app.DownloadManager
import android.os.Environment
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object PlatformBridge {
    const val CURRENT_VERSION = "6.8.0"
    const val CURRENT_TAG = "v6.8-rust-latest"
    const val REPO_API_LATEST = "https://api.github.com/repos/Pantapan1/Horon/releases/latest"
    const val REPO_RELEASES_URL = "https://github.com/Pantapan1/Horon/releases"
    const val APK_PROVIDER_URI = "content://com.voxelhorizon.nativegame.apkprovider/voxelhorizon_update.apk"
    const val CHANNEL_ID = "voxelhorizon_alerts"

    @Volatile var appContext: Context? = null
    @Volatile var backupDir: File? = null

    @Volatile var updateAvailable = false
    @Volatile var updateTag = ""
    @Volatile var updateTitle = ""
    @Volatile var updateUrl = REPO_RELEASES_URL
    @Volatile var updateApkUrl = ""
    @Volatile var updateStatusText = "Проверка обновлений..."
    @Volatile var updateDownloading = false
    @Volatile var updateProgress = 0

    fun init(ctx: Context, publicMirrorDir: File?) {
        appContext = ctx.applicationContext
        backupDir = publicMirrorDir
        createNotificationChannel(ctx.applicationContext)
        checkGitHubUpdate()
    }

    fun createNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val ch = NotificationChannel(CHANNEL_ID, "Обновления и напоминания VoxelHorizon", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Уведомления о новых версиях игры с GitHub и напоминания вернуться в мир"
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun showNotification(id: Int, title: String, body: String, openUrl: String? = null) {
        val ctx = appContext ?: return
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val intent = if (!openUrl.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            } else {
                Intent(ctx, AndroidLauncher::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getActivity(ctx, id, intent, flags)

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(ctx, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            val notif = builder
                .setSmallIcon(ctx.applicationInfo.icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(id, notif)
        } catch (_: Exception) {}
    }

    fun backupWorldsToDocuments(storageRoot: File): String {
        val dst = backupDir ?: return "Папка Documents недоступна"
        return try {
            val srcW = File(storageRoot, "worlds")
            val dstW = File(dst, "worlds").also { it.mkdirs() }
            var cnt = 0
            srcW.listFiles()?.forEach { f ->
                if (f.isFile && f.canonicalPath != File(dstW, f.name).canonicalPath) {
                    f.copyTo(File(dstW, f.name), overwrite = true); cnt++
                }
            }
            val srcM = File(storageRoot, "mods")
            val dstM = File(dst, "mods").also { it.mkdirs() }
            srcM.listFiles()?.forEach { f ->
                if (f.isFile && f.canonicalPath != File(dstM, f.name).canonicalPath) {
                    f.copyTo(File(dstM, f.name), overwrite = true)
                }
            }
            if (cnt == 0) "Миры уже в Documents/VoxelHorizon!" else "Сохранено копий в Documents: $cnt"
        } catch (e: Exception) {
            "Ошибка копирования: ${e.message ?: "доступ"}"
        }
    }

    fun openBrowserUrl(url: String) {
        val ctx = appContext ?: return
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }

    fun scheduleComeBackReminder(delayHours: Int = 20) {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(ctx, ReminderReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(ctx, 7001, intent, flags)
            val triggerAt = System.currentTimeMillis() + delayHours.toLong() * 3600L * 1000L
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: Exception) {}
    }

    private fun extractJsonString(json: String, key: String): String {
        val pat = "\"$key\""
        val idx = json.indexOf(pat)
        if (idx < 0) return ""
        val colon = json.indexOf(':', idx + pat.length)
        if (colon < 0) return ""
        val q1 = json.indexOf('"', colon + 1)
        if (q1 < 0) return ""
        val sb = StringBuilder()
        var i = q1 + 1
        while (i < json.length) {
            val ch = json[i]
            if (ch == '\\' && i + 1 < json.length) { sb.append(json[i + 1]); i += 2; continue }
            if (ch == '"') break
            sb.append(ch); i++
        }
        return sb.toString()
    }

    private fun extractApkDownloadUrl(json: String): String {
        var searchFrom = 0
        while (true) {
            val idx = json.indexOf("\"browser_download_url\"", searchFrom)
            if (idx < 0) return ""
            val colon = json.indexOf(':', idx + 22)
            if (colon < 0) return ""
            val q1 = json.indexOf('"', colon + 1)
            if (q1 < 0) return ""
            val sb = StringBuilder()
            var i = q1 + 1
            while (i < json.length) {
                val ch = json[i]
                if (ch == '\\' && i + 1 < json.length) { sb.append(json[i + 1]); i += 2; continue }
                if (ch == '"') break
                sb.append(ch); i++
            }
            val url = sb.toString()
            if (url.endsWith(".apk", ignoreCase = true)) return url
            searchFrom = i + 1
        }
    }

    private fun parseVersionTuple(tag: String): Int {
        val nums = Regex("\\d+").findAll(tag).map { it.value.toIntOrNull() ?: 0 }.toList()
        val maj = nums.getOrElse(0) { 0 }
        val min = nums.getOrElse(1) { 0 }
        val pat = nums.getOrElse(2) { 0 }
        return maj * 10000 + min * 100 + pat
    }

    fun checkGitHubUpdate() {
        if (updateDownloading) return
        updateStatusText = "Проверка GitHub..."
        thread(isDaemon = true) {
            try {
                val conn = (URL(REPO_API_LATEST).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "VoxelHorizon-Android-$CURRENT_VERSION")
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                val code = conn.responseCode
                if (code == 200) {
                    val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val tag = extractJsonString(body, "tag_name")
                    val name = extractJsonString(body, "name")
                    val apkDirect = extractApkDownloadUrl(body)
                    val remoteVer = parseVersionTuple(tag)
                    val localVer = parseVersionTuple(CURRENT_VERSION)
                    if (tag.isNotBlank() && (remoteVer > localVer || (remoteVer == localVer && tag != CURRENT_TAG && tag.contains("v")))) {
                        updateAvailable = true
                        updateTag = tag
                        updateTitle = name.ifBlank { "Новая версия $tag" }
                        updateUrl = REPO_RELEASES_URL
                        updateApkUrl = apkDirect
                        updateStatusText = "Доступно обновление: $tag!"
                        showNotification(
                            7002,
                            "Вышла новая версия VoxelHorizon ($tag)!",
                            "Откройте игру, чтобы скачать и установить обновление прямо внутри приложения.",
                            null
                        )
                    } else {
                        updateAvailable = false
                        updateApkUrl = apkDirect
                        updateStatusText = "Версия $CURRENT_VERSION (актуальная)"
                    }
                } else {
                    updateStatusText = "Версия $CURRENT_VERSION (GitHub: $code)"
                }
                conn.disconnect()
            } catch (_: Exception) {
                updateStatusText = "Версия $CURRENT_VERSION (офлайн)"
            }
        }
    }

    // Скачивание APK прямо в игре с прогрессом и автоматическим запуском установщика Android!
    fun downloadAndInstallUpdate(onStatus: (String) -> Unit) {
        val ctx = appContext ?: return
        if (updateDownloading) return
        val dlUrl = updateApkUrl
        if (dlUrl.isBlank()) {
            openBrowserUrl(REPO_RELEASES_URL)
            onStatus("Открыта страница релизов GitHub")
            return
        }

        // Проверяем право установки неизвестных приложений на Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            onStatus("Разрешите установку обновлений для VoxelHorizon и нажмите снова!")
            try {
                val permIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(permIntent)
            } catch (_: Exception) {
                openBrowserUrl(REPO_RELEASES_URL)
            }
            return
        }

        updateDownloading = true
        updateProgress = 0
        updateStatusText = "Загрузка обновления: 0%"
        onStatus("Начата загрузка $updateTag прямо в игре...")

        thread(isDaemon = true) {
            try {
                var curUrl = dlUrl
                var conn: HttpURLConnection? = null
                for (redir in 0 until 8) {
                    val c = (URL(curUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 45000
                        instanceFollowRedirects = false
                        if (redir == 0) setRequestProperty("User-Agent", "VoxelHorizon-Android-$CURRENT_VERSION")
                    }
                    val status = c.responseCode
                    if (status in intArrayOf(301, 302, 303, 307, 308)) {
                        val nextLoc = c.getHeaderField("Location")
                        c.disconnect()
                        if (nextLoc.isNullOrBlank()) break
                        curUrl = nextLoc
                    } else {
                        conn = c
                        break
                    }
                }
                val finalConn = conn ?: throw RuntimeException("Ошибка редиректа")
                if (finalConn.responseCode != 200) throw RuntimeException("HTTP ${finalConn.responseCode}")

                val totalBytes = finalConn.contentLengthLong
                val outFile = File(ctx.cacheDir, "voxelhorizon_update.apk")
                val tmpFile = File(ctx.cacheDir, "voxelhorizon_update.apk.part")
                if (tmpFile.exists()) tmpFile.delete()

                var downloaded = 0L
                val buf = ByteArray(16384)
                finalConn.inputStream.use { inp ->
                    FileOutputStream(tmpFile).use { out ->
                        while (true) {
                            val r = inp.read(buf)
                            if (r <= 0) break
                            out.write(buf, 0, r)
                            downloaded += r
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                updateProgress = pct
                                updateStatusText = "Загрузка $updateTag: $pct%"
                            } else {
                                updateStatusText = "Загрузка: ${downloaded / 1024} КБ"
                            }
                        }
                    }
                }
                finalConn.disconnect()

                if (outFile.exists()) outFile.delete()
                tmpFile.renameTo(outFile)

                updateDownloading = false
                updateProgress = 100
                updateStatusText = "Загружено! Открываем установщик..."
                onStatus("Обновление загружено! Подтвердите установку.")

                val installUri = Uri.parse(APK_PROVIDER_URI)
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(installUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(installIntent)
            } catch (e: Exception) {
                updateDownloading = false
                try {
                    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    if (dm != null) {
                        val req = DownloadManager.Request(Uri.parse(dlUrl))
                            .setTitle("VoxelHorizon $updateTag")
                            .setDescription("Загрузка обновления APK...")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "VoxelHorizon-$updateTag.apk")
                        dm.enqueue(req)
                        updateStatusText = "Скачивается в Downloads!"
                        onStatus("Загрузка запущена в шторке (папка Downloads)!")
                        return@thread
                    }
                } catch (_: Exception) {}
                updateStatusText = "Нажмите GitHub для скачивания"
                onStatus("Откройте GitHub для скачивания обновления")
            }
        }
    }
}

// Безопасный встроенный ContentProvider для установки скачанного APK без зависимостей AndroidX!
class ApkFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun apkFile(): File? = context?.let { File(it.cacheDir, "voxelhorizon_update.apk") }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = apkFile() ?: throw java.io.FileNotFoundException("No context")
        if (!f.exists()) throw java.io.FileNotFoundException("APK not downloaded")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val f = apkFile()
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> "VoxelHorizon-Update.apk"
                OpenableColumns.SIZE -> f?.length() ?: 0L
                else -> null
            }
        }
        cursor.addRow(row)
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlatformBridge.appContext = context.applicationContext
        PlatformBridge.createNotificationChannel(context.applicationContext)
        val phrases = listOf(
            "Пора поиграть! Ваш мир VoxelHorizon и Лисёнок скучают!",
            "Новый сезон уже близко — загляните проверить ферму и шахту!",
            "Готовы спуститься глубже к алмазам и рубинам? Заходите в VoxelHorizon!"
        )
        val msg = phrases[(System.currentTimeMillis() / 1000L % phrases.size).toInt()]
        PlatformBridge.showNotification(7003, "VoxelHorizon ждёт вас!", msg, null)
    }
}
