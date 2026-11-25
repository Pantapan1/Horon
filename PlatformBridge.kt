package com.voxelhorizon.nativegame

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object PlatformBridge {
    const val CURRENT_VERSION = "6.3.0"
    const val CURRENT_TAG = "v6.3-rust-latest"
    const val REPO_API_LATEST = "https://api.github.com/repos/Pantapan1/Horon/releases/latest"
    const val REPO_RELEASES_URL = "https://github.com/Pantapan1/Horon/releases"
    const val CHANNEL_ID = "voxelhorizon_alerts"

    @Volatile var appContext: Context? = null
    @Volatile var backupDir: File? = null

    @Volatile var updateAvailable = false
    @Volatile var updateTag = ""
    @Volatile var updateTitle = ""
    @Volatile var updateUrl = REPO_RELEASES_URL
    @Volatile var updateStatusText = "Проверка обновлений..."

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

    private fun parseVersionTuple(tag: String): Int {
        // Parses e.g. "v6.3-rust-latest" -> 60300, "v6.2.1" -> 60201
        val nums = Regex("\\d+").findAll(tag).map { it.value.toIntOrNull() ?: 0 }.toList()
        val maj = nums.getOrElse(0) { 0 }
        val min = nums.getOrElse(1) { 0 }
        val pat = nums.getOrElse(2) { 0 }
        return maj * 10000 + min * 100 + pat
    }

    fun checkGitHubUpdate() {
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
                    val htmlUrl = REPO_RELEASES_URL
                    val remoteVer = parseVersionTuple(tag)
                    val localVer = parseVersionTuple(CURRENT_VERSION)
                    if (tag.isNotBlank() && (remoteVer > localVer || (remoteVer == localVer && tag != CURRENT_TAG && tag.contains("v")))) {
                        updateAvailable = true
                        updateTag = tag
                        updateTitle = name.ifBlank { "Новая версия $tag" }
                        updateUrl = htmlUrl
                        updateStatusText = "Доступно обновление: $tag!"
                        showNotification(
                            7002,
                            "Вышла новая версия VoxelHorizon ($tag)!",
                            "Нажмите, чтобы скачать обновление из GitHub Releases.",
                            htmlUrl
                        )
                    } else {
                        updateAvailable = false
                        updateStatusText = "Установлена актуальная версия ($CURRENT_VERSION)"
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
