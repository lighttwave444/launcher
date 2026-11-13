package com.example.liquidlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Vidro líquido: mostra um pedaço ampliado (lente) do papel de parede atrás da peça,
 * clareia o miolo, põe brilho especular no topo e uma borda de luz que vai de forte a fraca.
 */
class GlassDrawable(
    private val view: View,
    private val root: View,
    private val lens: Bitmap?,
    private val radius: Float
) : Drawable() {

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val rect = RectF()
    private val m = Matrix()

    private fun white(a: Int): Int = (a shl 24) or 0x00FFFFFF

    override fun draw(c: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val d = view.resources.displayMetrics.density

        c.save()
        c.translate(b.left.toFloat(), b.top.toFloat())
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)

        c.save()
        c.clipPath(path)

        // lente: pedaço do papel de parede borrado, ampliado 10% (efeito de refração)
        if (lens != null && root.width > 0 && root.height > 0) {
            var x = 0f
            var y = 0f
            var cur: View = view
            while (cur !== root) {
                x += cur.left
                y += cur.top
                val p = cur.parent
                if (p !is View) break
                cur = p
            }
            m.reset()
            m.postScale(root.width.toFloat() / lens.width, root.height.toFloat() / lens.height)
            m.postTranslate(-x, -y)
            m.postScale(1.10f, 1.10f, w / 2f, h / 2f)
            c.drawBitmap(lens, m, bmpPaint)
        }

        // miolo claro e transparente (mais claro nas pontas, limpo no meio)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(white(0x38), white(0x0A), white(0x22)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(rect, fillPaint)

        // brilho especular curvo no topo
        specPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            white(0x70), white(0x00),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h * 0.55f, specPaint)
        c.restore()

        // borda de luz: forte no canto de cima/esquerda e embaixo/direita, fraca no meio
        val sw = 1.6f * d
        rimPaint.strokeWidth = sw
        rimPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(white(0xF0), white(0x18), white(0x18), white(0x99)),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        rect.inset(sw / 2f, sw / 2f)
        c.drawRoundRect(rect, radius - sw / 2f, radius - sw / 2f, rimPaint)

        c.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
