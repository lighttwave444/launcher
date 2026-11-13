package com.example.liquidlauncher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mmin18.realtimeblurview.BlurView
import com.github.mmin18.realtimeblurview.RenderScriptBlur

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val apps = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full‑screen immersive mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        setContentView(R.layout.activity_main)

        // ---- Liquid glass (blur) setup ----
        val blurView = findViewById<BlurView>(R.id.blur_view)
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        blurView.setupWith(rootView)
            .setBlurAlgorithm(RenderScriptBlur(this))
            .setBlurRadius(20f) // adjust for stronger/weaker blur
            .setHasFixedTransformationMatrix(true)
        // -----------------------------------

        recyclerView = findViewById(R.id.app_grid)
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        recyclerView.adapter = AppAdapter(apps) { launchApp(it) }

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            val packageName = info.activityInfo.packageName
            val className = info.activityInfo.name
            apps.add(AppInfo(label, icon, packageName, className))
        }
        recyclerView.adapter?.notifyDataSetChanged()
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = Intent().apply {
            setClassName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(launchIntent)
    }
}
