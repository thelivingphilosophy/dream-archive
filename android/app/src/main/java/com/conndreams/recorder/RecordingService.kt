package com.conndreams.recorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns the MediaRecorder lifecycle. Writes a .m4a file
 * to internal storage first (network-independent), then enqueues DriveUploadWorker
 * on stop. Emits local broadcasts so the widget can reflect state changes.
 */
class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtElapsed: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            mainHandler.postDelayed(this, 1000)
        }
    }
    private val maxDurationRunnable = Runnable {
        // Safety net: if setMaxDuration doesn't fire, we still stop ourselves.
        stopRecording(reason = "max-duration-watchdog")
    }
    private lateinit var cues: CueHelper
    private lateinit var prefs: Prefs

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        cues = CueHelper(this)
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording(reason = "user-stop")
            else -> if (!isRunning) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (isRunning) return

        val dir = File(filesDir, "pending").apply { mkdirs() }
        val file = File(dir, uniqueFilename(dir))
        outputFile = file

        startForegroundSafe(buildNotification(elapsedSeconds = 0))

        acquireWakeLock()
        requestAudioFocus()

        val maxMs = prefs.maxLengthMinutes.coerceIn(1, 120) * 60_000
        val rec = buildRecorder()
        try {
            rec.setOutputFile(file.absolutePath)
            rec.setMaxDuration(maxMs)
            rec.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording(reason = "max-duration")
                }
            }
            rec.setOnErrorListener { _, _, _ -> stopRecording(reason = "recorder-error") }
            rec.prepare()
            rec.start()
        } catch (t: Throwable) {
            cues.error()
            safeReleaseRecorder(rec)
            outputFile = null
            stopForegroundAndSelf()
            return
        }

        recorder = rec
        startedAtElapsed = SystemClock.elapsedRealtime()
        isRunning = true
        cues.start()
        mainHandler.post(tickRunnable)
        mainHandler.postDelayed(maxDurationRunnable, (maxMs + 2_000).toLong())
        RecordWidget.refresh(this)
    }

    private fun stopRecording(reason: String) {
        if (!isRunning) return
        isRunning = false
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.removeCallbacks(maxDurationRunnable)

        val rec = recorder
        recorder = null
        var savedOk = false
        if (rec != null) {
            try {
                rec.stop()
                savedOk = true
            } catch (_: Throwable) {
                // MediaRecorder.stop() throws when the stream ended abnormally — the .m4a
                // will be missing its MOOV atom, so the file is unplayable/untranscribable.
            }
            safeReleaseRecorder(rec)
        }

        releaseAudioFocus()
        releaseWakeLock()

        val file = outputFile
        outputFile = null

        if (file != null && file.exists()) {
            if (savedOk && file.length() > 1024) {
                cues.stop()
                enqueueUpload(file)
            } else {
                cues.error()
                quarantine(file)
            }
        } else {
            cues.stop()
        }
        RecordWidget.refresh(this)

        stopForegroundAndSelf()
    }

    private fun quarantine(file: File) {
        val damagedDir = File(filesDir, "damaged").apply { mkdirs() }
        val target = File(damagedDir, file.name)
        if (!file.renameTo(target)) {
            // Fallback: copy + delete. Same filesystem in practice, so renameTo should succeed.
            runCatching {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
    }

    private fun buildRecorder(): MediaRecorder {
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioChannels(1)
        rec.setAudioSamplingRate(44_100)
        rec.setAudioEncodingBitRate(64_000)
        return rec
    }

    private fun safeReleaseRecorder(rec: MediaRecorder) {
        runCatching { rec.reset() }
        runCatching { rec.release() }
    }

    private fun enqueueUpload(file: File) {
        val req = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setInputData(Data.Builder().putString(DriveUploadWorker.KEY_FILE_PATH, file.absolutePath).build())
            .setConstraints(DriveUploadWorker.constraints())
            .addTag(DriveUploadWorker.TAG)
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("upload-${file.name}", androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamArchive:recording").apply {
            setReferenceCounted(false)
            acquire(16 * 60_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) stopRecording(reason = "focus-loss")
                }
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        }
    }

    private fun releaseAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun startForegroundSafe(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun updateNotification() {
        val seconds = ((SystemClock.elapsedRealtime() - startedAtElapsed) / 1000).toInt()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(seconds))
    }

    private fun buildNotification(elapsedSeconds: Int): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openMain = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FORCE_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mm = elapsedSeconds / 60
        val ss = elapsedSeconds % 60
        val elapsed = String.format(Locale.US, "%02d:%02d", mm, ss)
        return NotificationCompat.Builder(this, CHANNEL_RECORDING)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText(getString(R.string.notif_recording_text, elapsed))
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openMain)
            .addAction(R.drawable.ic_stop, getString(R.string.notif_stop_action), stopPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_RECORDING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RECORDING,
                    getString(R.string.notif_channel_recording),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        if (nm.getNotificationChannel(CHANNEL_UPLOAD) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_UPLOAD,
                    getString(R.string.notif_channel_upload),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun uniqueFilename(dir: File): String {
        val base = ISO_FILENAME.format(Date())
        var name = "$base.m4a"
        var i = 2
        while (File(dir, name).exists()) {
            name = "${base}_${i}.m4a"
            i += 1
        }
        return name
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.removeCallbacks(maxDurationRunnable)
        recorder?.let { safeReleaseRecorder(it) }
        recorder = null
        releaseAudioFocus()
        releaseWakeLock()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.conndreams.recorder.action.START"
        const val ACTION_STOP = "com.conndreams.recorder.action.STOP"

        const val STATE_IDLE = "idle"
        const val STATE_RECORDING = "recording"
        const val STATE_UPLOADING = "uploading"

        private const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_UPLOAD = "upload"
        private const val NOTIF_ID = 1001

        // Millisecond precision — collision-proof for back-to-back recordings.
        private val ISO_FILENAME = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US)

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).apply { action = ACTION_START }
    }
}
