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
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * The whole library screen as one list: a header with the style switch,
 * one card per widget (live preview + add button), and a footer hint.
 */
class WidgetAdapter(
    private val entries: List<WidgetEntry>,
    private val currentStyle: () -> Int,
    private val onStyleChange: (Int) -> Unit,
    private val onAdd: (WidgetEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.library_label)
        val group: MaterialButtonToggleGroup = view.findViewById(R.id.style_group)
    }

    private class FooterHolder(view: View) : RecyclerView.ViewHolder(view)

    private class WidgetHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: FrameLayout = view.findViewById(R.id.preview_container)
        val title: TextView = view.findViewById(R.id.widget_title)
        val size: TextView = view.findViewById(R.id.widget_size)
        val description: TextView = view.findViewById(R.id.widget_desc)
        val add: MaterialButton = view.findViewById(R.id.btn_add)
        val secondary: MaterialButton = view.findViewById(R.id.btn_secondary)
    }

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_HEADER
        entries.size + 1 -> TYPE_FOOTER
        else -> TYPE_WIDGET
    }

    override fun getItemCount(): Int = entries.size + 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_header, parent, false))
            TYPE_FOOTER -> FooterHolder(inflater.inflate(R.layout.item_footer, parent, false))
            else -> WidgetHolder(inflater.inflate(R.layout.item_widget, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderHolder -> bindHeader(holder)
            is WidgetHolder -> bindWidget(holder, entries[position - 1])
            else -> Unit
        }
    }

    private fun bindHeader(holder: HeaderHolder) {
        val context = holder.itemView.context
        holder.label.text = context.getString(R.string.library_title) + " · " + entries.size

        // Remove the old listener first so selecting the saved style doesn't fire it.
        holder.group.clearOnButtonCheckedListeners()
        holder.group.check(
            when (currentStyle()) {
                Glass.STYLE_SMOKED -> R.id.btn_smoked
                Glass.STYLE_CLEAR -> R.id.btn_clear
                else -> R.id.btn_frosted
            }
        )
        holder.group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            onStyleChange(
                when (checkedId) {
                    R.id.btn_smoked -> Glass.STYLE_SMOKED
                    R.id.btn_clear -> Glass.STYLE_CLEAR
                    else -> Glass.STYLE_FROSTED
                }
            )
        }
    }

    private fun bindWidget(holder: WidgetHolder, entry: WidgetEntry) {
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
            // The application context has no AppCompat view factory. An activity does, and it swaps
            // TextView for AppCompatTextView, which RemoteViews refuses to change at run time.
            val appContext = context.applicationContext
            holder.container.addView(entry.preview(appContext).apply(appContext, holder.container))
        } catch (e: Exception) {
            // A preview that can't draw must never crash the library, but say why.
            Log.e("GlassWidgets", "Preview failed for ${entry.title}", e)
            val cause = generateSequence<Throwable>(e) { it.cause }.last()
            holder.container.addView(TextView(context).apply {
                text = "Preview unavailable\n${cause.javaClass.simpleName}: ${cause.message}"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
            })
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_WIDGET = 1
        const val TYPE_FOOTER = 2
    }
}
