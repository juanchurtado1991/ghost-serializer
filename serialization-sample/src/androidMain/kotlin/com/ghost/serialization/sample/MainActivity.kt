package com.ghostserializer.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ghostserializer.sample.ui.GhostSampleApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.ghostserializer.sample.util.AndroidContext.init(this)
        setContent {
            GhostSampleApp()
        }
    }
}
