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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Estilo "Glassify": peça de vidro escuro em formato squircle (cantos macios),
 * com borda de luz fininha, e o desenho (logo) do app por cima, com as cores vivas.
 */
object IconGlass {
    private var S = 240

    /** Gera os ícones já no tamanho em que aparecem na tela (nada de reduzir depois = sem serrilhado). */
    fun setTargetSize(px: Int) {
        S = px.coerceIn(96, 480)
    }

    /** Reduz em etapas (metade por vez) e só então ajusta: mantém as bordas lisas, sem pixelizar. */
    private fun smoothScale(src: Bitmap, tw: Int, th: Int): Bitmap {
        if (src.width == tw && src.height == th) return src
        var b = src
        while (b.width / 2 >= tw && b.height / 2 >= th) {
            val nb = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
            if (b !== src) b.recycle()
            b = nb
        }
        val out = Bitmap.createScaledBitmap(b, maxOf(1, tw), maxOf(1, th), true)
        if (b !== src && b !== out) b.recycle()
        return out
    }

    /**
     * IMPORTANTE: o Android guarda em cada bitmap a densidade "de fábrica" do aparelho. Se você mudou o DPI,
     * o ícone era esticado/encolhido por essa diferença antes de aparecer (e ficava serrilhado/borrado).
     * Com DENSITY_NONE o ícone é mostrado pixel por pixel, no tamanho exato em que foi desenhado.
     */
    private fun asDrawable(ctx: Context, bmp: Bitmap): Drawable {
        bmp.density = Bitmap.DENSITY_NONE
        return BitmapDrawable(ctx.resources, bmp)
    }

    /**
     * O Android NÃO suaviza o recorte (clipPath): a beira da peça fica serrilhada.
     * Aqui apaga tudo fora do formato com uma máscara SUAVE (antialias), deixando a borda lisa.
     */
    private fun softenEdge(c: Canvas, mask: Path) {
        val inv = Path(mask)
        inv.fillType = Path.FillType.INVERSE_WINDING
        val q = Paint(Paint.ANTI_ALIAS_FLAG)
        q.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        c.drawPath(inv, q)
    }

    /** theme 0 = vidro escuro (logo colorido) | theme 1 = vidro claro (logo prateado, estilo Glassify Glass) */
    fun make(ctx: Context, icon: Drawable, theme: Int = 0, pkg: String = ""): Drawable =
        when (theme) {
            1 -> makeGlass(ctx, icon)
            2 -> makePack(ctx, icon, pkg) ?: makeDark(ctx, icon)
            else -> makeDark(ctx, icon)
        }

    // ---------------- TEMA 3: pack de PNGs (desenhados app por app) sobre peça de vidro escuro ----------------

    // ---------------- DESENHO EM VETOR (sem PNG): nítido em qualquer tamanho ----------------

    private class Recipe(val cutWhite: Boolean, val cutLight: Boolean, val bgRemove: Boolean)
    private var recipes: Map<String, Recipe>? = null

    private fun recipeFor(ctx: Context, pkg: String): Recipe? {
        var m = recipes
        if (m == null) {
            val map = HashMap<String, Recipe>()
            try {
                val txt = ctx.assets.open("recipes.json").bufferedReader().use { it.readText() }
                val o = org.json.JSONObject(txt)
                val keysIt = o.keys()
                while (keysIt.hasNext()) {
                    val k = keysIt.next()
                    val r = o.getJSONObject(k)
                    map[k] = Recipe(
                        r.optBoolean("cutwhite", false),
                        r.optBoolean("cutlight", false),
                        r.optString("mode", "") == "bgremove"
                    )
                }
            } catch (t: Throwable) { }
            recipes = map
            m = map
        }
        return m[pkg]
    }

    /**
     * Desenha a camada da frente do ícone adaptável DIRETO no tamanho final (o vetor é refeito nesse
     * tamanho, então a borda sai perfeita), com as mesmas regras de cor do pack:
     * desenho branco -> cor do fundo; desenho colorido -> mantém (clareando o que sumiria);
     * detalhes brancos viram vazado quando a receita pede. Devolve um Bitmap s x s pronto para colar.
     */
    private fun buildCrispGlyph(icon: Drawable, s: Int, ratio: Float, rec: Recipe?): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable) return null
        val fg = icon.foreground ?: return null
        val w0 = 256
        val box = w0 * 1.5f
        val l = ((w0 - box) / 2f).toInt()
        val r = l + box.toInt()
        val bgRemove = rec != null && rec.bgRemove

        // passo 1: mede onde está o desenho
        val probe = Bitmap.createBitmap(w0, w0, Bitmap.Config.ARGB_8888)
        fg.setBounds(l, l, r, r)
        fg.draw(Canvas(probe))
        val pp = IntArray(w0 * w0)
        probe.getPixels(pp, 0, w0, 0, 0, w0, w0)
        probe.recycle()

        val cs = intArrayOf(8 * w0 + 8, 8 * w0 + (w0 - 9), (w0 - 9) * w0 + 8, (w0 - 9) * w0 + (w0 - 9))
        var bgR = 0f
        var bgG = 0f
        var bgB = 0f
        if (bgRemove) {
            for (i in cs) { bgR += Color.red(pp[i]); bgG += Color.green(pp[i]); bgB += Color.blue(pp[i]) }
            bgR /= 4f; bgG /= 4f; bgB /= 4f
        } else {
            var opaque = 0
            for (i in cs) if (Color.alpha(pp[i]) > 100) opaque++
            if (opaque >= 3) return null            // a camada traz fundo próprio: não serve
        }

        var minX = w0
        var minY = w0
        var maxX = -1
        var maxY = -1
        for (y in 0 until w0) {
            for (x in 0 until w0) {
                val q = pp[y * w0 + x]
                val present = if (bgRemove) {
                    maxOf(abs(Color.red(q) - bgR), abs(Color.green(q) - bgG), abs(Color.blue(q) - bgB)) / 255f > 0.2f
                } else {
                    Color.alpha(q) > 64
                }
                if (present) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        val k = ratio * s / maxOf(bw, bh).toFloat()
        val tx = s / 2f - (minX + maxX + 1) / 2f * k
        val ty = s / 2f - (minY + maxY + 1) / 2f * k

        // passo 2: desenha o vetor já no tamanho final
        val g = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cv = Canvas(g)
        cv.translate(tx, ty)
        cv.scale(k, k)
        fg.setBounds(l, l, r, r)
        fg.draw(cv)

        // cor do fundo (suave) no mesmo enquadramento: dá a cor da marca / o degradê
        var bgPx: IntArray? = null
        val bgL = icon.background
        if (bgL != null && !bgRemove) {
            val sm = 24
            val smb = Bitmap.createBitmap(sm, sm, Bitmap.Config.ARGB_8888)
            val bbox = sm * 1.5f
            val bl = ((sm - bbox) / 2f).toInt()
            bgL.setBounds(bl, bl, bl + bbox.toInt(), bl + bbox.toInt())
            bgL.draw(Canvas(smb))
            val bgc = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val cv2 = Canvas(bgc)
            cv2.translate(tx, ty)
            val f = k * w0 / sm
            cv2.scale(f, f)
            cv2.drawBitmap(smb, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            val arr = IntArray(s * s)
            bgc.getPixels(arr, 0, s, 0, 0, s, s)
            bgPx = arr
            smb.recycle()
            bgc.recycle()
        }

        val px = IntArray(s * s)
        g.getPixels(px, 0, s, 0, 0, s, s)
        val hsv = FloatArray(3)
        val hsv2 = FloatArray(3)

        // o desenho é branco? (decide se pinta com a cor do fundo)
        var solid = 0
        var whiteish = 0
        var br = 0L
        var bg = 0L
        var bb = 0L
        var bn = 0
        if (!bgRemove) {
            for (i in px.indices) {
                val q = px[i]
                if (Color.alpha(q) < 128) continue
                solid++
                Color.colorToHSV(q, hsv)
                val lum = (0.299f * Color.red(q) + 0.587f * Color.green(q) + 0.114f * Color.blue(q)) / 255f
                if (hsv[1] < 0.2f && lum > 0.78f) whiteish++
                val bq = bgPx?.get(i)
                if (bq != null && Color.alpha(bq) > 0) {
                    br += Color.red(bq); bg += Color.green(bq); bb += Color.blue(bq); bn++
                }
            }
            if (solid == 0) { g.recycle(); return null }
        }
        val whiteGlyph = !bgRemove && whiteish.toFloat() / maxOf(solid, 1) > 0.8f
        var neutralBg = true
        if (bn > 0) {
            Color.colorToHSV(Color.rgb((br / bn).toInt(), (bg / bn).toInt(), (bb / bn).toInt()), hsv)
            neutralBg = hsv[1] < 0.2f || hsv[2] < 0.12f
        }

        for (i in px.indices) {
            val q = px[i]
            var rr = Color.red(q)
            var gg = Color.green(q)
            var bl2 = Color.blue(q)
            var cov: Float
            if (bgRemove) {
                val d = maxOf(abs(rr - bgR), abs(gg - bgG), abs(bl2 - bgB)) / 255f
                cov = smooth((d - 0.06f) / 0.22f)
                if (cov <= 0f) { px[i] = 0; continue }
                // tira a mistura com o fundo para a borda não ficar clara
                rr = ((rr - (1f - cov) * bgR) / cov).coerceIn(0f, 255f).toInt()
                gg = ((gg - (1f - cov) * bgG) / cov).coerceIn(0f, 255f).toInt()
                bl2 = ((bl2 - (1f - cov) * bgB) / cov).coerceIn(0f, 255f).toInt()
            } else {
                val a = Color.alpha(q)
                if (a == 0) continue
                cov = ((a / 255f) - 0.2f) / 0.6f
                if (cov <= 0f) { px[i] = 0; continue }
                if (cov > 1f) cov = 1f
            }
            val base = Color.rgb(rr, gg, bl2)
            val rgb: Int
            if (whiteGlyph) {
                val bq = bgPx?.get(i)
                if (neutralBg || bq == null || Color.alpha(bq) == 0) {
                    rgb = Color.WHITE
                } else {
                    Color.colorToHSV(bq, hsv)
                    hsv[1] = minOf(1f, hsv[1] * 1.1f + 0.04f)
                    hsv[2] = maxOf(hsv[2], 0.9f)
                    rgb = Color.HSVToColor(hsv)
                }
            } else {
                Color.colorToHSV(base, hsv)
                val neutralDark = (hsv[2] < 0.35f && hsv[1] < 0.6f) || (hsv[1] < 0.25f && hsv[2] < 0.5f)
                if (neutralDark) {
                    rgb = Color.WHITE
                } else {
                    hsv[2] = maxOf(hsv[2], 0.72f)
                    rgb = Color.HSVToColor(hsv)
                }
            }
            if (rec != null && (rec.cutWhite || rec.cutLight)) {
                Color.colorToHSV(base, hsv2)
                val lum = (0.299f * rr + 0.587f * gg + 0.114f * bl2) / 255f
                val w = if (rec.cutLight) smooth((lum - 0.70f) / 0.18f)
                else smooth((lum - 0.72f) / 0.16f) * smooth((0.30f - hsv2[1]) / 0.12f)
                cov *= (1f - w)
            }
            px[i] = Color.argb((cov * 255f).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        }
        g.setPixels(px, 0, s, 0, 0, s, s)
        return g
    }

    /**
     * Peça igual à do Glassify (medida na referência): formato squircle n=2,6, miolo liso e escuro
     * (19,22,26) e um fio de luz fininho cuja força muda ao redor — forte em cima/esquerda,
     * médio embaixo/direita e fraco no resto (luz de cima à esquerda).
     */
    private fun rimAlpha(theta: Float): Float {
        fun diff(a: Float, b: Float): Float {
            var d = a - b
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            return d
        }
        val d1 = diff(theta, 220f)
        val s1 = if (d1 <= 0f) 46f else 38f
        val g1 = exp((-(d1 * d1) / (2f * s1 * s1)).toDouble()).toFloat()
        val d2 = diff(theta, 45f)
        val s2 = if (d2 >= 0f) 22f else 35f
        val g2 = exp((-(d2 * d2) / (2f * s2 * s2)).toDouble()).toFloat()
        val lum = 34f + 131f * g1 + 46f * g2
        return ((lum - 21f) / 234f).coerceIn(0f, 1f)
    }

    private fun makePack(ctx: Context, icon: Drawable, pkg: String): Drawable? {
        val logo: Bitmap? = try {
            ctx.assets.open("icons/$pkg.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (t: Throwable) {
            null
        }

        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = S.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tile = squircle(s, 2.6)
        val unit = s / 150f                      // 1 px da referência (peça de 150 px)

        // fio de luz: força por ângulo (SweepGradient começa em 3h e gira no sentido horário)
        val steps = 24
        val rimColors = IntArray(steps + 1) { i ->
            Color.argb((rimAlpha(i * 360f / steps) * 255f).toInt(), 255, 255, 255)
        }
        val glowColors = IntArray(steps + 1) { i ->
            Color.argb((rimAlpha(i * 360f / steps) * 0.04f * 255f).toInt(), 255, 255, 255)
        }
        val pos = FloatArray(steps + 1) { i -> i.toFloat() / steps }

        c.save()
        c.clipPath(tile)
        // miolo liso e escuro, igual ao da referência
        p.color = Color.rgb(19, 22, 26)
        c.drawRect(0f, 0f, s, s, p)

        val rec = recipeFor(ctx, pkg)
        val crisp: Bitmap? = if (rec != null || logo == null) buildCrispGlyph(icon, S, 0.567f, rec) else null
        if (crisp != null) {
            c.drawBitmap(crisp, 0f, 0f, null)
            crisp.recycle()
            logo?.recycle()
        } else if (logo != null) {
            // o desenho do app (o PNG cobre 146 px da referência; a peça tem 150)
            val k = 146f / 150f
            val tw = (s * k).toInt()
            val scaled = smoothScale(logo, tw, tw)
            val inset = (s - tw) / 2f
            c.drawBitmap(scaled, inset, inset, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            if (scaled !== logo) scaled.recycle()
            logo.recycle()
        } else {
            // app sem PNG (instalado depois): o mesmo vidro, com o logo gerado do ícone dele
            val g = buildBrandGlyph(icon)
            if (g != null) {
                drawNormalized(c, g, s, 0.567f, false)
                g.recycle()
            } else {
                drawWhole(c, icon, s, s * 0.74f)
            }
        }
        c.restore()
        softenEdge(c, tile)

        fun ring(width: Float, colors: IntArray) {
            val inner = Path()
            val m = Matrix()
            val sc = (s - width) / s
            m.setScale(sc, sc, s / 2f, s / 2f)
            tile.transform(m, inner)
            val q = Paint(Paint.ANTI_ALIAS_FLAG)
            q.style = Paint.Style.STROKE
            q.strokeWidth = width
            q.shader = android.graphics.SweepGradient(s / 2f, s / 2f, colors, pos)
            c.drawPath(inner, q)
        }
        ring(8f * unit, glowColors)    // brilho interno bem fraco
        ring(2.4f * unit, rimColors)   // fio de luz da borda

        return asDrawable(ctx, bmp)
    }


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
        softenEdge(c, tile)

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

        return asDrawable(ctx, bmp)
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
        softenEdge(c, tile)

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

        return asDrawable(ctx, bmp)
    }

    /** Cor da marca = cor do fundo original do ícone; null se for branco/cinza/preto. */
    private fun brandColorOf(bg: Drawable?): Int? {
        if (bg == null) return null
        val n = 48
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val box = n * 1.5f
        val off = ((n - box) / 2f).toInt()
        bg.setBounds(off, off, off + box.toInt(), off + box.toInt())
        bg.draw(cv)
        var r = 0
        var g = 0
        var b = 0
        var cnt = 0
        for (y in n / 4 until 3 * n / 4) {
            for (x in n / 4 until 3 * n / 4) {
                val q = bm.getPixel(x, y)
                if (Color.alpha(q) > 200) { r += Color.red(q); g += Color.green(q); b += Color.blue(q); cnt++ }
            }
        }
        bm.recycle()
        if (cnt == 0) return null
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb(r / cnt, g / cnt, b / cnt), hsv)
        if (hsv[1] < 0.2f || hsv[2] < 0.12f) return null
        hsv[1] = minOf(1f, hsv[1] * 1.1f + 0.04f)
        hsv[2] = maxOf(hsv[2], 0.9f)
        return Color.HSVToColor(hsv)
    }

    /**
     * Logo para app sem PNG: desenho branco -> cor do fundo do ícone; desenho colorido -> mantém
     * as cores (clareando só o que sumiria no vidro escuro). Sem como separar -> branco.
     */
    private fun buildBrandGlyph(icon: Drawable): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is AdaptiveIconDrawable) {
            val fg = icon.foreground
            if (fg != null) {
                val kind = glyphKind(fg)
                if (kind != KIND_UNUSABLE) {
                    val bm = Bitmap.createBitmap(G, G, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bm)
                    val box = G * 1.5f
                    val off = ((G - box) / 2f).toInt()
                    fg.setBounds(off, off, off + box.toInt(), off + box.toInt())
                    fg.draw(cv)
                    val px = IntArray(G * G)
                    bm.getPixels(px, 0, G, 0, 0, G, G)
                    val brand: Int = if (kind == KIND_WHITE_GLYPH) (brandColorOf(icon.background) ?: Color.WHITE) else Color.WHITE
                    val hsv = FloatArray(3)
                    for (i in px.indices) {
                        val q = px[i]
                        val a = Color.alpha(q)
                        if (a == 0) continue
                        val ca = (((a / 255f) - 0.2f) / 0.6f).coerceIn(0f, 1f)
                        if (ca <= 0f) { px[i] = 0; continue }
                        val rgb: Int
                        if (kind == KIND_WHITE_GLYPH) {
                            rgb = brand
                        } else {
                            Color.colorToHSV(q, hsv)
                            val neutralDark = (hsv[2] < 0.35f && hsv[1] < 0.6f) || (hsv[1] < 0.25f && hsv[2] < 0.5f)
                            if (neutralDark) {
                                rgb = Color.WHITE
                            } else {
                                hsv[2] = maxOf(hsv[2], 0.72f)
                                rgb = Color.HSVToColor(hsv)
                            }
                        }
                        px[i] = Color.argb((ca * 255f).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
                    }
                    bm.setPixels(px, 0, G, 0, 0, G, G)
                    return bm
                }
            }
        }
        return silhouette(icon)
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
    private fun drawNormalized(c: Canvas, gb: Bitmap, s: Float, ratio: Float = 0.62f, withGlow: Boolean = true) {
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
        val sc = s * ratio / maxOf(w, h).toFloat()
        val dw = w * sc
        val dh = h * sc
        val dst = RectF((s - dw) / 2f, (s - dh) / 2f, (s + dw) / 2f, (s + dh) / 2f)
        val src = Rect(minX, minY, maxX + 1, maxY + 1)
        if (withGlow) {
            // brilho suave atrás do logo (vidro iluminado)
            val glow = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            glow.maskFilter = BlurMaskFilter(s * 0.04f, BlurMaskFilter.Blur.NORMAL)
            glow.alpha = 110
            c.drawBitmap(gb, src, dst, glow)
        }
        // logo nítido: recorta, reduz em etapas e desenha 1:1
        val crop = Bitmap.createBitmap(gb, minX, minY, w, h)
        val scaled = smoothScale(crop, maxOf(1, dw.toInt()), maxOf(1, dh.toInt()))
        c.drawBitmap(scaled, dst.left, dst.top, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        if (scaled !== crop) scaled.recycle()
        crop.recycle()
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
        val layer = c.saveLayer(0f, 0f, s, s, null)
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
        softenEdge(c, path)
        c.restoreToCount(layer)
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
