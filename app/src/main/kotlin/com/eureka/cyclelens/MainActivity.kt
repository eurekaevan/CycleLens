package com.eureka.cyclelens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.eureka.cyclelens.overlay.OverlayService
import com.eureka.cyclelens.capture.CaptureService
import com.eureka.cyclelens.capture.CaptureProfile
import com.eureka.cyclelens.capture.AnalysisDelay
import com.eureka.cyclelens.ui.CycleLensTheme
import com.eureka.cyclelens.ui.CycleTrackerRoute

class MainActivity : ComponentActivity() {
    private var overlayPermissionGranted by mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        startOverlayService()
    }
    private val captureConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val captureState = cycleLensApplication.captureSessionState
        val resultData = result.data
        if (result.resultCode != RESULT_OK || resultData == null) {
            captureState.rejectConsent()
            return@registerForActivityResult
        }

        if (!captureState.acceptConsent()) {
            return@registerForActivityResult
        }
        try {
            CaptureService.start(
                this,
                result.resultCode,
                resultData,
                cycleLensApplication.captureConfiguration.profile.value,
            )
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to start capture service", error)
            captureState.fail("Screen capture service could not start")
        }
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
                    captureState = cycleLensApplication.captureSessionState.state,
                    captureProfile = cycleLensApplication.captureConfiguration.profile,
                    analysisDelay = cycleLensApplication.analysisConfiguration.delay,
                    debugSnapshotState = cycleLensApplication.captureDebugSnapshot.state,
                    overlayPermissionGranted = overlayPermissionGranted,
                    onEnableOverlayClick = ::openOverlayPermissionSettings,
                    onStartOverlayClick = ::startOverlay,
                    onStartCaptureClick = ::startCapture,
                    onStopCaptureClick = ::stopCapture,
                    onCaptureProfileChanged = ::setCaptureProfile,
                    onAnalysisDelayChanged = ::setAnalysisDelay,
                    onSaveDebugFrameClick = ::saveDebugFrame,
                    onDeleteDebugFrameClick = ::deleteDebugFrame,
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

    private fun startCapture() {
        val captureState = cycleLensApplication.captureSessionState
        if (!captureState.requestConsent()) {
            return
        }

        try {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            captureConsentLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to open capture authorization", error)
            captureState.fail("Screen capture authorization could not open")
        }
    }

    private fun stopCapture() {
        CaptureService.stop(this)
    }

    private fun setCaptureProfile(profile: CaptureProfile) {
        cycleLensApplication.captureConfiguration.setProfile(
            profile,
            cycleLensApplication.captureSessionState.state.value,
        )
    }

    private fun saveDebugFrame() {
        if (BuildConfig.DEBUG) {
            CaptureService.saveDebugFrame(this)
        }
    }

    private fun setAnalysisDelay(delay: AnalysisDelay) {
        if (BuildConfig.DEBUG) {
            cycleLensApplication.analysisConfiguration.setDelay(delay)
        }
    }

    private fun deleteDebugFrame() {
        if (BuildConfig.DEBUG) {
            cycleLensApplication.captureDebugSnapshot.delete()
        }
    }

    private val cycleLensApplication: CycleLensApplication
        get() = application as CycleLensApplication

    private companion object {
        const val TAG = "CycleLensActivity"
    }
}
