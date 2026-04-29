package com.conndreams.recorder

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Dev-only preview of the home-screen widget layout. Inflate the widget XML directly
 * into an Activity so we can screencap it without dragging through the launcher.
 *
 * Launch: adb shell am start -n com.conndreams.recorder/.WidgetPreviewActivity
 *
 * Strip before final release.
 */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val width = (220 * density).toInt()
        val height = (90 * density).toInt()

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#09080D"))
        }
        val widget = LayoutInflater.from(this).inflate(R.layout.widget_record, container, false)
        widget.layoutParams = FrameLayout.LayoutParams(width, height).apply {
            gravity = Gravity.CENTER
        }
        container.addView(widget)
        setContentView(container, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }
}
