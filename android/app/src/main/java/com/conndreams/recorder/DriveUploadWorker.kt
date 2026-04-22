package com.conndreams.recorder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import com.google.android.gms.auth.UserRecoverableAuthException
import java.io.File

class DriveUploadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val file = File(path)
        if (!file.exists()) return Result.success()

        val prefs = Prefs(applicationContext)
        val drive = DriveClient(applicationContext)
        val account = drive.currentAccount()?.account
            ?: run { notifyAuthNeeded(); return Result.retry() }

        val token = try {
            drive.getAccessToken(account)
        } catch (_: UserRecoverableAuthException) {
            notifyAuthNeeded()
            return Result.retry()
        } catch (_: Throwable) {
            return Result.retry()
        }

        val folderId = try {
            prefs.driveFolderId ?: drive.ensureFolder(token, prefs.driveFolderName).also { prefs.driveFolderId = it }
        } catch (_: DriveClient.NotFoundException) {
            prefs.driveFolderId = null
            return Result.retry()
        } catch (_: Throwable) {
            return Result.retry()
        }

        try {
            drive.uploadFile(token, folderId, file)
        } catch (_: DriveClient.NotFoundException) {
            prefs.driveFolderId = null
            return Result.retry()
        } catch (_: Throwable) {
            if (runAttemptCount >= 5) notifyFailed()
            return Result.retry()
        }

        file.delete()
        notifyDone()
        notifyWidgetOfQueueState()
        return Result.success()
    }

    private fun notifyWidgetOfQueueState() {
        val pending = File(applicationContext.filesDir, "pending")
        val stillHasFiles = pending.listFiles()?.any { it.extension == "m4a" } ?: false
        val state = if (stillHasFiles || RecordingService.isRunning) {
            if (RecordingService.isRunning) RecordingService.STATE_RECORDING else RecordingService.STATE_UPLOADING
        } else RecordingService.STATE_IDLE
        RecordWidget.notifyStateChanged(applicationContext, state)
    }

    private fun notifyDone() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = NotificationCompat.Builder(applicationContext, RecordingService.CHANNEL_UPLOAD)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(applicationContext.getString(R.string.notif_upload_done))
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()
        nm.notify(NOTIF_DONE, n)
    }

    private fun notifyFailed() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_FORCE_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(applicationContext, RecordingService.CHANNEL_UPLOAD)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(applicationContext.getString(R.string.notif_upload_failed))
            .setContentIntent(open)
            .setAutoCancel(false)
            .build()
        nm.notify(NOTIF_FAILED, n)
    }

    private fun notifyAuthNeeded() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_REQUEST_AUTH, true)
                .putExtra(MainActivity.EXTRA_FORCE_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(applicationContext, RecordingService.CHANNEL_UPLOAD)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(applicationContext.getString(R.string.notif_auth_revoked))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_AUTH, n)
    }

    companion object {
        const val TAG = "drive-upload"
        const val KEY_FILE_PATH = "file_path"
        private const val NOTIF_DONE = 2001
        private const val NOTIF_FAILED = 2002
        private const val NOTIF_AUTH = 2003

        fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
