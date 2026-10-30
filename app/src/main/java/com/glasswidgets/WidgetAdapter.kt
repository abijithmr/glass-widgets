package com.glasswidgets

import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/** Shows each widget as a card with a live preview and an add button. */
class WidgetAdapter(
    private val entries: List<WidgetEntry>,
    private val onAdd: (WidgetEntry) -> Unit
) : RecyclerView.Adapter<WidgetAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view.findViewById(R.id.preview_container)
        val title: TextView = view.findViewById(R.id.widget_title)
        val size: TextView = view.findViewById(R.id.widget_size)
        val description: TextView = view.findViewById(R.id.widget_desc)
        val add: MaterialButton = view.findViewById(R.id.btn_add)
        val secondary: MaterialButton = view.findViewById(R.id.btn_secondary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_widget, parent, false))

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        holder.title.text = entry.title
        holder.size.text = entry.sizeLabel
        holder.description.text = entry.description
        holder.add.setOnClickListener { onAdd(entry) }

        if (entry.secondaryLabel != null && entry.onSecondary != null) {
            holder.secondary.visibility = View.VISIBLE
            holder.secondary.text = entry.secondaryLabel
            holder.secondary.setOnClickListener { entry.onSecondary.invoke(context) }
        } else {
            holder.secondary.visibility = View.GONE
        }

        // Real widget layout, drawn at the widget's own size
        val width = entry.previewWidthDp?.let { (it * density).toInt() }
            ?: ViewGroup.LayoutParams.MATCH_PARENT
        holder.container.layoutParams =
            FrameLayout.LayoutParams(width, (entry.previewHeightDp * density).toInt(), Gravity.CENTER)
        holder.container.removeAllViews()
        try {
            holder.container.addView(entry.preview(context).apply(context, holder.container))
        } catch (e: Exception) {
            // A preview that can't draw must never crash the library, but say why.
            Log.e("GlassWidgets", "Preview failed for ${entry.title}", e)
            holder.container.addView(TextView(context).apply {
                text = "Preview unavailable: ${e.javaClass.simpleName}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
            })
        }
    }
}
