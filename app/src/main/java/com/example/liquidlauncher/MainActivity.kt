package com.example.liquidlauncher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.Button
import java.io.File
import java.io.FileOutputStream
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var home: View
    private lateinit var drawer: View
    private lateinit var drawerContent: View
    private lateinit var blurImage: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var search: EditText
    private lateinit var adapter: PagesAdapter
    private lateinit var dots: LinearLayout
    private var shownApps: List<AppInfo> = emptyList()
    private lateinit var gestures: GestureDetector

    private val apps = mutableListOf<AppInfo>()
    private var drawerOpen = false
    private var unit = 1f
    private val iconCache = mutableMapOf<String, android.graphics.drawable.Drawable>()
    private val rawIconCache = mutableMapOf<String, android.graphics.drawable.Drawable>()
    private var iconTheme = 0

    // Apps do dock: cada item é uma lista de nomes/pacotes possíveis (o primeiro que existir vale)
    private val dockSpecs = listOf(
        listOf("Telefone", "Phone"),
        listOf("com.whatsapp", "WhatsApp"),
        listOf("Chrome"),
        listOf("Câmera", "Camera")
    )
    private val dockIds = listOf(R.id.dock0, R.id.dock1, R.id.dock2, R.id.dock3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "dp virtual": largura da tela em pixels / 360 — não depende do DPI configurado no sistema
        val dm = resources.displayMetrics
        unit = minOf(dm.widthPixels, dm.heightPixels) / 360f
        iconTheme = getSharedPreferences("liquid", Context.MODE_PRIVATE).getInt("icon_theme", 0)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        drawer = findViewById(R.id.drawer)
        drawerContent = findViewById(R.id.drawer_content)
        blurImage = findViewById(R.id.blur)
        recyclerView = findViewById(R.id.app_grid)
        search = findViewById(R.id.search)
        applyUnits()
        findViewById<View>(R.id.theme_btn).apply {
            background = GearDrawable()
            setOnClickListener { showThemeDialog() }
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            drawerContent.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }

        adapter = PagesAdapter(unit, 4, { launchApp(it) }, { showAppMenu(it) })
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter
        PagerSnapHelper().attachToRecyclerView(recyclerView)
        dots = findViewById(R.id.dots)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val pos = lm.findFirstCompletelyVisibleItemPosition()
                    if (pos >= 0) updateDots(pos)
                }
            }
        })
        // quando o tamanho da lista muda, refaz as páginas (quantos apps cabem por página)
        recyclerView.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) recyclerView.post { repage() }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString().orEmpty()) }
        })

        // Gesto: deslizar para cima abre a gaveta; para baixo (no topo da lista) fecha
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dy = e2.y - e1.y
                val dx = e2.x - e1.x
                val min = 80 * resources.displayMetrics.density
                if (abs(dy) < min || abs(dy) < abs(dx)) return false
                if (!drawerOpen && dy < 0) { openDrawer(); return true }
                if (drawerOpen && dy > 0 && !recyclerView.canScrollVertically(-1)) {
                    closeDrawer(); return true
                }
                return false
            }
        })

        // Gaveta começa escondida embaixo da tela
        root.post {
            drawer.translationY = root.height.toFloat()
            drawer.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadInstalledApps()
        loadBlurredWallpaper()
        ensureNavService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Apertar Home de novo fecha a gaveta
        if (drawerOpen) closeDrawer()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------- escolha do tema dos ícones ----------

    private fun showThemeDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(ui(16f), ui(12f), ui(16f), ui(4f))
        val names = arrayOf("Vidro escuro", "Vidro claro")
        val sample = apps.take(4)
        var dialog: AlertDialog? = null

        for (t in 0..1) {
            val selected = t == iconTheme
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(ui(12f), ui(10f), ui(12f), ui(12f))
            val bg = GradientDrawable()
            bg.cornerRadius = ui(16f).toFloat()
            bg.setColor(Color.argb(if (selected) 40 else 14, 128, 128, 128))
            bg.setStroke(ui(if (selected) 2f else 1f), Color.argb(if (selected) 220 else 70, 128, 128, 128))
            card.background = bg

            val title = TextView(this)
            title.text = names[t] + (if (selected) "  \u2713" else "")
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * unit)
            card.addView(title)

            val icons = LinearLayout(this)
            icons.orientation = LinearLayout.HORIZONTAL
            icons.setPadding(ui(10f), ui(10f), ui(10f), ui(10f))
            val back = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(27, 42, 90), Color.rgb(10, 15, 31))
            )
            back.cornerRadius = ui(14f).toFloat()
            icons.background = back
            for (a in sample) {
                val key = a.packageName + "/" + a.className
                val raw = rawIconCache[key] ?: continue
                val iv = ImageView(this)
                iv.setImageDrawable(
                    iconCache.getOrPut(t.toString() + "|" + key) { IconGlass.make(this, raw, t) }
                )
                val lp = LinearLayout.LayoutParams(ui(52f), ui(52f))
                lp.marginEnd = ui(10f)
                icons.addView(iv, lp)
            }
            val ilp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            ilp.topMargin = ui(8f)
            card.addView(icons, ilp)

            card.setOnClickListener {
                iconTheme = t
                getSharedPreferences("liquid", Context.MODE_PRIVATE)
                    .edit().putInt("icon_theme", t).apply()
                loadInstalledApps()
                dialog?.dismiss()
            }
            val clp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clp.bottomMargin = ui(10f)
            box.addView(card, clp)
        }

        val exportBtn = Button(this)
        exportBtn.text = "Exportar ícones dos apps"
        exportBtn.setOnClickListener {
            dialog?.dismiss()
            exportIcons()
        }
        box.addView(exportBtn)

        dialog = AlertDialog.Builder(this)
            .setTitle("Tema dos ícones")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .show()
    }

    // ---------- exportar os ícones originais (para montar o pack de PNGs) ----------

    private fun renderLayer(d: android.graphics.drawable.Drawable, size: Int, scale: Float): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val box = size * scale
        val off = ((size - box) / 2f).toInt()
        d.setBounds(off, off, off + box.toInt(), off + box.toInt())
        d.draw(c)
        return bmp
    }

    private fun savePng(file: File, bmp: Bitmap) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    /** Salva, em Download/icones-apps, as camadas de cada ícone (fundo, desenho, inteiro) + a lista dos apps. */
    private fun exportIcons() {
        try {
            val dir = File("/sdcard/Download/icones-apps")
            dir.mkdirs()
            val list = StringBuilder()
            var count = 0
            for (a in apps) {
                val raw = rawIconCache[a.packageName + "/" + a.className] ?: continue
                val name = a.packageName
                list.append(a.label).append("|").append(a.packageName).append("|").append(a.className).append("\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && raw is AdaptiveIconDrawable) {
                    raw.background?.let { savePng(File(dir, "$name.bg.png"), renderLayer(it, 512, 1.5f)) }
                    raw.foreground?.let { savePng(File(dir, "$name.fg.png"), renderLayer(it, 512, 1.5f)) }
                    val full = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(full)
                    val box = 512 * 1.5f
                    val off = ((512 - box) / 2f).toInt()
                    raw.background?.let { it.setBounds(off, off, off + box.toInt(), off + box.toInt()); it.draw(cv) }
                    raw.foreground?.let { it.setBounds(off, off, off + box.toInt(), off + box.toInt()); it.draw(cv) }
                    savePng(File(dir, "$name.full.png"), full)
                } else {
                    savePng(File(dir, "$name.full.png"), renderLayer(raw, 512, 1.0f))
                }
                count++
            }
            File(dir, "apps.txt").writeText(list.toString())
            Toast.makeText(this, "Exportados $count ícones em Download/icones-apps", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Não consegui exportar: " + t.message, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- toque longo no ícone: informações / desinstalar ----------

    private fun showAppMenu(app: AppInfo) {
        val items = arrayOf("Informações do app", "Desinstalar")
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                if (which == 0) openAppInfo(app) else uninstall(app)
            }
            .show()
    }

    private fun openAppInfo(app: AppInfo) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
        )
    }

    private fun isSystemApp(pkg: String): Boolean = try {
        (packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: Exception) {
        false
    }

    private fun uninstall(app: AppInfo) {
        if (!isSystemApp(app.packageName)) {
            // app instalado por você: diálogo normal do Android
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)))
        } else {
            // app do sistema (ex.: Gmail): só com root, removendo para o seu usuário
            AlertDialog.Builder(this)
                .setTitle("Remover " + app.label + "?")
                .setMessage(
                    "É um app do sistema. Ele será removido só para o seu usuário (precisa de root) " +
                        "e dá para restaurar depois. Pode causar problemas se for essencial."
                )
                .setPositiveButton("Remover") { _, _ -> removeSystemApp(app) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun removeSystemApp(app: AppInfo) {
        Thread {
            val ok = try {
                val pr = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "pm uninstall --user 0 " + app.packageName)
                )
                pr.waitFor() == 0
            } catch (t: Throwable) {
                false
            }
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) app.label + " removido" else "Não consegui remover (root negado?)",
                    Toast.LENGTH_LONG
                ).show()
                loadInstalledApps()
            }
        }.start()
    }

    // ---------- barra de gestos (serviço de acessibilidade) ----------

    private fun ensureNavService() {
        val cn = packageName + "/" + packageName + ".NavService"
        val cur = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        if (cur.split(':').any { it.equals(cn, ignoreCase = true) }) return
        try {
            // com a permissão WRITE_SECURE_SETTINGS (dada por root) o app liga sozinho
            val newVal = if (cur.isEmpty()) cn else "$cur:$cn"
            Settings.Secure.putString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newVal
            )
            Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
        } catch (e: SecurityException) {
            val prefs = getSharedPreferences("liquid", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("asked_nav", false)) {
                prefs.edit().putBoolean("asked_nav", true).apply()
                AlertDialog.Builder(this)
                    .setTitle("Barra de gestos")
                    .setMessage(
                        "Ative \"Liquid Launcher - barra de gestos\" em Acessibilidade para ter a barra " +
                            "de voltar, início e recentes por cima dos apps."
                    )
                    .setPositiveButton("Abrir") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .setNegativeButton("Agora não", null)
                    .show()
            }
        }
    }

    private fun applyFilter(q: String) {
        val t = q.trim()
        shownApps = if (t.isEmpty()) apps.toList()
        else apps.filter { it.label.contains(t, ignoreCase = true) }
        repage()
    }

    /** Divide os apps em páginas: quantas linhas cabem na altura da gaveta x 4 colunas. */
    private fun repage() {
        val h = recyclerView.height
        if (h <= 0) return
        val rowPx = ui(112f)               // ícone + nome + espaços de uma linha
        val rows = maxOf(1, (h - ui(8f)) / rowPx)
        val perPage = rows * 4
        val pages = shownApps.chunked(perPage)
        adapter.submit(pages)
        recyclerView.scrollToPosition(0)
        buildDots(pages.size)
    }

    private fun buildDots(count: Int) {
        dots.removeAllViews()
        if (count <= 1) return
        for (i in 0 until count) {
            val dot = View(this)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val lp = LinearLayout.LayoutParams(ui(7f), ui(7f))
            lp.setMargins(ui(4f), 0, ui(4f), 0)
            dots.addView(dot, lp)
        }
        updateDots(0)
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until dots.childCount) {
            dots.getChildAt(i).alpha = if (i == selected) 1f else 0.35f
        }
    }

    private fun ui(v: Float): Int = (v * unit).toInt()

    private fun setMargins(v: View, l: Float, t: Float, r: Float, b: Float) {
        val p = v.layoutParams as ViewGroup.MarginLayoutParams
        p.setMargins(ui(l), ui(t), ui(r), ui(b))
        v.layoutParams = p
    }

    /** Põe todos os tamanhos em pixels fixos relativos à tela (imune ao DPI). */
    private fun applyUnits() {
        val clock = findViewById<TextView>(R.id.clock)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 80f * unit)
        setMargins(clock, 0f, 56f, 0f, 0f)
        findViewById<TextView>(R.id.date).setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f * unit)
        val hint = findViewById<TextView>(R.id.swipe_hint)
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f * unit)
        setMargins(hint, 0f, 0f, 0f, 12f)

        val dock = findViewById<View>(R.id.dock)
        dock.layoutParams.height = ui(94f)
        setMargins(dock, 16f, 0f, 16f, 12f)
        dock.setPadding(ui(11f), ui(11f), ui(11f), ui(11f))

        search.layoutParams.height = ui(48f)
        setMargins(findViewById(R.id.search_row), 16f, 12f, 16f, 8f)
        val tb = findViewById<View>(R.id.theme_btn)
        tb.layoutParams.width = ui(40f)
        tb.layoutParams.height = ui(40f)
        setMargins(tb, 10f, 0f, 0f, 0f)
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * unit)
        search.setPadding(ui(16f), 0, ui(16f), 0)
        search.compoundDrawablePadding = ui(10f)
        val sd = search.compoundDrawablesRelative[0]
        if (sd != null) {
            sd.setBounds(0, 0, ui(20f), ui(20f))
            search.setCompoundDrawablesRelative(sd, null, null, null)
        }

        recyclerView.setPadding(0, 0, 0, 0)
        findViewById<View>(R.id.dots).setPadding(0, ui(8f), 0, ui(18f))
    }

    private fun openDrawer() {
        drawerOpen = true
        drawer.animate().translationY(0f).setDuration(320)
            .setInterpolator(DecelerateInterpolator()).start()
        home.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(320).start()
    }

    private fun closeDrawer() {
        drawerOpen = false
        hideKeyboard()
        if (search.text.isNotEmpty()) search.setText("")
        recyclerView.scrollToPosition(0)
        drawer.animate().translationY(root.height.toFloat()).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).start()
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
    }

    private fun hideKeyboard() {
        search.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(search.windowToken, 0)
    }

    private var lastWallpaperId = -2

    /** Vidro líquido: lente leve (peças de vidro) + fundo borrado e saturado (gaveta). */
    private fun loadBlurredWallpaper() {
        val wm = WallpaperManager.getInstance(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val id = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
                if (id == lastWallpaperId) return
            } catch (t: Throwable) { /* segue e tenta carregar */ }
        }
        val m = resources.displayMetrics
        Thread {
            try {
                val d = wm.drawable ?: return@Thread
                val dw = d.intrinsicWidth
                val dh = d.intrinsicHeight
                if (dw <= 0 || dh <= 0) return@Thread
                val w = 270
                val h = max(1, (w.toFloat() * m.heightPixels / m.widthPixels).toInt())
                val base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(base)
                val sc = max(w.toFloat() / dw, h.toFloat() / dh)
                c.translate((w - dw * sc) / 2f, (h - dh * sc) / 2f)
                c.scale(sc, sc)
                d.setBounds(0, 0, dw, dh)
                d.draw(c)

                val lens = saturate(boxBlur(base, 3, 2), 1.5f)      // quase nítido, só suave
                val drawerBg = saturate(boxBlur(base, 9, 3), 1.7f)  // mais borrado, cores vivas

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    blurImage.setImageBitmap(drawerBg)
                    val dens = resources.displayMetrics.density
                    search.background = GlassDrawable(search, root, lens, ui(24f).toFloat())
                    findViewById<LinearLayout>(R.id.dock).background =
                        GlassDrawable(findViewById(R.id.dock), root, lens, ui(30f).toFloat())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try { lastWallpaperId = wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }
                        catch (t: Throwable) { }
                    }
                }
            } catch (t: Throwable) {
                // sem acesso ao papel de parede: ficam o fundo escuro e as peças de vidro simples
            }
        }.start()
    }

    private fun saturate(src: Bitmap, amount: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(amount) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var a = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        var b = IntArray(w * h)
        repeat(passes) {
            blurPass(a, b, w, h, radius, true)
            blurPass(b, a, w, h, radius, false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun blurPass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (l in 0 until lines) {
            for (i in 0 until len) {
                var rs = 0
                var gs = 0
                var bs = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) inp[l * w + j] else inp[j * w + l]
                    rs += (p shr 16) and 0xFF
                    gs += (p shr 8) and 0xFF
                    bs += p and 0xFF
                }
                val px = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                if (horizontal) out[l * w + i] = px else out[i * w + l] = px
            }
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                val key = it.activityInfo.packageName + "/" + it.activityInfo.name
                val ri = it
                val raw = rawIconCache.getOrPut(key) { ri.loadIcon(pm) }
                val glassIcon = iconCache.getOrPut(iconTheme.toString() + "|" + key) {
                    IconGlass.make(this, raw, iconTheme)
                }
                AppInfo(
                    it.loadLabel(pm).toString(),
                    glassIcon,
                    it.activityInfo.packageName,
                    it.activityInfo.name
                )
            }
            .sortedBy { it.label.lowercase() }
        apps.clear()
        apps.addAll(list)
        applyFilter(search.text.toString())
        bindDock()
    }

    private fun bindDock() {
        dockSpecs.forEachIndexed { i, keys ->
            val iv = findViewById<ImageView>(dockIds[i])
            val app = apps.firstOrNull { a ->
                keys.any { k -> a.packageName == k || a.label.equals(k, ignoreCase = true) }
            }
            if (app == null) {
                iv.visibility = View.INVISIBLE
                iv.setOnClickListener(null)
            } else {
                iv.visibility = View.VISIBLE
                iv.setImageDrawable(app.icon)
                iv.setOnClickListener { launchApp(app) }
            }
        }
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        startActivity(launchIntent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Launcher não fecha: voltar só recolhe a gaveta
        if (drawerOpen) closeDrawer()
    }
}
