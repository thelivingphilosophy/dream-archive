package com.conndreams.recorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.io.File

class RecordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = currentState(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, state))
        }
    }

    private fun buildViews(context: Context, state: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_record)

        val dotRes = when (state) {
            RecordingService.STATE_RECORDING -> R.drawable.widget_state_dot_recording
            RecordingService.STATE_UPLOADING -> R.drawable.widget_state_dot_uploading
            else -> R.drawable.widget_state_dot_idle
        }
        val stateRes = when (state) {
            RecordingService.STATE_RECORDING -> R.string.widget_state_recording
            RecordingService.STATE_UPLOADING -> R.string.widget_state_uploading
            else -> R.string.widget_state_idle
        }
        views.setInt(R.id.widget_state_dot, "setBackgroundResource", dotRes)
        views.setTextViewText(R.id.widget_state, context.getString(stateRes))

        val tapIntent = Intent(context, QuickRecordActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        return views
    }

    companion object {
        /**
         * Single source of truth: derive widget state from the foreground service flag and
         * whether anything is queued for upload. Call this from any state-mutating path.
         */
        fun refresh(context: Context) {
            notifyStateChanged(context, currentState(context))
        }

        /**
         * Push a fresh set of RemoteViews to every live widget instance — call this from
         * service/worker code on state transitions. Cheaper and more reliable than broadcasting.
         */
        fun notifyStateChanged(context: Context, state: String) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, RecordWidget::class.java))
            if (ids.isEmpty()) return
            val provider = RecordWidget()
            for (id in ids) {
                mgr.updateAppWidget(id, provider.buildViews(context, state))
            }
        }

        private fun currentState(context: Context): String {
            if (RecordingService.isRunning) return RecordingService.STATE_RECORDING
            val pending = File(context.filesDir, "pending")
            val hasQueue = pending.listFiles { f -> f.extension == "m4a" }?.isNotEmpty() == true
            return if (hasQueue) RecordingService.STATE_UPLOADING else RecordingService.STATE_IDLE
        }
    }
}
