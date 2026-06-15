package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.ui.screens.ApexFitApp
import com.example.ui.theme.ApexFitTheme
import com.example.utils.CoachingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule local background coaching checks and trigger worker safely in background
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                CoachingScheduler.schedule6AmDailyCoachingTask(applicationContext)
            } catch (t: Throwable) {
                android.util.Log.e("MainActivity", "Failed to schedule daily coaching task safely", t)
            }
        }
        
        // Enforce Edge to Edge insets
        enableEdgeToEdge()
        
        setContent {
            ApexFitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ApexFitApp()
                }
            }
        }
    }
}
