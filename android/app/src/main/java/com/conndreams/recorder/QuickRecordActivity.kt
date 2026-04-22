package com.conndreams.recorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * No-UI launcher target for the Side Key double-press, the home-screen widget,
 * and any other one-tap entry point. Toggles [RecordingService]: starts if idle,
 * stops if already recording, then finishes immediately.
 */
class QuickRecordActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleToggle()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleToggle()
    }

    private fun handleToggle() {
        if (!hasRecordPermission()) {
            // Need an interactive UI to request the runtime permission, so bounce to MainActivity.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_PERMISSION, true)
            )
            finishAndRemoveTask()
            return
        }

        val recording = RecordingService.isRunning
        val intent = Intent(this, RecordingService::class.java).apply {
            action = if (recording) RecordingService.ACTION_STOP else RecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        finishAndRemoveTask()
    }

    private fun hasRecordPermission(): Boolean {
        val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return audioOk && notifOk
    }
}
