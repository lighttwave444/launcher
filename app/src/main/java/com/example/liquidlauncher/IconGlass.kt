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
import android.graphics.RadialGradient
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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

    /** theme 0 = vidro escuro (logo colorido) | theme 1 = vidro claro (logo prateado, estilo Glassify Glass) */
    fun make(ctx: Context, icon: Drawable, theme: Int = 0): Drawable =
        if (theme == 1) makeGlass(ctx, icon) else makeDark(ctx, icon)

    private fun makeDark(ctx: Context, icon: Drawable): Drawable {
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

    // ---------------- TEMA 2: vidro claro translúcido, logo prateado ----------------

    private fun makeGlass(ctx: Context, icon: Drawable): Drawable {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = S.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tile = squircle(s)

        c.save()
        c.clipPath(tile)

        // vidro claro e translúcido (o fundo aparece através)
        p.shader = LinearGradient(
            0f, 0f, 0f, s,
            Color.argb(84, 255, 255, 255), Color.argb(30, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // sombra suave nas bordas, dá profundidade de vidro grosso
        p.shader = RadialGradient(
            s / 2f, s / 2f, s * 0.78f,
            intArrayOf(Color.argb(0, 6, 10, 30), Color.argb(0, 6, 10, 30), Color.argb(70, 6, 10, 30)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // reflexo no topo
        p.shader = LinearGradient(
            0f, 0f, 0f, s * 0.5f,
            Color.argb(80, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s * 0.5f, p)
        p.shader = null

        drawLogoMono(c, icon, s)
        c.restore()

        // borda de luz
        val sw = s * 0.024f
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
                Color.argb(215, 255, 255, 255), Color.argb(40, 255, 255, 255),
                Color.argb(40, 255, 255, 255), Color.argb(150, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(inner, p)

        return BitmapDrawable(ctx.resources, bmp)
    }

    /** Logo em tons de prata/cinza claro (como no Glassify Glass). */
    private fun drawLogoMono(c: Canvas, icon: Drawable, s: Float) {
        val silver = Color.argb(238, 224, 228, 235)
        // cinza claro que mantém os detalhes internos do desenho
        val gray = ColorMatrix().apply { setSaturation(0f) }
        gray.postConcat(ColorMatrix(floatArrayOf(
            0.6f, 0f, 0f, 0f, 100f,
            0f, 0.6f, 0f, 0f, 100f,
            0f, 0f, 0.6f, 0f, 100f,
            0f, 0f, 0f, 1f, 0f
        )))
        val grayPaint = Paint().apply { colorFilter = ColorMatrixColorFilter(gray) }

        val fg: Drawable? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable)
                icon.foreground
            else null
        val kind = if (fg != null) glyphKind(fg) else KIND_UNUSABLE

        if (fg != null && kind != KIND_UNUSABLE) {
            val box = s * 1.2f
            val off = (s - box) / 2f
            fg.setBounds(off.toInt(), off.toInt(), (off + box).toInt(), (off + box).toInt())
            if (kind == KIND_WHITE_GLYPH) {
                fg.colorFilter = PorterDuffColorFilter(silver, PorterDuff.Mode.SRC_IN)
                fg.draw(c)
                fg.colorFilter = null
            } else {
                c.saveLayer(0f, 0f, s, s, grayPaint)
                fg.draw(c)
                c.restore()
            }
        } else {
            c.saveLayer(0f, 0f, s, s, grayPaint)
            drawWhole(c, icon, s, s * 0.64f)
            c.restore()
        }
    }


    private fun drawLogo(c: Canvas, icon: Drawable, s: Float) {
        val fg: Drawable? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable)
                icon.foreground
            else null
        val kind = if (fg != null) glyphKind(fg) else KIND_UNUSABLE

        if (fg != null && kind != KIND_UNUSABLE) {
            val box = s * 1.2f
            val off = (s - box) / 2f
            fg.setBounds(off.toInt(), off.toInt(), (off + box).toInt(), (off + box).toInt())

            // logo branco: pinta com a cor original do app (a cor do fundo do ícone dele)
            var tint: Int? = null
            if (kind == KIND_WHITE_GLYPH) {
                val bg = (icon as AdaptiveIconDrawable).background
                if (bg != null) tint = brandColor(bg)
            }

            if (tint != null) {
                fg.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                fg.draw(c)
                fg.colorFilter = null
            } else {
                val cm = ColorMatrix().apply { setSaturation(1.35f) }
                val vivid = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
                c.saveLayer(0f, 0f, s, s, vivid)
                fg.draw(c)
                c.restore()
            }
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

    private const val KIND_UNUSABLE = 0
    private const val KIND_COLORED = 1
    private const val KIND_WHITE_GLYPH = 2

    /** Classifica o logo: sem uso (fundo próprio/vazio/escuro), colorido, ou branco (precisa ganhar a cor da marca). */
    private fun glyphKind(fg: Drawable): Int {
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
        if (opaqueCorners >= 3) { bm.recycle(); return KIND_UNUSABLE }

        var sum = 0.0
        var cnt = 0
        var whiteish = 0
        for (y in 18 until 90 step 2) {
            for (x in 18 until 90 step 2) {
                val px = bm.getPixel(x, y)
                if (Color.alpha(px) > 128) {
                    val r = Color.red(px)
                    val g = Color.green(px)
                    val b = Color.blue(px)
                    val lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    sum += lum
                    cnt++
                    val spread = maxOf(r, g, b) - minOf(r, g, b)
                    if (spread < 40 && lum > 0.75) whiteish++
                }
            }
        }
        bm.recycle()
        if (cnt < 20) return KIND_UNUSABLE
        if (whiteish.toDouble() / cnt > 0.8) return KIND_WHITE_GLYPH
        return if (sum / cnt > 0.22) KIND_COLORED else KIND_UNUSABLE
    }

    /** Cor da marca = cor média do fundo do ícone original; null se for cinza/branco/preto. */
    private fun brandColor(bg: Drawable): Int? {
        val n = 108
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        bg.setBounds(0, 0, n, n)
        bg.draw(cv)
        var r = 0
        var g = 0
        var b = 0
        var cnt = 0
        for (y in 36 until 72 step 3) {
            for (x in 36 until 72 step 3) {
                val px = bm.getPixel(x, y)
                if (Color.alpha(px) > 200) {
                    r += Color.red(px)
                    g += Color.green(px)
                    b += Color.blue(px)
                    cnt++
                }
            }
        }
        bm.recycle()
        if (cnt < 10) return null
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb(r / cnt, g / cnt, b / cnt), hsv)
        if (hsv[1] < 0.3f) return null            // fundo cinza/branco/preto: mantém o logo branco
        hsv[1] = minOf(1f, hsv[1] * 1.1f)
        hsv[2] = maxOf(hsv[2], 0.85f)             // bem vivo e visível sobre o vidro escuro
        return Color.HSVToColor(hsv)
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
