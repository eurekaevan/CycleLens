package com.eureka.cyclelens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.eureka.cyclelens.overlay.OverlayService
import com.eureka.cyclelens.ui.CycleLensTheme
import com.eureka.cyclelens.ui.CycleTrackerRoute

class MainActivity : ComponentActivity() {
    private var overlayPermissionGranted by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startOverlayService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshOverlayPermission()

        setContent {
            CycleLensTheme {
                CycleTrackerRoute(
                    matchSession = cycleLensApplication.matchSession,
                    catalog = cycleLensApplication.cardCatalog,
                    artworkRepository = cycleLensApplication.cardArtworkRepository,
                    overlayQuickCards = cycleLensApplication.overlayQuickCards,
                    overlayConfiguration = cycleLensApplication.overlayConfiguration,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onEnableOverlayClick = ::openOverlayPermissionSettings,
                    onStartOverlayClick = ::startOverlay,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayPermission()
    }

    private fun refreshOverlayPermission() {
        overlayPermissionGranted = Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionSettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri(),
            ),
        )
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startOverlayService()
            }
        } else {
            refreshOverlayPermission()
            openOverlayPermissionSettings()
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    private val cycleLensApplication: CycleLensApplication
        get() = application as CycleLensApplication
}
