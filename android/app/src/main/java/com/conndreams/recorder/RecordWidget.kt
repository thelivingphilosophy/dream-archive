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
        val state = if (RecordingService.isRunning) RecordingService.STATE_RECORDING else RecordingService.STATE_IDLE
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, state))
        }
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
    }
}
