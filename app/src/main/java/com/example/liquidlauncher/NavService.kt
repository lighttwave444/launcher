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
 * Sem teclado: um traço fininho e apagado embaixo; ao encostar ela acorda e fica visível,
 * e volta a dormir depois de uns segundos.
 *   - deslizar para cima: início | para o lado: recentes | toque (já acordada): voltar
 *
 * Com teclado aberto: a barra SOME (só sobra uma faixa finíssima invisível no rodapé, longe das
 * teclas). Deslizando para cima a partir do rodapé, ela reaparece acima do teclado por uns
 * segundos e depois some de novo. Fica escondida dentro do próprio launcher.
 */
class NavService : AccessibilityService() {

    private var bar: View? = null
    private var wm: WindowManager? = null
    private var unit = 1f
    private var awake = false
    private var barVisible = true
    private var kbHeight = 0
    private var revealed = false
    private val handler = Handler(Looper.getMainLooper())

    private val sleepRunnable = Runnable {
        awake = false
        if (kbHeight > 0) {
            revealed = false
            applyLayout()
        }
        bar?.invalidate()
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
            // barra de status, teclado e afins não mudam nada (era isso que fazia piscar)
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
        handler.removeCallbacks(sleepRunnable)
        hideBar()
        super.onDestroy()
    }

    private fun hideBar() {
        try { bar?.let { wm?.removeView(it) } } catch (t: Throwable) { }
        bar = null
    }

    private fun wake() {
        handler.removeCallbacks(sleepRunnable)
        awake = true
        bar?.invalidate()
    }

    private fun scheduleSleep() {
        handler.removeCallbacks(sleepRunnable)
        handler.postDelayed(sleepRunnable, if (kbHeight > 0) 3000L else 2500L)
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
        if (kb <= 0) revealed = false
        applyLayout()
    }

    /** Ajusta tamanho e posição da janela da barra conforme o estado. */
    private fun applyLayout() {
        val v = bar ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        val full = (30f * unit).toInt()
        val thin = (8f * unit).toInt()
        val h: Int
        val y: Int
        if (kbHeight <= 0) {
            h = full; y = 0
        } else if (revealed) {
            h = full; y = kbHeight + (4f * unit).toInt()
        } else {
            h = thin; y = 0
        }
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
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            private var downX = 0f
            private var downY = 0f
            private var wasAwake = false

            override fun onDraw(c: Canvas) {
                // com o teclado aberto e a barra escondida, não desenha nada
                if (kbHeight > 0 && !revealed) return
                val pw = width * (if (awake) 0.62f else 0.40f)
                val ph = (if (awake) 5f else 3f) * unit
                paint.alpha = if (awake) 215 else 70
                val l = (width - pw) / 2f
                val t = height - ph - 6f * unit
                c.drawRoundRect(l, t, l + pw, t + ph, ph / 2f, ph / 2f, paint)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                val hiddenByKeyboard = kbHeight > 0 && !revealed
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX
                        downY = ev.rawY
                        wasAwake = awake
                        if (!hiddenByKeyboard) wake()
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        val th = 24f * unit
                        if (hiddenByKeyboard) {
                            // só um deslize para cima, a partir do rodapé, traz a barra de volta
                            if (dy < -(th / 2f) && abs(dy) > abs(dx)) {
                                revealed = true
                                awake = true
                                service.applyLayout()
                                scheduleSleep()
                            }
                            return true
                        }
                        if (dy < -th && abs(dy) > abs(dx)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        } else if (abs(dx) > th * 1.5f && abs(dx) > abs(dy)) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        } else if (abs(dx) < th && abs(dy) < th && wasAwake) {
                            // toque só vale se a barra já estava acordada (evita toque sem querer)
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        }
                        scheduleSleep()
                    }
                    MotionEvent.ACTION_CANCEL -> if (!hiddenByKeyboard) scheduleSleep()
                }
                return true
            }
        }

        val lp = WindowManager.LayoutParams(
            (180f * unit).toInt(),
            (30f * unit).toInt(),
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
