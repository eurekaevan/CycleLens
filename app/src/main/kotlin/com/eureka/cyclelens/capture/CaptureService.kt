package com.eureka.cyclelens.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.eureka.cyclelens.CycleLensApplication
import com.eureka.cyclelens.MainActivity
import com.eureka.cyclelens.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.FileOutputStream
import java.nio.ByteBuffer

class CaptureService : Service() {
    private lateinit var captureState: CaptureSessionStateStore
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var windowManager: WindowManager
    private lateinit var workerThread: HandlerThread
    private lateinit var workerHandler: Handler

    private val resourceCoordinator = CaptureResourceCoordinator()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var frameOutput: FrameOutput? = null
    private val retiredFrameOutputs = mutableSetOf<FrameOutput>()
    private var statsAccumulator: CaptureStatsAccumulator? = null
    private var activeProfile = CaptureProfile.NATIVE
    private var activeGeometry: CaptureGeometry? = null
    private var samplingGate: FrameSamplingGate? = null
    private var debugSnapshotNotBeforeNs: Long? = null
    private var publishedStatsCount = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            debugLog("projection stopped by system")
            releaseCapture(stopProjection = false, terminalState = TerminalState.IDLE)
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width <= 0 || height <= 0) {
                Log.w(TAG, "Ignoring invalid captured content size: ${width}x$height")
                return
            }
            resizeCapture(CaptureSize(width, height))
        }

        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            statsAccumulator?.setCapturedContentVisible(isVisible)
            debugLog("captured content visible=$isVisible")
        }
    }

    private val publishStats = object : Runnable {
        override fun run() {
            val stats = statsAccumulator?.snapshot() ?: return
            captureState.publish(stats)
            publishedStatsCount += 1
            if (publishedStatsCount % STATS_LOG_INTERVALS == 0L) {
                debugLog(
                    "stats ${stats.width}x${stats.height}, " +
                        "received=${stats.receivedFrames}, accepted=${stats.acceptedFrames}, " +
                        "incomingFps=${"%.1f".format(stats.incomingFps)}, " +
                        "acceptedFps=${"%.1f".format(stats.acceptedFps)}",
                )
            }
            workerHandler.postDelayed(this, STATS_PUBLISH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        captureState = (application as CycleLensApplication).captureSessionState
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        windowManager = getSystemService(WindowManager::class.java)
        workerThread = HandlerThread(WORKER_THREAD_NAME).apply { start() }
        workerHandler = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                startCaptureForeground()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.captureResultData()
                val profileName = intent.getStringExtra(EXTRA_CAPTURE_PROFILE)
                intent.removeExtra(EXTRA_RESULT_DATA)
                workerHandler.post {
                    beginCapture(resultCode, resultData, profileName)
                }
                START_NOT_STICKY
            }

            ACTION_STOP -> {
                workerHandler.post {
                    debugLog("capture stop requested")
                    releaseCapture(stopProjection = true, terminalState = TerminalState.IDLE)
                }
                START_NOT_STICKY
            }

            ACTION_SAVE_DEBUG_FRAME -> {
                workerHandler.post(::armDebugSnapshot)
                START_NOT_STICKY
            }

            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onDestroy() {
        if (::workerHandler.isInitialized && workerThread.isAlive) {
            val cleanupFinished = CountDownLatch(1)
            workerHandler.post {
                try {
                    releaseCapture(
                        stopProjection = true,
                        terminalState = TerminalState.IDLE,
                        stopService = false,
                    )
                    when (captureState.state.value) {
                        CaptureState.Starting,
                        is CaptureState.Running,
                        -> captureState.stop()

                        else -> Unit
                    }
                } finally {
                    cleanupFinished.countDown()
                }
            }
            if (!cleanupFinished.await(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out while waiting for capture cleanup")
            }
            workerHandler.removeCallbacksAndMessages(null)
            workerThread.quitSafely()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun beginCapture(resultCode: Int, resultData: Intent?, profileName: String?) {
        when (captureState.state.value) {
            is CaptureState.Running -> {
                debugLog("ignoring duplicate capture start")
                return
            }

            CaptureState.Starting -> Unit
            else -> {
                debugLog("ignoring capture start outside Starting state")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
        }
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            failCapture("Screen capture authorization was unavailable", null)
            return
        }

        activeProfile = CaptureProfile.entries.firstOrNull { it.name == profileName }
            ?: CaptureProfile.NATIVE
        val sourceSize = initialCaptureSize()
        val initialGeometry = activeProfile.geometryFor(sourceSize.width, sourceSize.height)
        val initialSize = CaptureSize(initialGeometry.outputWidth, initialGeometry.outputHeight)
        if (!resourceCoordinator.begin(initialSize)) {
            debugLog("ignoring duplicate capture start")
            return
        }

        try {
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjectionManager did not create a projection")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, workerHandler)

            val output = createFrameOutput(initialSize)
            frameOutput = output
            val display = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                initialSize.width,
                initialSize.height,
                resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                output.surface,
                null,
                workerHandler,
            ) ?: error("MediaProjection did not create a virtual display")
            virtualDisplay = display

            activeGeometry = initialGeometry
            samplingGate = FrameSamplingGate(activeProfile.targetAnalysisFps)
            val accumulator = CaptureStatsAccumulator(
                initialGeometry = initialGeometry,
                profile = activeProfile,
                surfaceFrameRateHintRequested = SHOULD_REQUEST_SURFACE_FRAME_RATE_HINT,
                surfaceFrameRateHintApplied = output.frameRateHintApplied,
            )
            statsAccumulator = accumulator
            publishedStatsCount = 0L
            val initialStats = accumulator.snapshot()
            captureState.start(initialStats)
            workerHandler.removeCallbacks(publishStats)
            workerHandler.postDelayed(publishStats, STATS_PUBLISH_INTERVAL_MS)
            debugLog(
                "capture started profile=$activeProfile source=" +
                    "${sourceSize.width}x${sourceSize.height} output=" +
                    "${initialSize.width}x${initialSize.height} " +
                    "density=${resources.configuration.densityDpi}",
            )
        } catch (error: RuntimeException) {
            failCapture("Screen capture could not start", error)
        }
    }

    private fun createFrameOutput(size: CaptureSize): FrameOutput {
        val reader = ImageReader.newInstance(
            size.width,
            size.height,
            PixelFormat.RGBA_8888,
            MAX_IMAGES,
        )
        val surface = reader.surface
        val hintApplied = requestSurfaceFrameRateHint(surface)
        val output = FrameOutput(reader, surface, hintApplied)
        reader.setOnImageAvailableListener(
            { availableReader -> onImageAvailable(availableReader) },
            workerHandler,
        )
        return output
    }

    private fun onImageAvailable(reader: ImageReader) {
        if (frameOutput?.reader !== reader) {
            return
        }

        val image = try {
            reader.acquireLatestImage()
        } catch (error: IllegalStateException) {
            Log.e(TAG, "Unable to acquire the latest capture frame", error)
            failCapture("Screen capture frame acquisition failed", error)
            return
        } ?: return

        var metadataError: RuntimeException? = null
        try {
            val firstPlane = image.planes.firstOrNull()
            val metadata = FrameMetadata(
                width = image.width,
                height = image.height,
                timestampNs = image.timestamp,
                planeCount = image.planes.size,
                rowStride = firstPlane?.rowStride,
                pixelStride = firstPlane?.pixelStride,
            )
            val decision = samplingGate?.decide(image.timestamp) ?: FrameSamplingDecision.DROP
            statsAccumulator?.onFrame(metadata, accepted = decision == FrameSamplingDecision.ACCEPT)
            maybeSaveDebugSnapshot(image)
            if (decision == FrameSamplingDecision.ACCEPT) {
                activeGeometry?.let { geometry ->
                    onAcceptedAnalysisFrame(
                        AnalysisFrameDescriptor(
                            timestampNs = image.timestamp,
                            geometry = geometry,
                            arenaRect = ClashRoyaleCaptureLayout.arenaRegion.toPixelRect(geometry),
                        ),
                    )
                }
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to read capture frame metadata", error)
            metadataError = error
        } finally {
            image.close()
        }
        metadataError?.let { error ->
            failCapture("Screen capture frame metadata failed", error)
        }
    }

    private fun resizeCapture(nextSize: CaptureSize) {
        val nextGeometry = activeProfile.geometryFor(nextSize.width, nextSize.height)
        val nextOutputSize = CaptureSize(nextGeometry.outputWidth, nextGeometry.outputHeight)
        when (val decision = resourceCoordinator.resize(nextOutputSize)) {
            ResizeDecision.Ignore -> return
            ResizeDecision.Unchanged -> {
                activeGeometry = nextGeometry
                statsAccumulator?.resize(nextGeometry)
                return
            }

            is ResizeDecision.Replace -> {
                val display = virtualDisplay ?: return
                val oldOutput = frameOutput ?: return
                var newOutput: FrameOutput? = null
                try {
                    newOutput = createFrameOutput(decision.next)
                    display.resize(
                        decision.next.width,
                        decision.next.height,
                        resources.configuration.densityDpi,
                    )
                    display.surface = newOutput.surface
                    frameOutput = newOutput
                    activeGeometry = nextGeometry
                    statsAccumulator?.resize(nextGeometry)
                    retireFrameOutput(oldOutput)
                    debugLog(
                        "capture resized source=${nextSize.width}x${nextSize.height}, " +
                            "output ${decision.previous.width}x${decision.previous.height} " +
                            "to ${decision.next.width}x${decision.next.height}",
                    )
                } catch (error: RuntimeException) {
                    if (newOutput !== frameOutput) {
                        newOutput?.close()
                    }
                    failCapture("Screen capture could not resize", error)
                }
            }
        }
    }

    private fun releaseCapture(
        stopProjection: Boolean,
        terminalState: TerminalState,
        stopService: Boolean = true,
    ) {
        if (!resourceCoordinator.stop()) {
            if (stopService) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return
        }

        workerHandler.removeCallbacks(publishStats)
        virtualDisplay?.release()
        virtualDisplay = null
        frameOutput?.close()
        frameOutput = null
        retiredFrameOutputs.forEach(FrameOutput::close)
        retiredFrameOutputs.clear()
        statsAccumulator = null
        activeGeometry = null
        samplingGate?.reset()
        samplingGate = null
        debugSnapshotNotBeforeNs = null

        val projection = mediaProjection
        mediaProjection = null
        if (projection != null) {
            projection.unregisterCallback(projectionCallback)
            if (stopProjection) {
                projection.stop()
            }
        }

        when (terminalState) {
            TerminalState.IDLE -> captureState.stop()
            TerminalState.ERROR -> Unit
        }
        debugLog("capture resources released")
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun failCapture(message: String, cause: Throwable?) {
        if (cause == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, cause)
        }
        releaseCapture(
            stopProjection = true,
            terminalState = TerminalState.ERROR,
            stopService = false,
        )
        captureState.fail(message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun initialCaptureSize(): CaptureSize {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            null
        }
        return CaptureSize(
            width = bounds?.width() ?: resources.displayMetrics.widthPixels,
            height = bounds?.height() ?: resources.displayMetrics.heightPixels,
        )
    }

    private fun requestSurfaceFrameRateHint(surface: Surface): Boolean {
        if (!SHOULD_REQUEST_SURFACE_FRAME_RATE_HINT || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false
        }
        return try {
            surface.setFrameRate(
                SURFACE_FRAME_RATE_HINT_FPS,
                Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
            )
            true
        } catch (error: Exception) {
            Log.w(TAG, "Surface frame-rate hint was rejected", error)
            false
        }
    }

    private fun armDebugSnapshot() {
        if (!isDebuggable() || captureState.state.value !is CaptureState.Running) {
            return
        }
        debugSnapshotNotBeforeNs = SystemClock.elapsedRealtimeNanos() + DEBUG_SNAPSHOT_DELAY_NS
        (application as CycleLensApplication).captureDebugSnapshot.markPending()
        debugLog("debug snapshot armed")
    }

    private fun maybeSaveDebugSnapshot(image: android.media.Image) {
        val notBeforeNs = debugSnapshotNotBeforeNs ?: return
        if (SystemClock.elapsedRealtimeNanos() < notBeforeNs) {
            return
        }
        debugSnapshotNotBeforeNs = null
        val snapshotStore = (application as CycleLensApplication).captureDebugSnapshot
        try {
            val plane = image.planes.singleOrNull()
                ?: error("Debug snapshot requires exactly one RGBA plane")
            val packed = RgbaRowCopy.tightlyPacked(
                source = plane.buffer,
                width = image.width,
                height = image.height,
                pixelStride = plane.pixelStride,
                rowStride = plane.rowStride,
            )
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            try {
                bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(packed))
                FileOutputStream(snapshotStore.file()).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG encoder rejected the debug frame"
                    }
                }
            } finally {
                bitmap.recycle()
            }
            snapshotStore.saved()
            debugLog("debug snapshot saved ${snapshotStore.file().absolutePath}")
        } catch (error: Exception) {
            Log.e(TAG, "Unable to save debug snapshot", error)
            snapshotStore.delete()
            snapshotStore.failed("Debug frame could not be saved")
        }
    }

    private fun onAcceptedAnalysisFrame(descriptor: AnalysisFrameDescriptor) {
        // Stage 6C boundary only. No Image or pixel buffer leaves the callback.
        check(descriptor.timestampNs >= 0L)
    }

    private fun isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun retireFrameOutput(output: FrameOutput) {
        output.stopListening()
        retiredFrameOutputs += output
        workerHandler.postDelayed(
            {
                if (retiredFrameOutputs.remove(output)) {
                    output.close()
                }
            },
            OUTPUT_RETIRE_DELAY_MS,
        )
    }

    private fun startCaptureForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.capture_notification_text)
            },
        )

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cyclelens_notification)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop_capture),
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun debugLog(message: String) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, message)
        }
    }

    private fun Intent.captureResultData(): Intent? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
        getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(EXTRA_RESULT_DATA)
    }

    private class FrameOutput(
        val reader: ImageReader,
        val surface: Surface,
        val frameRateHintApplied: Boolean,
    ) {
        private var closed = false

        fun stopListening() {
            if (!closed) {
                reader.setOnImageAvailableListener(null, null)
            }
        }

        fun close() {
            if (closed) {
                return
            }
            closed = true
            reader.setOnImageAvailableListener(null, null)
            reader.close()
            surface.release()
        }
    }

    private enum class TerminalState {
        IDLE,
        ERROR,
    }

    companion object {
        private const val TAG = "CycleLensCapture"
        private const val ACTION_START = "com.eureka.cyclelens.capture.START"
        private const val ACTION_STOP = "com.eureka.cyclelens.capture.STOP"
        private const val ACTION_SAVE_DEBUG_FRAME =
            "com.eureka.cyclelens.capture.SAVE_DEBUG_FRAME"
        private const val EXTRA_RESULT_CODE = "capture_result_code"
        private const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val EXTRA_CAPTURE_PROFILE = "capture_profile"
        private const val NOTIFICATION_CHANNEL_ID = "cyclelens_screen_capture"
        private const val NOTIFICATION_ID = 1002
        private const val VIRTUAL_DISPLAY_NAME = "CycleLensCapture"
        private const val WORKER_THREAD_NAME = "CycleLensCaptureFrames"
        private const val MAX_IMAGES = 2
        private const val STATS_PUBLISH_INTERVAL_MS = 1_000L
        private const val STATS_LOG_INTERVALS = 5L
        private const val OUTPUT_RETIRE_DELAY_MS = 250L
        private const val CLEANUP_TIMEOUT_SECONDS = 2L
        private const val DEBUG_SNAPSHOT_DELAY_NS = 2_000_000_000L
        private const val SURFACE_FRAME_RATE_HINT_FPS = 30f
        private const val SHOULD_REQUEST_SURFACE_FRAME_RATE_HINT = true

        fun start(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            profile: CaptureProfile,
        ) {
            context.startForegroundService(
                Intent(context, CaptureService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, resultData)
                    .putExtra(EXTRA_CAPTURE_PROFILE, profile.name),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_STOP),
            )
        }


        fun saveDebugFrame(context: Context) {
            context.startService(
                Intent(context, CaptureService::class.java).setAction(ACTION_SAVE_DEBUG_FRAME),
            )
        }
    }
}
