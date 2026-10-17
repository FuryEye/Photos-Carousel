package com.photoscarousel.widget

import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Launcher activity; widgets are added from the home screen's widget picker. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            text = "Long-press your home screen, open Widgets, and add \"Photos Carousel\"."
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
        })
    }
}
