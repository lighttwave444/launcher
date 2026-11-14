package com.example.liquidlauncher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var adapter: AppAdapter
    private lateinit var gestures: GestureDetector

    private val apps = mutableListOf<AppInfo>()
    private var drawerOpen = false
    private var unit = 1f
    private val iconCache = mutableMapOf<String, android.graphics.drawable.Drawable>()

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

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            drawerContent.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }

        adapter = AppAdapter(apps, unit) { launchApp(it) }
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { adapter.filter(s?.toString().orEmpty()) }
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
        setMargins(search, 16f, 12f, 16f, 8f)
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15f * unit)
        search.setPadding(ui(16f), 0, ui(16f), 0)
        search.compoundDrawablePadding = ui(10f)
        val sd = search.compoundDrawablesRelative[0]
        if (sd != null) {
            sd.setBounds(0, 0, ui(20f), ui(20f))
            search.setCompoundDrawablesRelative(sd, null, null, null)
        }

        recyclerView.setPadding(ui(4f), ui(8f), ui(4f), ui(24f))
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
                val glassIcon = iconCache.getOrPut(key) { IconGlass.make(this, it.loadIcon(pm)) }
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
        adapter.filter(search.text.toString())
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
