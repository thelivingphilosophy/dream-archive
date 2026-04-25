package com.conndreams.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.conndreams.recorder.ui.SettingsCallbacks
import com.conndreams.recorder.ui.SettingsScreen
import com.conndreams.recorder.ui.SettingsState
import com.conndreams.recorder.ui.theme.ConnDreamsTheme
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var drive: DriveClient

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleSignInResult(result.data)
    }
    private val recoverAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
    }
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            ContextCompat.startForegroundService(this, RecordingService.startIntent(this))
        } else {
            Toast.makeText(this, "Microphone permission is required to record.", Toast.LENGTH_LONG).show()
        }
    }

    private var accountEmail by mutableStateOf<String?>(null)
    private var folderName by mutableStateOf("")
    private var pendingCount by mutableIntStateOf(0)
    private var damagedCount by mutableIntStateOf(0)
    private var isRecording by mutableStateOf(false)
    private var recordOnLaunch by mutableStateOf(true)
    private var beepEnabled by mutableStateOf(true)
    private var hapticEnabled by mutableStateOf(true)
    private var maxLengthMinutes by mutableIntStateOf(15)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        drive = DriveClient(this)

        // F1: direct launcher tap (home icon / Samsung Side Key) toggles recording when configured.
        if (shouldToggleRecording(intent)) {
            val action = if (RecordingService.isRunning) RecordingService.ACTION_STOP else RecordingService.ACTION_START
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingService::class.java).setAction(action)
            )
            finishAndRemoveTask()
            return
        }

        refresh()

        setContent {
            ConnDreamsTheme {
                val state = remember(
                    accountEmail, folderName, pendingCount, damagedCount,
                    isRecording, recordOnLaunch, beepEnabled, hapticEnabled, maxLengthMinutes,
                ) {
                    SettingsState(
                        accountEmail = accountEmail,
                        folderName = folderName,
                        pendingCount = pendingCount,
                        damagedCount = damagedCount,
                        isRecording = isRecording,
                        recordOnLaunch = recordOnLaunch,
                        beepEnabled = beepEnabled,
                        hapticEnabled = hapticEnabled,
                        maxLengthMinutes = maxLengthMinutes,
                    )
                }
                val callbacks = SettingsCallbacks(
                    onConnect = ::launchSignIn,
                    onTestRecord = ::handleTestRecord,
                    onCleanupDamaged = ::cleanupDamaged,
                    onRecordOnLaunchChange = { v -> prefs.recordOnLaunch = v; recordOnLaunch = v },
                    onBeepChange = { v -> prefs.beepEnabled = v; beepEnabled = v },
                    onHapticChange = { v -> prefs.hapticEnabled = v; hapticEnabled = v },
                    onMaxLengthChange = { v -> prefs.maxLengthMinutes = v; maxLengthMinutes = v },
                )
                SettingsScreen(state = state, callbacks = callbacks)
            }
        }

        val requestingPerms = intent.getBooleanExtra(EXTRA_REQUEST_PERMISSION, false)
        if (requestingPerms) requestRuntimePermissions()
        if (intent.getBooleanExtra(EXTRA_REQUEST_AUTH, false)) launchSignIn()
        if (!requestingPerms) maybeRequestNotificationPermission()
    }

    private fun shouldToggleRecording(intent: Intent): Boolean {
        if (intent.action != Intent.ACTION_MAIN) return false
        if (intent.categories?.contains(Intent.CATEGORY_LAUNCHER) != true) return false
        if (intent.getBooleanExtra(EXTRA_FORCE_SETTINGS, false)) return false
        if (intent.getBooleanExtra(EXTRA_REQUEST_AUTH, false)) return false
        if (intent.getBooleanExtra(EXTRA_REQUEST_PERMISSION, false)) return false
        if (!prefs.recordOnLaunch) return false
        if (drive.currentAccount() == null) return false
        if (prefs.driveFolderId == null) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return false
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (shouldToggleRecording(intent)) {
            val action = if (RecordingService.isRunning) RecordingService.ACTION_STOP else RecordingService.ACTION_START
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingService::class.java).setAction(action)
            )
            finishAndRemoveTask()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        refresh()
    }

    private fun refresh() {
        val account = drive.currentAccount()
        accountEmail = account?.email
        folderName = prefs.driveFolderName
        pendingCount = countFiles("pending")
        damagedCount = countFiles("damaged")
        isRecording = RecordingService.isRunning
        recordOnLaunch = prefs.recordOnLaunch
        beepEnabled = prefs.beepEnabled
        hapticEnabled = prefs.hapticEnabled
        maxLengthMinutes = prefs.maxLengthMinutes
    }

    private fun countFiles(subdir: String): Int =
        File(filesDir, subdir).listFiles { f -> f.extension == "m4a" }?.size ?: 0

    private fun launchSignIn() {
        val client = drive.buildSignInClient()
        client.signOut().addOnCompleteListener {
            signInLauncher.launch(client.signInIntent)
        }
    }

    private fun handleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            prefs.accountEmail = account?.email
            lifecycleScope.launch { ensureFolderAfterSignIn(account) }
        } catch (e: Exception) {
            Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun ensureFolderAfterSignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        val acct = account?.account ?: return
        try {
            val token = drive.getAccessToken(acct)
            val id = drive.ensureFolder(token, prefs.driveFolderName)
            prefs.driveFolderId = id
            runOnUiThread {
                Toast.makeText(this, "Connected. Drive folder ready.", Toast.LENGTH_SHORT).show()
                refresh()
            }
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { recoverAuthLauncher.launch(it) }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Drive setup failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun handleTestRecord() {
        if (RecordingService.isRunning) {
            startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
            isRecording = false
            return
        }
        if (!hasRecordAudio()) {
            requestRuntimePermissions()
            return
        }
        ContextCompat.startForegroundService(this, RecordingService.startIntent(this))
        isRecording = true
    }

    private fun hasRecordAudio(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(perms.toTypedArray())
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) return
        requestPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun cleanupDamaged() {
        val damagedDir = File(filesDir, "damaged")
        val removed = damagedDir.listFiles { f -> f.extension == "m4a" }?.count { it.delete() } ?: 0
        if (removed > 0) Toast.makeText(this, "Removed $removed damaged file(s).", Toast.LENGTH_SHORT).show()
        refresh()
    }

    companion object {
        const val EXTRA_REQUEST_PERMISSION = "request_permission"
        const val EXTRA_REQUEST_AUTH = "request_auth"
        const val EXTRA_FORCE_SETTINGS = "force_settings"
    }
}
