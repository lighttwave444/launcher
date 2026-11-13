package com.example.liquidlauncher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        home = findViewById(R.id.home)
        drawer = findViewById(R.id.drawer)
        drawerContent = findViewById(R.id.drawer_content)
        blurImage = findViewById(R.id.blur)
        recyclerView = findViewById(R.id.app_grid)
        search = findViewById(R.id.search)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            home.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            drawerContent.setPadding(bars.left, bars.top, bars.right, max(bars.bottom, ime.bottom))
            insets
        }

        adapter = AppAdapter(apps) { launchApp(it) }
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

    /** Desfoque próprio: pega o papel de parede, reduz e borra — funciona sem depender do sistema. */
    private fun loadBlurredWallpaper() {
        try {
            val d = WallpaperManager.getInstance(this).drawable ?: return
            val dw = d.intrinsicWidth
            val dh = d.intrinsicHeight
            if (dw <= 0 || dh <= 0) return
            val m = resources.displayMetrics
            val w = 120
            val h = max(1, (w.toFloat() * m.heightPixels / m.widthPixels).toInt())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val s = max(w.toFloat() / dw, h.toFloat() / dh)
            c.translate((w - dw * s) / 2f, (h - dh * s) / 2f)
            c.scale(s, s)
            d.setBounds(0, 0, dw, dh)
            d.draw(c)
            blurImage.setImageBitmap(bmp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurImage.setRenderEffect(
                    RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.CLAMP)
                )
            }
        } catch (t: Throwable) {
            // sem acesso ao papel de parede: fica o fundo escuro sólido da gaveta
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
                AppInfo(
                    it.loadLabel(pm).toString(),
                    it.loadIcon(pm),
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
