package com.ezsocial.video

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import com.ezsocial.engine.VideoEngine
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(null, runnable, "EzSocial-download", 64L * 1024 * 1024)
    }
    private val pending = AtomicInteger(0)
    @Volatile private var currentId: String? = null
    private val jobs = ConcurrentHashMap<String, AtomicBoolean>()
    private val prefs by lazy { getSharedPreferences("downloads", MODE_PRIVATE) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var lastNotice = 0L

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel("downloads", "Video downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction("CANCEL"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "downloads").setSmallIcon(R.drawable.ic_download)
            .setContentTitle("EzSocial Video").setContentText(text).setContentIntent(open)
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Cancel", cancel).build()).build()
    }
    private fun report(text: String, record: Boolean = false, uri: Uri? = null) {
        val edit = prefs.edit().putString("status", text)
        if (record) {
            val prior = prefs.getString("history", "").orEmpty()
            edit.putString("history", (listOf(text) + prior.split("\n\n")).take(10).joinToString("\n\n"))
        }
        edit.apply()
        if (record) {
            val intent = if (uri == null) Intent(this, MainActivity::class.java) else
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val open = PendingIntent.getActivity(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notifications.notify(2, Notification.Builder(this, "downloads").setSmallIcon(R.drawable.ic_download)
                .setContentTitle("EzSocial Video").setContentText(text).setContentIntent(open)
                .setOngoing(false).setAutoCancel(true).build())
        }
        if (System.currentTimeMillis() - lastNotice > 800 || record) {
            notifications.notify(1, notification(text))
            lastNotice = System.currentTimeMillis()
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CANCEL") {
            jobs.values.forEach { token -> synchronized(token) { token.set(true) } }
            if (pending.get() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelfResult(startId) }
            return START_NOT_STICKY
        }
        val id = UUID.randomUUID().toString()
        val cancelled = AtomicBoolean(false)
        jobs[id] = cancelled
        startForeground(1, notification("Preparing download…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        pending.incrementAndGet()
        val quality = prefs.getInt("quality", 0)
        executor.execute {
            val wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EzSocial:download")
            wake.acquire(60 * 60 * 1000L)
            var directory: File? = null
            try {
                currentId = id
                if (cancelled.get()) error("Cancelled")
                val url = Links.extract(intent?.getStringExtra("url").orEmpty())
                directory = File(cacheDir, "downloads/$id").apply { mkdirs() }
                val limit = when (quality) { 1 -> 720; 2 -> Int.MAX_VALUE; else -> 1080 }
                val video = VideoEngine.download(url, directory, limit, { progress, message ->
                    if (!cancelled.get()) report("$message · ${progress.coerceIn(0, 99)}%")
                }, cancelled)
                saveToGallery(video, cancelled)
            } catch (e: Throwable) {
                android.util.Log.e("EzSocial", "download failed", e)
                val reason = when (e) {
                    is OutOfMemoryError -> "the extractor ran out of memory (OutOfMemoryError)"
                    is StackOverflowError -> "the extractor ran out of stack (StackOverflowError)"
                    else -> e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                }
                report(if (cancelled.get()) "Download cancelled." else "Failed: ${reason.take(700)}", true)
            } finally {
                directory?.deleteRecursively()
                currentId = null
                jobs.remove(id)
                if (wake.isHeld) wake.release()
                pending.decrementAndGet()
                android.os.Handler(mainLooper).post {
                    if (pending.get() == 0) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun saveToGallery(file: File, cancelled: AtomicBoolean): Uri {
        val mime = when(file.extension.lowercase()) { "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; else -> "video/mp4" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EzSocial")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create gallery entry.")
        try {
            val output = contentResolver.openOutputStream(uri) ?: error("Could not open gallery file.")
            output.use { destination -> file.inputStream().use { it.copyTo(destination) } }
            synchronized(cancelled) {
                check(!cancelled.get()) { "Cancelled" }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                check(contentResolver.update(uri, values, null, null) > 0) { "Could not publish gallery entry." }
                report("Saved to gallery: ${file.name}", true, uri)
            }
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        jobs.values.forEach { it.set(true) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        jobs.values.forEach { it.set(true) }
        executor.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
