package com.conndreams.recorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * No-UI launcher target for the home-screen widget and any other one-tap entry point.
 * Toggles [RecordingService]: starts if idle, stops if already recording, then finishes.
 *
 * Note: the Samsung Side Key gesture targets [MainActivity] (the LAUNCHER activity),
 * which auto-delegates to RecordingService when configured.
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
        if (!hasRecordAudio()) {
            // Need an interactive UI to request the mic permission — bounce to MainActivity.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_PERMISSION, true)
                    .putExtra(MainActivity.EXTRA_FORCE_SETTINGS, true)
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

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
