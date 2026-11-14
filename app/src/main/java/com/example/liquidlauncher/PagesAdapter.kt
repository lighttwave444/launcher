package com.example.liquidlauncher

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Cada item é uma PÁGINA da gaveta (grade de apps); passa-se de página arrastando para o lado. */
class PagesAdapter(
    private val unit: Float,
    private val columns: Int,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<PagesAdapter.PageViewHolder>() {

    private var pages: List<List<AppInfo>> = emptyList()

    class PageViewHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

    fun submit(newPages: List<List<AppInfo>>) {
        pages = newPages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val ll = LinearLayout(parent.context)
        ll.orientation = LinearLayout.VERTICAL
        ll.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        ll.setPadding((4f * unit).toInt(), (8f * unit).toInt(), (4f * unit).toInt(), 0)
        return PageViewHolder(ll)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val ctx = holder.root.context
        val inflater = LayoutInflater.from(ctx)
        holder.root.removeAllViews()
        val apps = pages[position]

        var i = 0
        while (i < apps.size) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            holder.root.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            for (col in 0 until columns) {
                val idx = i + col
                if (idx < apps.size) {
                    val app = apps[idx]
                    val cell = inflater.inflate(R.layout.item_app, row, false)
                    // tamanhos em "dp virtuais" (imunes ao DPI do sistema)
                    val iv = cell.findViewById<ImageView>(R.id.app_icon)
                    iv.layoutParams.width = (72f * unit).toInt()
                    iv.layoutParams.height = (72f * unit).toInt()
                    iv.setImageDrawable(app.icon)
                    val tv = cell.findViewById<TextView>(R.id.app_label)
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f * unit)
                    (tv.layoutParams as LinearLayout.LayoutParams).topMargin = (6f * unit).toInt()
                    tv.text = app.label
                    cell.setPadding(0, (8f * unit).toInt(), 0, (8f * unit).toInt())
                    cell.setOnClickListener { onClick(app) }
                    cell.setOnLongClickListener { onLongClick(app); true }
                    row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                } else {
                    // célula vazia só para manter as colunas alinhadas
                    row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
            i += columns
        }
    }

    override fun getItemCount(): Int = pages.size
}
