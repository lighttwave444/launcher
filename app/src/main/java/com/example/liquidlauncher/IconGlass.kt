package com.example.liquidlauncher

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * O próprio ícone vira uma peça de vidro escuro: o desenho inteiro continua com as mesmas
 * cores, mas ganha película fumê (escurece), reflexo curvo no topo e borda de luz.
 */
object IconGlass {
    private const val S = 200

    fun make(ctx: Context, icon: Drawable): Drawable {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = S.toFloat()
        val r = s * 0.225f
        val rect = RectF(0f, 0f, s, s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)

        // base escura (aparece só onde o ícone é transparente)
        p.shader = LinearGradient(
            0f, 0f, 0f, s,
            Color.argb(235, 40, 48, 66), Color.argb(235, 10, 13, 22),
            Shader.TileMode.CLAMP
        )
        c.drawRect(rect, p)
        p.shader = null

        // o ícone inteiro, com as cores mais vivas (mesmo matiz, só mais saturado e brilhante)
        val cm = ColorMatrix().apply { setSaturation(1.4f) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            1.12f, 0f, 0f, 0f, 6f,
            0f, 1.12f, 0f, 0f, 6f,
            0f, 0f, 1.12f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        )))
        val vivid = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        c.saveLayer(0f, 0f, s, s, vivid)
        drawWholeIcon(c, icon, s)
        c.restore()

        // película de vidro fumê por cima: escurece o ícone sem trocar a cor
        p.shader = LinearGradient(
            0f, 0f, 0f, s,
            Color.argb(22, 8, 12, 22), Color.argb(78, 8, 12, 22),
            Shader.TileMode.CLAMP
        )
        c.drawRect(rect, p)

        // reflexo de vidro no topo
        p.shader = LinearGradient(
            0f, 0f, 0f, s * 0.5f,
            Color.argb(85, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s * 0.5f, p)
        p.shader = null
        c.restore()

        // borda de luz
        val sw = s * 0.018f
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.shader = LinearGradient(
            0f, 0f, s, s,
            intArrayOf(
                Color.argb(225, 255, 255, 255), Color.argb(26, 255, 255, 255),
                Color.argb(26, 255, 255, 255), Color.argb(140, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        val rr = RectF(sw / 2f, sw / 2f, s - sw / 2f, s - sw / 2f)
        c.drawRoundRect(rr, r - sw / 2f, r - sw / 2f, p)

        return BitmapDrawable(ctx.resources, bmp)
    }

    private fun drawWholeIcon(c: Canvas, icon: Drawable, s: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            // as duas camadas (fundo + desenho) preenchem toda a peça
            val box = s * 1.5f
            val off = (s - box) / 2f
            val l = off.toInt()
            val rgt = (off + box).toInt()
            icon.background?.let { it.setBounds(l, l, rgt, rgt); it.draw(c) }
            icon.foreground?.let { it.setBounds(l, l, rgt, rgt); it.draw(c) }
        } else {
            val box = s * 0.9f
            val off = (s - box) / 2f
            val rad = box * 0.225f
            val path = Path().apply {
                addRoundRect(RectF(off, off, off + box, off + box), rad, rad, Path.Direction.CW)
            }
            c.save()
            c.clipPath(path)
            icon.setBounds(off.toInt(), off.toInt(), (off + box).toInt(), (off + box).toInt())
            icon.draw(c)
            c.restore()
        }
    }
}
