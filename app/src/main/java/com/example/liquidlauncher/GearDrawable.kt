package com.example.liquidlauncher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Engrenagem toda de vidro: miolo translúcido, reflexo no topo e borda de luz. */
class GearDrawable : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()

    override fun onBoundsChange(b: Rect) {
        super.onBoundsChange(b)
        buildPath(b)
    }

    private fun buildPath(b: Rect) {
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val r = min(b.width(), b.height()) / 2f * 0.94f
        val inner = r * 0.78f
        val teeth = 8
        val step = 2.0 * PI / teeth

        fun pt(angle: Double, rad: Float, first: Boolean) {
            val x = cx + (rad * cos(angle)).toFloat()
            val y = cy + (rad * sin(angle)).toFloat()
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
        }

        for (i in 0 until teeth) {
            val c = i * step
            pt(c - 0.33 * step, inner, i == 0)
            pt(c - 0.20 * step, r, false)
            pt(c + 0.20 * step, r, false)
            pt(c + 0.33 * step, inner, false)
            pt(c + 0.5 * step, inner, false)
        }
        path.close()
        path.addCircle(cx, cy, r * 0.34f, Path.Direction.CCW)
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val top = b.top.toFloat()
        val h = b.height().toFloat()

        fill.shader = LinearGradient(
            0f, top, 0f, top + h,
            Color.argb(120, 255, 255, 255), Color.argb(46, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawPath(path, fill)

        c.save()
        c.clipPath(path)
        spec.shader = LinearGradient(
            0f, top, 0f, top + h * 0.5f,
            Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(b.left.toFloat(), top, b.right.toFloat(), top + h * 0.5f, spec)
        c.restore()

        rim.strokeWidth = max(1f, b.width() * 0.035f)
        rim.shader = LinearGradient(
            b.left.toFloat(), top, b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(
                Color.argb(240, 255, 255, 255), Color.argb(40, 255, 255, 255),
                Color.argb(40, 255, 255, 255), Color.argb(170, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(path, rim)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
