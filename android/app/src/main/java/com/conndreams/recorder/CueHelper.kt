package com.conndreams.recorder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short audio + haptic cues for eyes-closed bedside use. Tones are system-provided
 * so we don't ship media assets.
 */
class CueHelper(private val context: Context) {

    private val prefs = Prefs(context)

    fun start() {
        if (prefs.beepEnabled) playTone(ToneGenerator.TONE_PROP_BEEP, 180)
        if (prefs.hapticEnabled) vibrate(longArrayOf(0, 40, 80, 40))
    }

    fun stop() {
        if (prefs.beepEnabled) playTone(ToneGenerator.TONE_PROP_ACK, 220)
        if (prefs.hapticEnabled) vibrate(longArrayOf(0, 80))
    }

    fun error() {
        if (prefs.beepEnabled) playTone(ToneGenerator.TONE_PROP_NACK, 300)
        if (prefs.hapticEnabled) vibrate(longArrayOf(0, 60, 60, 60, 60, 60))
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            gen.startTone(toneType, durationMs)
            // Release shortly after the tone completes
            Thread {
                try { Thread.sleep((durationMs + 150).toLong()) } catch (_: InterruptedException) {}
                runCatching { gen.release() }
            }.start()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator ?: return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator.vibrate(effect, attrs)
    }
}
