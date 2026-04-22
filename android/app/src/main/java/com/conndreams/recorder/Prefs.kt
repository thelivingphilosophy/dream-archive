package com.conndreams.recorder

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("conn_dreams", Context.MODE_PRIVATE)

    var accountEmail: String?
        get() = sp.getString(KEY_ACCOUNT_EMAIL, null)
        set(v) = sp.edit().putString(KEY_ACCOUNT_EMAIL, v).apply()

    var driveFolderId: String?
        get() = sp.getString(KEY_FOLDER_ID, null)
        set(v) = sp.edit().putString(KEY_FOLDER_ID, v).apply()

    var driveFolderName: String
        get() = sp.getString(KEY_FOLDER_NAME, DEFAULT_FOLDER_NAME) ?: DEFAULT_FOLDER_NAME
        set(v) = sp.edit().putString(KEY_FOLDER_NAME, v).apply()

    var beepEnabled: Boolean
        get() = sp.getBoolean(KEY_BEEP, true)
        set(v) = sp.edit().putBoolean(KEY_BEEP, v).apply()

    var hapticEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTIC, v).apply()

    var maxLengthMinutes: Int
        get() = sp.getInt(KEY_MAX_LENGTH, 15)
        set(v) = sp.edit().putInt(KEY_MAX_LENGTH, v).apply()

    companion object {
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_FOLDER_ID = "drive_folder_id"
        private const val KEY_FOLDER_NAME = "drive_folder_name"
        private const val KEY_BEEP = "beep_enabled"
        private const val KEY_HAPTIC = "haptic_enabled"
        private const val KEY_MAX_LENGTH = "max_length_minutes"
        const val DEFAULT_FOLDER_NAME = "Conn Dreams — Audio Recordings"
    }
}
