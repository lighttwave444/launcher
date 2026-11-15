package com.example.liquidlauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs

/**
 * Barra de gestos por cima dos apps (para quando a barra do sistema some, ex.: DPI alterado).
 *
 * Normalmente ela fica ESCONDIDA: só sobra uma faixa finíssima e invisível no rodapé, que não
 * aparece nem atrapalha toques. Para ela sair é preciso PUXAR PARA CIMA a partir do rodapé.
 * Aí a barra aparece (acima do teclado, se ele estiver aberto) e fica uns segundos:
 *   - deslizar para cima: início | para o lado: recentes | toque: voltar
 * Depois de uns segundos sem uso, ela some de novo. Escondida dentro do próprio launcher.
 */
class NavService : AccessibilityService() {

    private var bar: View? = null
    private var wm: WindowManager? = null
    private var unit = 1f
    private var barVisible = true
    private var kbHeight = 0
    private var revealed = false
    private val handler = Handler(Looper.getMainLooper())

    private val hideRunnable = Runnable {
        revealed = false
        applyLayout()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        unit = minOf(dm.widthPixels, dm.heightPixels) / 360f
        showBar()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            updateKeyboard()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        updateKeyboard()

        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""
        val shouldShow: Boolean = when {
            // só a tela principal do launcher esconde a barra (a própria barra e os diálogos não contam)
            pkg == packageName -> if (cls == "$packageName.MainActivity") false else return
            // barra de status, teclado e afins não mudam nada
            pkg.startsWith("com.android.systemui") -> return
            pkg.contains("inputmethod") || pkg.contains("keyboard") -> return
            else -> true
        }
        if (shouldShow == barVisible) return
        barVisible = shouldShow
        bar?.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(hideRunnable)
        hideBar()
        super.onDestroy()
    }

    private fun hideBar() {
        try { bar?.let { wm?.removeView(it) } } catch (t: Throwable) { }
        bar = null
    }

    private fun scheduleHide(ms: Long = 3000L) {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, ms)
    }

    private fun cancelHide() {
        handler.removeCallbacks(hideRunnable)
    }

    /** Descobre se o teclado está aberto e qual a altura dele. */
    private fun updateKeyboard() {
        var kb = 0
        try {
            val screenH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                wm!!.currentWindowMetrics.bounds.height()
            else resources.displayMetrics.heightPixels
            for (w in windows) {
                if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    val r = Rect()
                    w.getBoundsInScreen(r)
                    kb = maxOf(kb, screenH - r.top)
                }
            }
        } catch (t: Throwable) { }
        if (kb == kbHeight) return
        kbHeight = kb
        applyLayout()
    }

    /** Escondida: faixa finíssima no rodapé. Aparecida: barra inteira (acima do teclado, se houver). */
    private fun applyLayout() {
        val v = bar ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val h = if (revealed) (30f * unit).toInt() else (8f * unit).toInt()
        val y = if (revealed && kbHeight > 0) kbHeight + (4f * unit).toInt() else 0
        if (lp.height != h || lp.y != y) {
            lp.height = h
            lp.y = y
            try { wm?.updateViewLayout(v, lp) } catch (t: Throwable) { }
        }
        v.invalidate()
    }

    private fun showBar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        hideBar()
        val service = this
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 215
            }
            private var downX = 0f
            private var downY = 0f

            override fun onDraw(c: Canvas) {
                // escondida: não desenha nada
                if (!revealed) return
                val pw = width * 0.62f
                val ph = 5f * unit
                val l = (width - pw) / 2f
                val t = height - ph - 6f * unit
                c.drawRoundRect(l, t, l + pw, t + ph, ph / 2f, ph / 2f, paint)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                val hidden = !revealed
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        if (!hidden) cancelHide()
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        val th = 24f * unit
                        if (hidden) {
                            // só um puxão para cima faz a barra sair; toque simples não faz nada
                            if (dy < -(12f * unit) && abs(dy) > abs(dx)) {
                                revealed = true
                                service.applyLayout()
                                scheduleHide()
                            }
                            return true
                        }
                        if (dy < -th && abs(dy) > abs(dx)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            revealed = false
                            service.applyLayout()
                            cancelHide()
                        } else if (abs(dx) > th * 1.5f && abs(dx) > abs(dy)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                            revealed = false
                            service.applyLayout()
                            cancelHide()
                        } else if (abs(dx) < th && abs(dy) < th) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                            scheduleHide()
                        } else {
                            scheduleHide()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> if (!hidden) scheduleHide()
                }
                return true
            }
        }

        val lp = WindowManager.LayoutParams(
            (200f * unit).toInt(),
            (8f * unit).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        wm?.addView(view, lp)
        bar = view
        barVisible = true
        updateKeyboard()
    }
}
