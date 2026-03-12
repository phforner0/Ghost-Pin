package com.ghostpin.app.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import com.ghostpin.core.model.JoystickState

/**
 * 360° virtual joystick for manual position control.
 *
 * Renders a circular track with a draggable thumb. Emits [JoystickState]
 * containing the angle (0–360°, 0 = north, clockwise) and magnitude (0.0–1.0)
 * as a [StateFlow] that [FloatingBubbleService] collects.
 *
 * When the user lifts their finger, the thumb returns to center and
 * magnitude resets to 0.0.
 *
 * In joystick mode, [com.ghostpin.app.service.SimulationService] uses:
 *   - angle → bearing
 *   - magnitude × profile.maxSpeedMs → current speed
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // Nested data class moved to com.ghostpin.core.model.JoystickState

    private val _state = MutableStateFlow(JoystickState())
    val state: StateFlow<JoystickState> = _state.asStateFlow()

    // ── Paints ────────────────────────────────────────────────────────────
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x401A1A2E.toInt()
        style = Paint.Style.FILL
    }
    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF80CBC4.toInt()
        style = Paint.Style.FILL
    }
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30FFFFFF.toInt()
        strokeWidth = 1f
    }

    // ── Geometry ──────────────────────────────────────────────────────────
    private var centerX = 0f
    private var centerY = 0f
    private var trackRadius = 0f
    private val thumbRadius = 28f

    private var thumbX = 0f
    private var thumbY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        trackRadius = min(w, h) / 2f - thumbRadius - 8f
        thumbX = centerX
        thumbY = centerY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = sqrt(dx * dx + dy * dy)
                val clampedDist = min(dist, trackRadius)

                if (dist > 0f) {
                    thumbX = centerX + dx / dist * clampedDist
                    thumbY = centerY + dy / dist * clampedDist
                }

                val magnitude = (clampedDist / trackRadius).coerceIn(0f, 1f)
                // atan2 gives angle from positive X-axis; convert to compass bearing
                // (0° = north = -Y axis, clockwise)
                val angleRad = atan2(dx, -dy)  // note: (dx, -dy) for north-up
                val angleDeg = ((Math.toDegrees(angleRad.toDouble()) + 360.0) % 360.0).toFloat()

                _state.value = JoystickState(angleDeg, magnitude)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                thumbX = centerX
                thumbY = centerY
                _state.value = JoystickState(0f, 0f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Track background
        canvas.drawCircle(centerX, centerY, trackRadius, trackPaint)
        canvas.drawCircle(centerX, centerY, trackRadius, trackBorderPaint)

        // Crosshairs
        canvas.drawLine(centerX, centerY - trackRadius, centerX, centerY + trackRadius, crosshairPaint)
        canvas.drawLine(centerX - trackRadius, centerY, centerX + trackRadius, centerY, crosshairPaint)

        // Thumb
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbBorderPaint)
    }

    companion object {
        /** Recommended size for the joystick view in dp. */
        const val RECOMMENDED_SIZE_DP = 180
    }
}
