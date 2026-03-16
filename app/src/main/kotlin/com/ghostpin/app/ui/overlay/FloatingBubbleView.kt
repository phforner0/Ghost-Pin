package com.ghostpin.app.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FloatingBubbleView(
    context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onNextWaypoint: () -> Unit,
    private val onPreviousWaypoint: () -> Unit,
) : View(context) {

    enum class BubbleState { IDLE, RUNNING, PAUSED }

    var bubbleState: BubbleState = BubbleState.IDLE
        set(value) {
            field = value
            invalidate()
        }

    val windowParams: WindowManager.LayoutParams
        get() = WindowManager.LayoutParams(
            BUBBLE_SIZE,
            BUBBLE_SIZE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 400
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE01A1A2E.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF80CBC4.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
        style = Paint.Style.FILL
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = layoutParams as? WindowManager.LayoutParams ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (abs(dx) > 10 || abs(dy) > 10) isDragging = true

                params.x = initialX + dx.toInt()
                params.y = initialY + dy.toInt()
                wm.updateViewLayout(this, params)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    handleTap(event.x)
                } else {
                    snapToEdge(wm, params)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float) {
        val leftZone = width * 0.30f
        val rightZone = width * 0.70f

        if (x <= leftZone) {
            onPreviousWaypoint()
            return
        }
        if (x >= rightZone) {
            onNextWaypoint()
            return
        }

        when (bubbleState) {
            BubbleState.IDLE -> onPlay()
            BubbleState.RUNNING -> onPause()
            BubbleState.PAUSED -> onPlay()
        }
    }

    private fun snapToEdge(wm: WindowManager, params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val center = params.x + BUBBLE_SIZE / 2
        params.x = if (center < screenWidth / 2) 0 else screenWidth - BUBBLE_SIZE
        wm.updateViewLayout(this, params)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val padding = 8f
        val cornerRadius = 32f
        val r = width / 2f

        rect.set(padding, padding, width - padding, height - padding)
        bgPaint.setShadowLayer(12f, 0f, 4f, 0x66000000)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        bgPaint.clearShadowLayer()

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        statusPaint.color = when (bubbleState) {
            BubbleState.RUNNING -> 0xFF00E676.toInt()
            BubbleState.PAUSED -> 0xFFFFEA00.toInt()
            BubbleState.IDLE -> 0xFF757575.toInt()
        }
        statusPaint.setShadowLayer(6f, 0f, 0f, statusPaint.color)
        canvas.drawCircle(width - padding - 16f, padding + 16f, 10f, statusPaint)
        statusPaint.clearShadowLayer()

        when (bubbleState) {
            BubbleState.RUNNING -> drawPauseIcon(canvas, cx, cy, r)
            else -> drawPlayIcon(canvas, cx, cy, r)
        }

        iconPaint.textAlign = Paint.Align.CENTER
        iconPaint.textSize = r * 0.34f
        canvas.drawText("‹", width * 0.23f, cy + (r * 0.12f), iconPaint)
        canvas.drawText("›", width * 0.77f, cy + (r * 0.12f), iconPaint)
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val size = r * 0.40f
        val path = android.graphics.Path().apply {
            moveTo(cx - size * 0.3f, cy - size)
            lineTo(cx + size * 0.8f, cy)
            lineTo(cx - size * 0.3f, cy + size)
            close()
        }
        canvas.drawPath(path, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val barW = r * 0.12f
        val barH = r * 0.45f
        val gap = r * 0.15f
        rect.set(cx - gap - barW, cy - barH, cx - gap, cy + barH)
        canvas.drawRoundRect(rect, 6f, 6f, iconPaint)
        rect.set(cx + gap, cy - barH, cx + gap + barW, cy + barH)
        canvas.drawRoundRect(rect, 6f, 6f, iconPaint)
    }

    companion object {
        const val BUBBLE_SIZE = 140
    }
}
