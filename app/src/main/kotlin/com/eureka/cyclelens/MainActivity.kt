package com.eureka.cyclelens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eureka.cyclelens.ui.CycleLensTheme
import com.eureka.cyclelens.ui.CycleTrackerRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CycleLensTheme {
                CycleTrackerRoute()
            }
        }
    }
}
