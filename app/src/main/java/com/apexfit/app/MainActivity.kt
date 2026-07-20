package com.apexfit.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apexfit.app.data.AppDatabase
import com.apexfit.app.data.DataStoreManager
import com.apexfit.app.data.Exercise
import com.apexfit.app.data.ExerciseMetadata
import com.apexfit.app.ui.screens.ApexFitApp
import com.apexfit.app.ui.theme.ApexFitTheme
import com.apexfit.app.utils.CoachingScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "apex_prefs"
        private const val KEY_NOTIFICATIONS_REQUESTED = "notifications_requested"
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // granted == true => can post notifications; false => cannot
            // Intentionally mark requested so we don't re-prompt on every launch.
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_NOTIFICATIONS_REQUESTED, true).apply()
        }

    private fun maybeRequestNotificationPermission() {
        // Only for Android 13+; don't request if already granted or if we already requested once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val alreadyRequested = prefs.getBoolean(KEY_NOTIFICATIONS_REQUESTED, false)

            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission && !alreadyRequested) {
                // Launch the system permission dialog. Non-blocking — continues after this call.
                requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                // Mark requested immediately so we don't prompt again on subsequent starts.
                prefs.edit().putBoolean(KEY_NOTIFICATIONS_REQUESTED, true).apply()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "apexfit" || uri.host != "screen") {
            android.util.Log.w("DeepLink", "Invalid deep link rejected")
            return
        }
        val screen = uri.lastPathSegment ?: return
        val validScreens = setOf("home", "train", "nutrition", "progress")
        if (screen !in validScreens) {
            android.util.Log.w("DeepLink", "Unknown screen rejected: $screen")
            return
        }
        val fitnessViewModel = ViewModelProvider(this, FitnessViewModel.Factory)[FitnessViewModel::class.java]
        fitnessViewModel.selectTab(when (screen) {
            "home" -> 0
            "train" -> 1
            "nutrition" -> 2
            "progress" -> 3
            else -> 0
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Note: seeding logic moved into the Compose lifecycle below so we can react if the
        // DataStore flag becomes false while the process is still alive (e.g., user cleared app data).

        // Enforce Edge to Edge insets
        enableEdgeToEdge()

        handleDeepLink(intent)
        maybeRequestNotificationPermission()

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