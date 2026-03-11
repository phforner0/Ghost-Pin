package com.ghostpin.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.WindowManager
import com.ghostpin.app.data.SimulationRepository
import com.ghostpin.app.ui.overlay.FloatingBubbleView
import com.ghostpin.app.ui.overlay.JoystickView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * Manages the floating overlay bubble and optional joystick for
 * real-time simulation control from outside the app.
 *
 * Lifecycle:
 *  - Started via [ACTION_SHOW], which adds the bubble to the WindowManager.
 *  - Stopped via [ACTION_HIDE] or when the service is destroyed.
 *  - Uses START_NOT_STICKY so the overlay doesn't linger after a crash.
 *
 * Requires `SYSTEM_ALERT_WINDOW` permission to function.
 */
@AndroidEntryPoint
class FloatingBubbleService : Service() {

    companion object {
        const val ACTION_SHOW = "com.ghostpin.action.SHOW_BUBBLE"
        const val ACTION_HIDE = "com.ghostpin.action.HIDE_BUBBLE"
        const val ACTION_TOGGLE_JOYSTICK = "com.ghostpin.action.TOGGLE_JOYSTICK"

        fun showIntent(context: Context): Intent =
            Intent(context, FloatingBubbleService::class.java).setAction(ACTION_SHOW)

        fun hideIntent(context: Context): Intent =
            Intent(context, FloatingBubbleService::class.java).setAction(ACTION_HIDE)
    }

    @Inject lateinit var simulationRepository: SimulationRepository

    private var windowManager: WindowManager? = null
    private var bubbleView: FloatingBubbleView? = null
    private var joystickView: JoystickView? = null
    private var isJoystickVisible = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> {
                removeBubble()
                stopSelf()
            }
            ACTION_TOGGLE_JOYSTICK -> toggleJoystick()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeBubble()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Bubble management ─────────────────────────────────────────────────

    private fun showBubble() {
        if (bubbleView != null) return // already showing
        if (!Settings.canDrawOverlays(this)) return // no permission

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val bubble = FloatingBubbleView(
            context = this,
            onPlay = { startSimulation() },
            onPause = { pauseSimulation() },
            onStop = { stopSimulation() },
        )
        bubbleView = bubble
        wm.addView(bubble, bubble.windowParams)

        // Observe simulation state → sync bubble indicator
        serviceScope.launch {
            simulationRepository.state.collectLatest { state ->
                bubble.bubbleState = when (state) {
                    is SimulationState.Running -> FloatingBubbleView.BubbleState.RUNNING
                    is SimulationState.Paused  -> FloatingBubbleView.BubbleState.PAUSED
                    else                       -> FloatingBubbleView.BubbleState.IDLE
                }
            }
        }
    }

    private fun removeBubble() {
        val wm = windowManager ?: return
        bubbleView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        removeJoystick()
    }

    // ── Joystick management ───────────────────────────────────────────────

    private fun toggleJoystick() {
        if (isJoystickVisible) removeJoystick() else showJoystick()
    }

    private fun showJoystick() {
        if (joystickView != null) return
        val wm = windowManager ?: return

        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            JoystickView.RECOMMENDED_SIZE_DP.toFloat(),
            resources.displayMetrics,
        ).toInt()

        val joy = JoystickView(this)
        joystickView = joy
        isJoystickVisible = true

        val params = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            x = 32
            y = 200
        }
        wm.addView(joy, params)
    }

    private fun removeJoystick() {
        val wm = windowManager ?: return
        joystickView?.let {
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        joystickView = null
        isJoystickVisible = false
    }

    // ── Simulation control ────────────────────────────────────────────────

    private fun startSimulation() {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun pauseSimulation() {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_PAUSE
        }
        startService(intent)
    }

    private fun stopSimulation() {
        val intent = Intent(this, SimulationService::class.java).apply {
            action = SimulationService.ACTION_STOP
        }
        startService(intent)
    }
}
