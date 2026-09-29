package com.stepadder.app

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/** Local privacy screen. Health Connect links to this from its permission dialog. */
class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setText(R.string.privacy_text)
            textSize = 16f
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(text)
        })
    }
}
