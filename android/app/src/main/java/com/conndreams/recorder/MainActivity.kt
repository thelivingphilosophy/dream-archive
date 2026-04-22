package com.conndreams.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var drive: DriveClient

    private lateinit var driveStatus: TextView
    private lateinit var folderName: TextView
    private lateinit var pendingCount: TextView
    private lateinit var damagedCount: TextView
    private lateinit var connectButton: Button
    private lateinit var testRecordButton: Button
    private lateinit var beepSwitch: SwitchMaterial
    private lateinit var hapticSwitch: SwitchMaterial
    private lateinit var maxLengthGroup: RadioGroup

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
            startService(RecordingService.startIntent(this))
        } else {
            Toast.makeText(this, "Microphone permission is required to record.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        drive = DriveClient(this)

        driveStatus = findViewById(R.id.drive_status)
        folderName = findViewById(R.id.folder_name)
        pendingCount = findViewById(R.id.pending_count)
        damagedCount = findViewById(R.id.damaged_count)
        connectButton = findViewById(R.id.connect_button)
        testRecordButton = findViewById(R.id.test_record_button)
        beepSwitch = findViewById(R.id.beep_switch)
        hapticSwitch = findViewById(R.id.haptic_switch)
        maxLengthGroup = findViewById(R.id.max_length_group)

        connectButton.setOnClickListener { launchSignIn() }
        testRecordButton.setOnClickListener { handleTestRecord() }

        beepSwitch.setOnCheckedChangeListener { _, c -> prefs.beepEnabled = c }
        hapticSwitch.setOnCheckedChangeListener { _, c -> prefs.hapticEnabled = c }
        maxLengthGroup.setOnCheckedChangeListener { _, id ->
            prefs.maxLengthMinutes = when (id) {
                R.id.max_30 -> 30
                R.id.max_60 -> 60
                else -> 15
            }
        }

        damagedCount.setOnClickListener { cleanupDamaged() }

        if (intent.getBooleanExtra(EXTRA_REQUEST_PERMISSION, false)) requestRuntimePermissions()
        if (intent.getBooleanExtra(EXTRA_REQUEST_AUTH, false)) launchSignIn()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val account = drive.currentAccount()
        if (account != null) {
            driveStatus.text = getString(R.string.drive_connected, account.email ?: "")
            connectButton.text = getString(R.string.reconnect_drive)
            folderName.text = "${getString(R.string.drive_folder_label)}: ${prefs.driveFolderName}"
        } else {
            driveStatus.setText(R.string.drive_not_connected)
            connectButton.setText(R.string.connect_drive)
            folderName.text = ""
        }

        val pendingDir = File(filesDir, "pending")
        val pending = pendingDir.listFiles { f -> f.extension == "m4a" && f.length() > 1024 }?.size ?: 0
        if (pending > 0) {
            pendingCount.visibility = android.view.View.VISIBLE
            pendingCount.text = getString(R.string.pending_uploads, pending)
        } else {
            pendingCount.visibility = android.view.View.GONE
        }

        val damaged = pendingDir.listFiles { f -> f.extension == "m4a" && f.length() <= 1024 }?.size ?: 0
        if (damaged > 0) {
            damagedCount.visibility = android.view.View.VISIBLE
            damagedCount.text = getString(R.string.damaged_files, damaged)
        } else {
            damagedCount.visibility = android.view.View.GONE
        }

        beepSwitch.isChecked = prefs.beepEnabled
        hapticSwitch.isChecked = prefs.hapticEnabled
        when (prefs.maxLengthMinutes) {
            30 -> maxLengthGroup.check(R.id.max_30)
            60 -> maxLengthGroup.check(R.id.max_60)
            else -> maxLengthGroup.check(R.id.max_15)
        }
    }

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
            prefs.driveFolderName = prefs.driveFolderName // keep default
            lifecycleScope.launch { ensureFolderAfterSignIn(account) }
        } catch (e: Exception) {
            Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun ensureFolderAfterSignIn(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        val acct = account?.account ?: return
        try {
            val token = drive.getAccessToken(acct)
            val id = withContext(Dispatchers.IO) { drive.ensureFolder(token, prefs.driveFolderName) }
            prefs.driveFolderId = id
            runOnUiThread {
                Toast.makeText(this, "Connected. Drive folder ready.", Toast.LENGTH_SHORT).show()
                refresh()
            }
        } catch (e: UserRecoverableAuthException) {
            recoverAuthLauncher.launch(e.intent)
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Drive setup failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun handleTestRecord() {
        if (RecordingService.isRunning) {
            startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
            testRecordButton.setText(R.string.record_test)
            return
        }
        if (!hasAllPermissions()) {
            requestRuntimePermissions()
            return
        }
        ContextCompat.startForegroundService(this, RecordingService.startIntent(this))
        testRecordButton.setText(R.string.stop_recording)
    }

    private fun hasAllPermissions(): Boolean {
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return audio && notif
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(perms.toTypedArray())
    }

    private fun cleanupDamaged() {
        val pendingDir = File(filesDir, "pending")
        val removed = pendingDir.listFiles { f -> f.extension == "m4a" && f.length() <= 1024 }?.count { it.delete() } ?: 0
        if (removed > 0) Toast.makeText(this, "Removed $removed damaged file(s).", Toast.LENGTH_SHORT).show()
        refresh()
    }

    companion object {
        const val EXTRA_REQUEST_PERMISSION = "request_permission"
        const val EXTRA_REQUEST_AUTH = "request_auth"
    }
}
