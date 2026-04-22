package com.conndreams.recorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RecordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = currentState()
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, state))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == RecordingService.ACTION_STATE_CHANGED || intent.action == ACTION_REFRESH) {
            refreshAll(context, intent.getStringExtra(RecordingService.EXTRA_STATE) ?: currentState())
        }
    }

    private fun currentState(): String =
        if (RecordingService.isRunning) RecordingService.STATE_RECORDING else RecordingService.STATE_IDLE

    private fun refreshAll(context: Context, state: String) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, RecordWidget::class.java))
        for (id in ids) mgr.updateAppWidget(id, buildViews(context, state))
    }

    private fun buildViews(context: Context, state: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_record)

        val bgRes = when (state) {
            RecordingService.STATE_RECORDING -> R.drawable.widget_button_recording
            RecordingService.STATE_UPLOADING -> R.drawable.widget_button_uploading
            else -> R.drawable.widget_button_idle
        }
        val iconRes = when (state) {
            RecordingService.STATE_RECORDING -> R.drawable.ic_stop
            RecordingService.STATE_UPLOADING -> R.drawable.ic_upload
            else -> R.drawable.ic_mic
        }
        views.setInt(R.id.widget_button, "setBackgroundResource", bgRes)
        views.setImageViewResource(R.id.widget_button, iconRes)

        val tapIntent = Intent(context, QuickRecordActivity::class.java)
            .setAction("com.conndreams.recorder.action.QUICK_RECORD")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        return views
    }

    companion object {
        const val ACTION_REFRESH = "com.conndreams.recorder.action.WIDGET_REFRESH"
    }
}
