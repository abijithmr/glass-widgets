package com.glasswidgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.ChipGroup

/**
 * The whole first screen as one list: a header (live stage, look controls, category
 * filter), one card per widget, and a footer hint.
 */
class WidgetAdapter(
    private val all: List<WidgetEntry>,
    private val onLookChanged: () -> Unit,
    private val onAdd: (WidgetEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var filter: Category? = null
    private var visible: List<WidgetEntry> = all

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stage: View = view.findViewById(R.id.stage)
        val stageSub: TextView = view.findViewById(R.id.stage_sub)
        val stagePreview: FrameLayout = view.findViewById(R.id.stage_preview)
        val styleGroup: MaterialButtonToggleGroup = view.findViewById(R.id.style_group)
        val sceneRow: LinearLayout = view.findViewById(R.id.scene_row)
        val sceneName: TextView = view.findViewById(R.id.scene_name)
        val accentRow: LinearLayout = view.findViewById(R.id.accent_row)
        val accentName: TextView = view.findViewById(R.id.accent_name)
        val chips: ChipGroup = view.findViewById(R.id.chip_group)
    }

    private class FooterHolder(view: View) : RecyclerView.ViewHolder(view)

    private class WidgetHolder(view: View) : RecyclerView.ViewHolder(view) {
        val previewHolder: View = view.findViewById(R.id.preview_holder)
        val container: FrameLayout = view.findViewById(R.id.preview_container)
        val title: TextView = view.findViewById(R.id.widget_title)
        val size: TextView = view.findViewById(R.id.widget_size)
        val description: TextView = view.findViewById(R.id.widget_desc)
        val add: MaterialButton = view.findViewById(R.id.btn_add)
        val secondary: MaterialButton = view.findViewById(R.id.btn_secondary)
    }

    override fun getItemViewType(position: Int): Int = when (position) {
        0 -> TYPE_HEADER
        visible.size + 1 -> TYPE_FOOTER
        else -> TYPE_WIDGET
    }

    override fun getItemCount(): Int = visible.size + 2

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
            is WidgetHolder -> bindWidget(holder, visible[position - 1])
            else -> Unit
        }
    }

    /** Redraw the header and every card after the look changed, or the home screen count did. */
    fun refreshLook() {
        notifyItemChanged(0)
        notifyItemRangeChanged(1, visible.size)
    }

    /** Re-read how many widgets are on the home screen. */
    fun refreshHeader() {
        notifyItemChanged(0)
    }

    private fun setFilter(category: Category?) {
        if (category == filter) return
        val oldSize = visible.size
        filter = category
        visible = if (category == null) all else all.filter { it.category == category }
        val newSize = visible.size
        notifyItemRangeChanged(1, minOf(oldSize, newSize))
        when {
            newSize < oldSize -> notifyItemRangeRemoved(1 + newSize, oldSize - newSize)
            newSize > oldSize -> notifyItemRangeInserted(1 + oldSize, newSize - oldSize)
        }
    }

    // ---------------------------------------------------------------- header

    private fun bindHeader(h: HeaderHolder) {
        val context = h.itemView.context
        val density = context.resources.displayMetrics.density
        val scene = Glass.scene(context)

        // Stage
        h.stage.background = Look.sceneDrawable(scene, 28f * density)
        val placed = Glass.providers.count { Glass.hasInstances(context, it) }
        h.stageSub.text = if (placed == 0) {
            context.getString(R.string.stage_empty)
        } else {
            "$placed of ${all.size} widgets on your home screen"
        }
        h.stagePreview.removeAllViews()
        try {
            val appContext = context.applicationContext
            h.stagePreview.addView(
                GlassDigitalClockWidget.create(appContext).apply(appContext, h.stagePreview)
            )
        } catch (e: Exception) {
            Log.e("GlassWidgets", "Stage preview failed", e)
        }

        // Glass style
        h.styleGroup.clearOnButtonCheckedListeners()
        h.styleGroup.check(
            when (Glass.style(context)) {
                Glass.STYLE_SMOKED -> R.id.btn_smoked
                Glass.STYLE_CLEAR -> R.id.btn_clear
                else -> R.id.btn_frosted
            }
        )
        h.styleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            Glass.setStyle(
                context,
                when (checkedId) {
                    R.id.btn_smoked -> Glass.STYLE_SMOKED
                    R.id.btn_clear -> Glass.STYLE_CLEAR
                    else -> Glass.STYLE_FROSTED
                }
            )
            lookChanged()
        }

        // Preview backdrop
        h.sceneName.text = scene.name
        fillSwatches(
            row = h.sceneRow,
            count = Look.scenes.size,
            selected = Glass.sceneIndex(context),
            nameOf = { Look.scenes[it].name },
            fill = { i ->
                GradientDrawable(GradientDrawable.Orientation.TL_BR, Look.scenes[i].colors)
                    .apply { shape = GradientDrawable.OVAL }
            },
            onPick = { i ->
                Glass.setScene(context, i)
                lookChanged()
            }
        )

        // Today highlight colour
        h.accentName.text = Glass.accent(context).name
        fillSwatches(
            row = h.accentRow,
            count = Look.accents.size,
            selected = Glass.accentIndex(context),
            nameOf = { Look.accents[it].name },
            fill = { i ->
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Look.accents[i].color)
                }
            },
            onPick = { i ->
                Glass.setAccent(context, i)
                lookChanged()
            }
        )

        // Category filter
        h.chips.setOnCheckedStateChangeListener(null)
        h.chips.check(chipIdFor(filter))
        h.chips.setOnCheckedStateChangeListener { _, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            setFilter(
                when (id) {
                    R.id.chip_clocks -> Category.CLOCKS
                    R.id.chip_weather -> Category.WEATHER
                    R.id.chip_calendar -> Category.CALENDAR
                    R.id.chip_controls -> Category.CONTROLS
                    else -> null
                }
            )
        }
    }

    private fun chipIdFor(category: Category?): Int = when (category) {
        Category.CLOCKS -> R.id.chip_clocks
        Category.WEATHER -> R.id.chip_weather
        Category.CALENDAR -> R.id.chip_calendar
        Category.CONTROLS -> R.id.chip_controls
        null -> R.id.chip_all
    }

    private fun lookChanged() {
        refreshLook()
        onLookChanged()
    }

    /** A row of round colour choices with a ring around the selected one. */
    private fun fillSwatches(
        row: LinearLayout,
        count: Int,
        selected: Int,
        nameOf: (Int) -> String,
        fill: (Int) -> Drawable,
        onPick: (Int) -> Unit
    ) {
        val context = row.context
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        row.removeAllViews()
        for (i in 0 until count) {
            val ring = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    marginEnd = dp(10)
                }
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    if (i == selected) setStroke(dp(2), 0xFFEAF2F5.toInt())
                }
                contentDescription = nameOf(i)
                isClickable = true
                isFocusable = true
                setOnClickListener { onPick(i) }
            }
            val dot = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                background = fill(i)
            }
            ring.addView(dot)
            row.addView(ring)
        }
    }

    // ---------------------------------------------------------------- cards

    private fun bindWidget(holder: WidgetHolder, entry: WidgetEntry) {
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        holder.previewHolder.background = Look.sceneDrawable(Glass.scene(context), 16f * density)
        holder.title.text = entry.title
        holder.size.text = entry.sizeLabel
        holder.description.text = entry.description
        holder.add.setOnClickListener { onAdd(entry) }

        val secondaryLabel = entry.secondaryLabel
        val onSecondary = entry.onSecondary
        if (secondaryLabel != null && onSecondary != null) {
            holder.secondary.visibility = View.VISIBLE
            holder.secondary.text = secondaryLabel
            holder.secondary.setOnClickListener { onSecondary.invoke(context) }
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
            // The application context has no AppCompat view factory; an activity does, and it
            // swaps TextView for AppCompatTextView, which RemoteViews can't restyle at run time.
            val appContext = context.applicationContext
            holder.container.addView(entry.preview(appContext).apply(appContext, holder.container))
        } catch (e: Exception) {
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
