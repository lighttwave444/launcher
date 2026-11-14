package com.example.liquidlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import android.graphics.Rect
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
    private const val S = 240

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

    // ---------------- TEMA 2: vidro claro "premium", logo branco sólido e padronizado ----------------

    private const val G = 480   // resolução interna para montar o logo (depois reduz: fica nítido)

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun makeGlass(ctx: Context, icon: Drawable): Drawable {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = S.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tile = squircle(s, 3.7)

        c.save()
        c.clipPath(tile)

        // corpo de vidro claro: luminoso no centro (pastilha convexa)
        p.shader = RadialGradient(
            s * 0.5f, s * 0.42f, s * 0.64f,
            Color.argb(104, 255, 255, 255), Color.argb(42, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // bordas um pouco mais fundas e azuladas (dá volume)
        p.shader = RadialGradient(
            s / 2f, s / 2f, s * 0.78f,
            intArrayOf(Color.argb(0, 10, 25, 90), Color.argb(0, 10, 25, 90), Color.argb(54, 10, 25, 90)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // luz que entra pelo canto de cima/esquerda
        p.shader = RadialGradient(
            s * 0.26f, s * 0.20f, s * 0.70f,
            Color.argb(92, 255, 255, 255), Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, s, s, p)

        // brilho de luz refletida embaixo
        p.shader = LinearGradient(
            0f, s * 0.68f, 0f, s,
            Color.argb(0, 255, 255, 255), Color.argb(46, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, s * 0.68f, s, s, p)
        p.shader = null

        // o logo: branco sólido, sempre do mesmo tamanho e centralizado
        val glyph = buildGlyph(icon)
        if (glyph != null) {
            drawNormalized(c, glyph, s)
            glyph.recycle()
        } else {
            // sem como virar logo (arte/foto): mantém o ícone original, com as cores dele
            drawWhole(c, icon, s, s * 0.74f)
        }
        c.restore()

        // borda de luz nítida e fina
        val sw = s * 0.02f
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
                Color.argb(255, 255, 255, 255), Color.argb(70, 255, 255, 255),
                Color.argb(70, 255, 255, 255), Color.argb(210, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(inner, p)

        // fio de luz interno: espessura de vidro
        val inner2 = Path()
        val k2 = (s - sw * 4.6f) / s
        val m2 = Matrix()
        m2.setScale(k2, k2, s / 2f, s / 2f)
        tile.transform(m2, inner2)
        p.strokeWidth = sw * 0.7f
        p.shader = LinearGradient(
            0f, 0f, s, s,
            intArrayOf(
                Color.argb(80, 255, 255, 255), Color.argb(10, 255, 255, 255),
                Color.argb(10, 255, 255, 255), Color.argb(50, 255, 255, 255)
            ),
            floatArrayOf(0f, 0.4f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(inner2, p)

        return BitmapDrawable(ctx.resources, bmp)
    }

    /** Monta o logo em branco sólido (G x G). Retorna null se o ícone não dá para virar logo. */
    private fun buildGlyph(icon: Drawable): Bitmap? {
        val fg: Drawable? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable)
                icon.foreground
            else null
        val kind = if (fg != null) glyphKind(fg) else KIND_UNUSABLE

        if (fg != null && kind != KIND_UNUSABLE) {
            val bm = Bitmap.createBitmap(G, G, Bitmap.Config.ARGB_8888)
            val cv = Canvas(bm)
            val box = G * 1.2f
            val off = (G - box) / 2f
            fg.setBounds(off.toInt(), off.toInt(), (off + box).toInt(), (off + box).toInt())
            fg.draw(cv)
            val px = IntArray(G * G)
            bm.getPixels(px, 0, G, 0, 0, G, G)
            for (i in px.indices) {
                val q = px[i]
                val a = Color.alpha(q)
                if (a == 0) continue
                val k = if (kind == KIND_WHITE_GLYPH) 1f else {
                    // colorido: parte clara vira branco sólido, parte escura vira "entalhe" (vazado)
                    val lum = (0.299f * Color.red(q) + 0.587f * Color.green(q) + 0.114f * Color.blue(q)) / 255f
                    smooth((lum - 0.10f) / 0.22f)
                }
                px[i] = Color.argb((a * k).toInt(), 255, 255, 255)
            }
            bm.setPixels(px, 0, G, 0, 0, G, G)
            return bm
        }
        return silhouette(icon)
    }

    /** Ícone com fundo próprio (liso ou em degradê): tira o fundo e fica só o desenho. Null se o fundo for arte/foto. */
    private fun silhouette(icon: Drawable): Bitmap? {
        val n = G
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            val box = n * 1.5f
            val o = ((n - box) / 2f).toInt()
            val e = (o + box).toInt()
            icon.background?.let { it.setBounds(o, o, e, e); it.draw(cv) }
            icon.foreground?.let { it.setBounds(o, o, e, e); it.draw(cv) }
        } else {
            icon.setBounds(0, 0, n, n)
            icon.draw(cv)
        }

        val px = IntArray(n * n)
        bm.getPixels(px, 0, n, 0, 0, n, n)
        val inset = n / 16
        val far = n - 1 - inset
        val span = (far - inset).toFloat()

        val tl = px[inset * n + inset]
        val tr = px[inset * n + far]
        val bl = px[far * n + inset]
        val br = px[far * n + far]
        val alphaAvg = (Color.alpha(tl) + Color.alpha(tr) + Color.alpha(bl) + Color.alpha(br)) / 4
        val transparentBg = alphaAvg < 128

        // cor estimada do fundo em (x,y): interpolação dos 4 cantos (serve para fundo liso e para degradê)
        fun est(x: Int, y: Int, ch: Int): Float {
            val u = ((x - inset) / span).coerceIn(0f, 1f)
            val v = ((y - inset) / span).coerceIn(0f, 1f)
            fun c(q: Int): Float = when (ch) {
                0 -> Color.red(q).toFloat()
                1 -> Color.green(q).toFloat()
                else -> Color.blue(q).toFloat()
            }
            return (1 - u) * (1 - v) * c(tl) + u * (1 - v) * c(tr) + (1 - u) * v * c(bl) + u * v * c(br)
        }

        if (!transparentBg) {
            // o fundo tem que ser liso: pontos da borda precisam bater com a interpolação
            val bx = intArrayOf(n / 2, n / 2, inset, far, n / 4, 3 * n / 4, inset, inset)
            val by = intArrayOf(inset, far, n / 2, n / 2, inset, inset, n / 4, 3 * n / 4)
            for (k in bx.indices) {
                val q = px[by[k] * n + bx[k]]
                val d = maxOf(
                    abs(Color.red(q) - est(bx[k], by[k], 0)),
                    abs(Color.green(q) - est(bx[k], by[k], 1)),
                    abs(Color.blue(q) - est(bx[k], by[k], 2))
                )
                if (d > 45f) { bm.recycle(); return null }
            }
        }

        var fgCount = 0
        for (y in 0 until n) {
            for (x in 0 until n) {
                val i = y * n + x
                val q = px[i]
                val a: Int
                if (transparentBg) {
                    a = Color.alpha(q)
                } else {
                    val d = maxOf(
                        abs(Color.red(q) - est(x, y, 0)),
                        abs(Color.green(q) - est(x, y, 1)),
                        abs(Color.blue(q) - est(x, y, 2))
                    ) / 255f
                    a = (Color.alpha(q) * smooth((d - 0.10f) / 0.2f)).toInt()
                }
                if (a > 100) fgCount++
                px[i] = Color.argb(a, 255, 255, 255)
            }
        }
        if (fgCount > n * n * 0.42 || fgCount < n * n * 0.01) { bm.recycle(); return null }
        bm.setPixels(px, 0, n, 0, 0, n, n)
        return bm
    }

    /** Desenha o logo centralizado e sempre com o mesmo tamanho (~56% da peça). */
    private fun drawNormalized(c: Canvas, gb: Bitmap, s: Float) {
        val n = gb.width
        val px = IntArray(n * n)
        gb.getPixels(px, 0, n, 0, 0, n, n)
        var minX = n
        var minY = n
        var maxX = -1
        var maxY = -1
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (Color.alpha(px[y * n + x]) > 48) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        val sc = s * 0.62f / maxOf(w, h).toFloat()
        val dw = w * sc
        val dh = h * sc
        val dst = RectF((s - dw) / 2f, (s - dh) / 2f, (s + dw) / 2f, (s + dh) / 2f)
        val src = Rect(minX, minY, maxX + 1, maxY + 1)
        // brilho suave atrás do logo (vidro iluminado)
        val glow = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        glow.maskFilter = BlurMaskFilter(s * 0.04f, BlurMaskFilter.Blur.NORMAL)
        glow.alpha = 110
        c.drawBitmap(gb, src, dst, glow)
        c.drawBitmap(gb, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
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
    private fun squircle(s: Float, n: Double = 4.6): Path {
        val path = Path()
        val a = s / 2.0
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
