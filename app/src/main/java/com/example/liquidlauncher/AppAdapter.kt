package com.example.liquidlauncher

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val allApps: List<AppInfo>,
    private val unit: Float,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var shown: List<AppInfo> = allApps

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    fun filter(query: String) {
        val q = query.trim()
        shown = if (q.isEmpty()) allApps
        else allApps.filter { it.label.contains(q, ignoreCase = true) }
        notifyDataSetChanged()
    }

    fun refresh() {
        shown = allApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        // tamanhos em "dp virtuais" (largura da tela / 360): não mudam quando o DPI do sistema muda
        val iv = view.findViewById<ImageView>(R.id.app_icon)
        iv.layoutParams.width = (72f * unit).toInt()
        iv.layoutParams.height = (72f * unit).toInt()
        val tv = view.findViewById<TextView>(R.id.app_label)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f * unit)
        (tv.layoutParams as LinearLayout.LayoutParams).topMargin = (6f * unit).toInt()
        view.setPadding(0, (8f * unit).toInt(), 0, (8f * unit).toInt())
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = shown[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount(): Int = shown.size
}
