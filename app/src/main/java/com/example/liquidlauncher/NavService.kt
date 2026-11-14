package com.example.liquidlauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Barra de gestos por cima de todos os apps (para quando a barra do sistema some, ex.: DPI alterado).
 *  - toque: voltar
 *  - deslizar para cima: início
 *  - deslizar para o lado: apps recentes
 * Fica escondida dentro do próprio launcher.
 */
class NavService : AccessibilityService() {

    private var bar: View? = null
    private var wm: WindowManager? = null
    private var unit = 1f

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        unit = minOf(dm.widthPixels, dm.heightPixels) / 360f
        showBar()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        bar?.visibility = if (pkg == packageName) View.GONE else View.VISIBLE
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideBar()
        super.onDestroy()
    }

    private fun hideBar() {
        try { bar?.let { wm?.removeView(it) } } catch (t: Throwable) { }
        bar = null
    }

    private fun showBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        hideBar()
        val service = this
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
            }
            private var downX = 0f
            private var downY = 0f

            override fun onDraw(c: Canvas) {
                val pw = width * 0.62f
                val ph = 5f * unit
                val l = (width - pw) / 2f
                val t = height - ph - 8f * unit
                c.drawRoundRect(l, t, l + pw, t + ph, ph / 2f, ph / 2f, paint)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        val th = 24f * unit
                        if (dy < -th && abs(dy) > abs(dx)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        } else if (abs(dx) > th * 1.5f && abs(dx) > abs(dy)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        } else if (abs(dx) < th && abs(dy) < th) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        }
                    }
                }
                return true
            }
        }

        val lp = WindowManager.LayoutParams(
            (200f * unit).toInt(),
            (40f * unit).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        wm?.addView(view, lp)
        bar = view
    }
}
