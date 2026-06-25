package com.ghost.serialization.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ghost.serialization.sample.ui.GhostSampleApp
import com.ghost.serialization.sample.util.AndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidContext.init(this)
        setContent { GhostSampleApp() }
    }
}

