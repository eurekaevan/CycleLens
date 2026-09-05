package com.eureka.cyclelens.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import com.eureka.cyclelens.CycleLensApplication
import com.eureka.cyclelens.MainActivity
import com.eureka.cyclelens.R
import com.eureka.cyclelens.session.MatchSession
import com.eureka.cyclelens.session.MatchSnapshot
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var matchSession: MatchSession
    private lateinit var overlayQuickCards: OverlayQuickCards
    private lateinit var overlayConfiguration: OverlayConfiguration
    private lateinit var presentationController: OverlayPresentationController
    private lateinit var windowManager: WindowManager
    private lateinit var viewFactory: OverlayViewFactory
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayRoot: FrameLayout? = null
    private var latestSnapshot: MatchSnapshot? = null
    private var latestQuickCardIds = emptyList<cyclelens.core.CardId>()
    private var latestAppearance = OverlayAppearance()

    override fun onCreate() {
        super.onCreate()
        val cycleLensApplication = application as CycleLensApplication
        matchSession = cycleLensApplication.matchSession
        overlayQuickCards = cycleLensApplication.overlayQuickCards
        overlayConfiguration = cycleLensApplication.overlayConfiguration
        presentationController = OverlayPresentationController(
            OverlayPresentationMapper(cycleLensApplication.cardCatalog),
        )
        windowManager = getSystemService(WindowManager::class.java)
        viewFactory = OverlayViewFactory(this, cycleLensApplication.cardArtworkRepository)
        latestSnapshot = matchSession.snapshot.value
        latestQuickCardIds = overlayQuickCards.cardIds.value
        latestAppearance = overlayConfiguration.appearance.value

        serviceScope.launch {
            matchSession.snapshot.collect { snapshot ->
                latestSnapshot = snapshot
                renderOverlay()
            }
        }
        serviceScope.launch {
            overlayQuickCards.cardIds.collect { cardIds ->
                latestQuickCardIds = cardIds
                renderOverlay()
            }
        }
        serviceScope.launch {
            overlayConfiguration.appearance.collect { appearance ->
                latestAppearance = appearance
                renderOverlay()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                debugLog("overlay stop")
                stopOverlayAndSelf()
                START_NOT_STICKY
            }

            ACTION_START -> {
                debugLog("overlay start")
                startForeground(NOTIFICATION_ID, createNotification())
                if (Settings.canDrawOverlays(this)) {
                    ensureOverlayWindow()
                } else {
                    Log.w(TAG, "Overlay permission is not granted; stopping service")
                    stopOverlayAndSelf()
                }
                START_NOT_STICKY
            }

            else -> {
                stopSelf(startId)
                START_NOT_STICKY
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayRoot?.post(::clampOverlayToScreen)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlayWindow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureOverlayWindow() {
        if (overlayRoot != null) {
            renderOverlay()
            return
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(96)
        }

        val root = FrameLayout(this).apply {
            elevation = dp(8).toFloat()
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                post(::clampOverlayToScreen)
            }
        }

        try {
            windowManager.addView(root, layoutParams)
            overlayRoot = root
            renderOverlay()
        } catch (error: SecurityException) {
            Log.e(TAG, "Unable to add overlay window", error)
            stopOverlayAndSelf()
        }
    }

    private fun renderOverlay() {
        val root = overlayRoot ?: return
        val snapshot = latestSnapshot ?: return
        val state = presentationController.stateFor(
            matchSnapshot = snapshot,
            quickCardIds = latestQuickCardIds,
            appearance = latestAppearance,
        )
        val content = viewFactory.create(
            state = state,
            callbacks = OverlayViewFactory.Callbacks(
                onCardClick = { cardId ->
                    if (matchSession.observe(cardId)) {
                        debugLog("observe ${cardId.value}")
                    }
                },
                onAddClick = {
                    if (
                        presentationController.openPicker(
                            matchSnapshot = snapshot,
                            quickCardIds = latestQuickCardIds,
                            appearance = latestAppearance,
                        )
                    ) {
                        renderOverlay()
                    }
                },
                onPickerCardClick = { cardId ->
                    presentationController.expand()
                    if (matchSession.observe(cardId)) {
                        debugLog("observe ${cardId.value}")
                    }
                },
                onUndoClick = {
                    matchSession.undo()?.let { cardId ->
                        debugLog("undo ${cardId.value}")
                    }
                },
                onCollapseClick = {
                    presentationController.collapse()
                    renderOverlay()
                },
                onExpandClick = {
                    presentationController.expand()
                    renderOverlay()
                },
                onClosePickerClick = {
                    presentationController.expand()
                    renderOverlay()
                },
            ),
        )

        root.removeAllViews()
        root.addView(content.view)
        installDragGesture(content.dragHandle, content.onDragHandleTap)
        root.requestLayout()
        root.post(::clampOverlayToScreen)
    }

    private fun installDragGesture(
        dragHandle: View,
        onTap: (() -> Unit)?,
    ) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        if (onTap != null) {
            dragHandle.setOnClickListener { onTap() }
        }
        dragHandle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && hypot(deltaX.toDouble(), deltaY.toDouble()) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        moveOverlayTo(
                            requestedX = startX + deltaX.roundToInt(),
                            requestedY = startY + deltaY.roundToInt(),
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        view.performClick()
                    } else {
                        clampOverlayToScreen()
                        debugLog("overlay position ${layoutParams.x},${layoutParams.y}")
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun moveOverlayTo(requestedX: Int, requestedY: Int) {
        val root = overlayRoot ?: return
        val bounds = screenBounds()
        val maxX = (bounds.width() - root.width).coerceAtLeast(0)
        val maxY = (bounds.height() - root.height).coerceAtLeast(0)
        val clampedX = requestedX.coerceIn(0, maxX)
        val clampedY = requestedY.coerceIn(0, maxY)
        if (layoutParams.x == clampedX && layoutParams.y == clampedY) {
            return
        }
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        windowManager.updateViewLayout(root, layoutParams)
    }

    private fun clampOverlayToScreen() {
        moveOverlayTo(layoutParams.x, layoutParams.y)
    }

    private fun screenBounds(): Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds
    } else {
        Rect(
            0,
            0,
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
        )
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.overlay_notification_text)
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
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cyclelens_notification)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openAppIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.stop_overlay),
                    stopIntent,
                ).build(),
            )
            .build()
    }

    private fun stopOverlayAndSelf() {
        removeOverlayWindow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlayWindow() {
        val root = overlayRoot ?: return
        overlayRoot = null
        windowManager.removeView(root)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun debugLog(message: String) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "CycleLensOverlay"
        private const val ACTION_START = "com.eureka.cyclelens.overlay.START"
        private const val ACTION_STOP = "com.eureka.cyclelens.overlay.STOP"
        private const val NOTIFICATION_CHANNEL_ID = "cyclelens_overlay"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java).setAction(ACTION_START),
            )
        }
    }
}
