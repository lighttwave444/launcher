package com.example.liquidlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Estilo "Glassify": peça de vidro escuro em formato squircle (cantos macios),
 * com borda de luz fininha, e o desenho (logo) do app por cima, com as cores vivas.
 */
object IconGlass {
    private const val S = 220

    fun make(ctx: Context, icon: Drawable): Drawable {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = S.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tile = squircle(s)

        c.save()
        c.clipPath(tile)

        // vidro escuro: mais claro no canto de cima/esquerda, bem escuro embaixo/direita
        p.shader = LinearGradient(
            0f, 0f, s, s,
            Color.argb(250, 46, 50, 60), Color.argb(250, 15, 17, 22),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // brilho suave no topo
        p.shader = LinearGradient(
            0f, 0f, 0f, s * 0.45f,
            Color.argb(26, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s * 0.45f, p)
        p.shader = null

        drawLogo(c, icon, s)
        c.restore()

        // borda de luz fininha
        val sw = s * 0.022f
        val inner = Path()
        val m = Matrix()
        val k = (s - sw) / s
        m.setScale(k, k, s / 2f, s / 2f)
        tile.transform(m, inner)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.shader = LinearGradient(
            0f, 0f, s, s,
            intArrayOf(
                Color.argb(150, 255, 255, 255), Color.argb(18, 255, 255, 255),
                Color.argb(18, 255, 255, 255), Color.argb(95, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(inner, p)

        return BitmapDrawable(ctx.resources, bmp)
    }

    private fun drawLogo(c: Canvas, icon: Drawable, s: Float) {
        val fg: Drawable? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable)
                icon.foreground
            else null

        if (fg != null && glyphUsable(fg)) {
            // só o logo (sem o fundo do app), com cores bem vivas
            val cm = ColorMatrix().apply { setSaturation(1.35f) }
            val vivid = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            c.saveLayer(0f, 0f, s, s, vivid)
            val box = s * 1.2f
            val off = (s - box) / 2f
            fg.setBounds(off.toInt(), off.toInt(), (off + box).toInt(), (off + box).toInt())
            fg.draw(c)
            c.restore()
        } else {
            // ícone que já traz fundo próprio (ou logo escuro): vai inteiro, menor, dentro da peça
            drawWhole(c, icon, s, s * 0.70f)
        }
    }

    private fun drawWhole(c: Canvas, icon: Drawable, s: Float, size: Float) {
        val off = (s - size) / 2f
        val rad = size * 0.24f
        val path = Path().apply {
            addRoundRect(RectF(off, off, off + size, off + size), rad, rad, Path.Direction.CW)
        }
        c.save()
        c.clipPath(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            val box = size * 1.5f
            val o = (s - box) / 2f
            val l = o.toInt()
            val r = (o + box).toInt()
            icon.background?.let { it.setBounds(l, l, r, r); it.draw(c) }
            icon.foreground?.let { it.setBounds(l, l, r, r); it.draw(c) }
        } else {
            icon.setBounds(off.toInt(), off.toInt(), (off + size).toInt(), (off + size).toInt())
            icon.draw(c)
        }
        c.restore()
    }

    /** O logo serve sobre vidro escuro? Falso se tiver fundo próprio, estiver vazio ou for escuro demais. */
    private fun glyphUsable(fg: Drawable): Boolean {
        val n = 108
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        fg.setBounds(0, 0, n, n)
        fg.draw(cv)

        var opaqueCorners = 0
        val pts = listOf(20 to 20, 87 to 20, 20 to 87, 87 to 87)
        for ((x, y) in pts) {
            if (Color.alpha(bm.getPixel(x, y)) > 200) opaqueCorners++
        }
        if (opaqueCorners >= 3) { bm.recycle(); return false }

        var sum = 0.0
        var cnt = 0
        for (y in 18 until 90 step 2) {
            for (x in 18 until 90 step 2) {
                val px = bm.getPixel(x, y)
                if (Color.alpha(px) > 128) {
                    sum += (0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)) / 255.0
                    cnt++
                }
            }
        }
        bm.recycle()
        if (cnt < 20) return false
        return sum / cnt > 0.22
    }

    /** Superelipse (squircle): cantos macios, tipo "almofada". */
    private fun squircle(s: Float): Path {
        val path = Path()
        val a = s / 2.0
        val n = 4.6
        val steps = 180
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val ct = cos(t)
            val st = sin(t)
            val x = a + a * sign(ct) * abs(ct).pow(2.0 / n)
            val y = a + a * sign(st) * abs(st).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat())
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return path
    }
}
