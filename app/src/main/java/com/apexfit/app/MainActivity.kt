package com.apexfit.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            installSplashScreen()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "installSplashScreen fallback", e)
        }
        super.onCreate(savedInstanceState)

        // Note: seeding logic moved into the Compose lifecycle below so we can react if the
        // DataStore flag becomes false while the process is still alive (e.g., user cleared app data).

        // Enforce Edge to Edge insets
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ApexFitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val access by com.apexfit.app.utils.LocalResetCoordinator.access.collectAsStateWithLifecycle()
                    when (access) {
                        com.apexfit.app.utils.ResetAccess.READY -> ApexFitApp()
                        else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if (access == com.apexfit.app.utils.ResetAccess.FAILED) {
                                Text("Local data deletion did not finish. Retry to complete it before using Apex Fit.")
                                TextButton(onClick = { (application as ApexFitApplication).retryPendingReset() }) { Text("Retry deletion") }
                            } else {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(if (access == com.apexfit.app.utils.ResetAccess.RESETTING) "Deleting local data…" else "Checking local data…")
                            }
                        }
                    }
                }
            }
        }
    }
}