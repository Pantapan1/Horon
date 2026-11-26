package com.voxelhorizon.nativegame

import android.app.AlarmManager
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

data class ImportedFileEvent(
    val kind: String, // "world", "nocode_mod", "lua_mod", "vhpack", "error"
    val name: String,
    val file: File?,
    val message: String = ""
)

object PlatformBridge {
    const val CURRENT_VERSION = "6.9.1"
    const val CURRENT_TAG = "v6.9-rust-latest"
    const val REPO_API_LATEST = "https://api.github.com/repos/Pantapan1/Horon/releases/latest"
    const val REPO_RELEASES_URL = "https://github.com/Pantapan1/Horon/releases"
    const val PROVIDER_AUTHORITY = "com.voxelhorizon.nativegame.apkprovider"
    const val APK_PROVIDER_URI = "content://$PROVIDER_AUTHORITY/voxelhorizon_update.apk"
    const val CHANNEL_ID = "voxelhorizon_alerts"

    @Volatile var appContext: Context? = null
    @Volatile var backupDir: File? = null
    @Volatile var storageRootDir: File? = null
    @Volatile var onBeforeUpdateCallback: (() -> Unit)? = null
    @Volatile var onPickFileRequest: (() -> Unit)? = null

    val pendingImports = ConcurrentLinkedQueue<ImportedFileEvent>()

    @Volatile var updateAvailable = false
    @Volatile var updateTag = ""
    @Volatile var updateTitle = ""
    @Volatile var updateUrl = REPO_RELEASES_URL
    @Volatile var updateApkUrl = ""
    @Volatile var updateStatusText = "Проверка обновлений..."
    @Volatile var updateDownloading = false
    @Volatile var updateProgress = 0

    fun init(ctx: Context, publicMirrorDir: File?, activeRoot: File? = null) {
        appContext = ctx.applicationContext
        backupDir = publicMirrorDir
        storageRootDir = activeRoot
        createNotificationChannel(ctx.applicationContext)
        checkGitHubUpdate()
    }

    // === НАДЁЖНОЕ ХРАНИЛИЩЕ ПРОФИЛЯ (SharedPreferences + внутренняя память filesDir — 100% переживает обновления!) ===
    fun saveProfileVault(text: String) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        try {
            ctx.getSharedPreferences("vh_profile_vault", Context.MODE_PRIVATE)
                .edit()
                .putString("profile_data", text)
                .putLong("profile_ts", now)
                .commit()
        } catch (_: Exception) {}
        try {
            val f = File(File(ctx.filesDir, "VoxelHorizon"), "profiles_v62.dat")
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun loadProfileVaultCandidates(): List<Pair<String, Long>> {
        val ctx = appContext ?: return emptyList()
        val out = mutableListOf<Pair<String, Long>>()
        try {
            val sp = ctx.getSharedPreferences("vh_profile_vault", Context.MODE_PRIVATE)
            val txt = sp.getString("profile_data", null)
            val ts = sp.getLong("profile_ts", 0L)
            if (!txt.isNullOrBlank() && txt.length > 10) out += Pair(txt, ts)
        } catch (_: Exception) {}
        try {
            val f = File(File(ctx.filesDir, "VoxelHorizon"), "profiles_v62.dat")
            if (f.exists() && f.length() > 10) out += Pair(f.readText(Charsets.UTF_8), f.lastModified())
        } catch (_: Exception) {}
        try {
            val extApp = File(File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "VoxelHorizon"), "profiles_v62.dat")
            if (extApp.exists() && extApp.length() > 10) out += Pair(extApp.readText(Charsets.UTF_8), extApp.lastModified())
        } catch (_: Exception) {}
        return out
    }

    fun saveNoCodeModsVault(text: String) {
        val ctx = appContext ?: return
        try {
            val f = File(File(ctx.filesDir, "VoxelHorizon/mods"), "nocode_mods_v69.dat")
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
        try {
            backupDir?.let { bd ->
                val mf = File(File(bd, "mods"), "nocode_mods_v69.dat")
                mf.parentFile?.mkdirs()
                mf.writeText(text, Charsets.UTF_8)
            }
        } catch (_: Exception) {}
    }

    fun loadNoCodeModsVault(): String? {
        val ctx = appContext ?: return null
        val candidates = listOfNotNull(
            File(File(ctx.filesDir, "VoxelHorizon/mods"), "nocode_mods_v69.dat"),
            backupDir?.let { File(File(it, "mods"), "nocode_mods_v69.dat") }
        ).filter { it.exists() && it.length() > 5 }
        val newest = candidates.maxByOrNull { it.lastModified() } ?: return null
        return try { newest.readText(Charsets.UTF_8) } catch (_: Exception) { null }
    }

    // === ИМПОРТ МИРОВ И МОДОВ ПО ТАПУ В ПРОВОДНИКЕ ИЛИ МЕССЕНДЖЕРЕ (Telegram / VK / Files) ===
    fun handleIncomingIntent(ctx: Context, intent: Intent, storageRoot: File) {
        val action = intent.action ?: return
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri != null) handleIncomingUri(ctx, uri, storageRoot)
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val n = c.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
            }
        } catch (_: Exception) {}
        val seg = uri.lastPathSegment ?: "imported_${System.currentTimeMillis()}"
        return seg.substringAfterLast('/').substringAfterLast(':')
    }

    private fun sanitizeFileName(raw: String, fallbackExt: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9А-Яа-яЁё._\\- ]"), "_").trim().take(48)
        val base = clean.ifBlank { "import_${System.currentTimeMillis()}" }
        return if (base.endsWith(fallbackExt, ignoreCase = true)) base else "$base$fallbackExt"
    }

    fun handleIncomingUri(ctx: Context, uri: Uri, storageRoot: File) {
        thread(isDaemon = true) {
            try {
                val rawName = queryDisplayName(ctx, uri)
                val lower = rawName.lowercase()
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { inp ->
                    val bos = ByteArrayOutputStream()
                    val buf = ByteArray(16384)
                    var total = 0
                    while (true) {
                        val r = inp.read(buf)
                        if (r <= 0) break
                        total += r
                        if (total > 50 * 1024 * 1024) throw RuntimeException("Файл слишком большой (>50 МБ)")
                        bos.write(buf, 0, r)
                    }
                    bos.toByteArray()
                } ?: throw RuntimeException("Не удалось прочитать файл")

                if (bytes.size < 4) throw RuntimeException("Пустой файл")

                // 1. Проверяем бинарный заголовок мира (.vhworld: первые 4 байта = версия 52/54/63/67)
                val firstInt = DataInputStream(ByteArrayInputStream(bytes)).readInt()
                val isWorldMagic = firstInt == 52 || firstInt == 54 || firstInt == 63 || firstInt == 67

                // 2. Проверяем ZIP / .vhpack заголовок (PK\x03\x04)
                val isZipMagic = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

                // 3. Проверяем текстовый формат (.vhmod / .lua)
                val headText = if (!isWorldMagic && !isZipMagic) String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8).trimStart() else ""
                val isNoCodeModText = headText.startsWith("WORLD|") || headText.startsWith("BLK|") || headText.startsWith("ITM|") ||
                    headText.startsWith("REC|") || headText.startsWith("MOB|") || headText.startsWith("RULE|")
                val isLuaText = headText.contains("VH.") || headText.contains("function(") || headText.contains("local ")

                val worldsDir = File(storageRoot, "worlds").also { it.mkdirs() }
                val modsDir = File(storageRoot, "mods").also { it.mkdirs() }

                when {
                    lower.endsWith(".vhworld") || isWorldMagic -> {
                        val fn = sanitizeFileName(rawName, ".vhworld")
                        val dst = File(worldsDir, fn)
                        dst.writeBytes(bytes)
                        pendingImports.add(ImportedFileEvent("world", fn.removeSuffix(".vhworld"), dst))
                    }
                    lower.endsWith(".vhmod") || isNoCodeModText -> {
                        val fn = sanitizeFileName(rawName, ".vhmod")
                        val dst = File(modsDir, fn)
                        dst.writeBytes(bytes)
                        pendingImports.add(ImportedFileEvent("nocode_mod", fn.removeSuffix(".vhmod"), dst))
                    }
                    lower.endsWith(".lua") || isLuaText -> {
                        val fn = sanitizeFileName(rawName, ".lua")
                        val dst = File(modsDir, fn)
                        dst.writeBytes(bytes)
                        pendingImports.add(ImportedFileEvent("lua_mod", fn.removeSuffix(".lua"), dst))
                    }
                    lower.endsWith(".vhpack") || lower.endsWith(".zip") || isZipMagic -> {
                        // Умный разбор ZIP / .vhpack: ищем внутри .vhworld, .vhmod или main.lua!
                        var hasMainLua = false
                        var extractedWorld: File? = null
                        var extractedNoCode: File? = null
                        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                            while (true) {
                                val e = zis.nextEntry ?: break
                                val en = e.name.lowercase()
                                if (en == "main.lua" || en == "scripts/main.lua") {
                                    hasMainLua = true
                                } else if (en.endsWith(".vhworld") && !e.isDirectory) {
                                    val wName = sanitizeFileName(File(e.name).name, ".vhworld")
                                    val outW = File(worldsDir, wName)
                                    outW.writeBytes(zis.readBytes())
                                    extractedWorld = outW
                                } else if ((en.endsWith(".vhmod") || en.endsWith("nocode_mods_v69.dat")) && !e.isDirectory) {
                                    val mName = sanitizeFileName(File(e.name).name, ".vhmod")
                                    val outM = File(modsDir, mName)
                                    outM.writeBytes(zis.readBytes())
                                    extractedNoCode = outM
                                }
                            }
                        }
                        if (extractedWorld != null) {
                            pendingImports.add(ImportedFileEvent("world", extractedWorld!!.nameWithoutExtension, extractedWorld))
                        }
                        if (extractedNoCode != null) {
                            pendingImports.add(ImportedFileEvent("nocode_mod", extractedNoCode!!.nameWithoutExtension, extractedNoCode))
                        }
                        if (hasMainLua || (extractedWorld == null && extractedNoCode == null)) {
                            val fn = sanitizeFileName(rawName, ".vhpack")
                            val dst = File(modsDir, fn)
                            dst.writeBytes(bytes)
                            pendingImports.add(ImportedFileEvent("vhpack", fn.removeSuffix(".vhpack"), dst))
                        }
                    }
                    else -> {
                        pendingImports.add(ImportedFileEvent("error", rawName, null, "Неподдерживаемый файл: $rawName (нужен .vhworld, .vhmod, .lua или .vhpack)"))
                    }
                }
            } catch (e: Exception) {
                pendingImports.add(ImportedFileEvent("error", "", null, "Ошибка импорта: ${e.message ?: "файл"}"))
            }
        }
    }

    fun shareFile(srcFile: File, chooserTitle: String): Boolean {
        val ctx = appContext ?: return false
        if (!srcFile.exists()) return false
        return try {
            val expDir = File(ctx.cacheDir, "export").also { it.mkdirs() }
            val safeName = srcFile.name.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            val dst = File(expDir, safeName)
            srcFile.copyTo(dst, overwrite = true)
            val uri = Uri.parse("content://$PROVIDER_AUTHORITY/export/$safeName")
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
            true
        } catch (_: Exception) { false }
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

    fun copyToClipboard(label: String, text: String): Boolean {
        val ctx = appContext ?: return false
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        } catch (_: Exception) { false }
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

    fun downloadAndInstallUpdate(onStatus: (String) -> Unit) {
        val ctx = appContext ?: return
        if (updateDownloading) return
        // Гарантированно сохраняем профиль, моды и мир ПЕРЕД началом обновления!
        try { onBeforeUpdateCallback?.invoke() } catch (_: Exception) {}

        val dlUrl = updateApkUrl
        if (dlUrl.isBlank()) {
            openBrowserUrl(REPO_RELEASES_URL)
            onStatus("Открыта страница релизов GitHub")
            return
        }

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
        onStatus("Профиль сохранён! Загружаем $updateTag...")

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

                try { onBeforeUpdateCallback?.invoke() } catch (_: Exception) {}
                updateDownloading = false
                updateProgress = 100
                updateStatusText = "Загружено! Открываем установщик..."
                onStatus("Обновление загружено! Профиль защищён.")

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

// Безопасный встроенный ContentProvider для установки APK И отправки миров/модов друзьям!
class ApkFileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun resolveFile(uri: Uri): File? {
        val ctx = context ?: return null
        val path = uri.path ?: return File(ctx.cacheDir, "voxelhorizon_update.apk")
        return if (path.startsWith("/export/")) {
            val safe = File(path.removePrefix("/export/")).name
            File(File(ctx.cacheDir, "export"), safe)
        } else {
            File(ctx.cacheDir, "voxelhorizon_update.apk")
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val f = resolveFile(uri) ?: throw java.io.FileNotFoundException("No context")
        if (!f.exists()) throw java.io.FileNotFoundException("File not found: ${f.name}")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        val p = uri.path ?: ""
        return if (p.endsWith(".apk", ignoreCase = true)) "application/vnd.android.package-archive"
        else "application/octet-stream"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val f = resolveFile(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = Array<Any?>(cols.size) { i ->
            when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> f?.name ?: "VoxelHorizon.dat"
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
