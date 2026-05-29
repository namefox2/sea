package com.koretide.app.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.koretide.app.R
import com.koretide.app.theme.ThemeConfig

class ThemeCardAdapter(
    private val themes: List<ThemeConfig>,
    private var selectedId: String,
    private val onThemeSelected: (ThemeConfig) -> Unit
) : RecyclerView.Adapter<ThemeCardAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView      = view.findViewById(R.id.cardTheme)
        val preview: View       = view.findViewById(R.id.viewThemePreview)
        val tvName: TextView    = view.findViewById(R.id.tvThemeName)
        val ivSelected: View    = view.findViewById(R.id.ivSelected)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_theme_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val theme = themes[position]
        holder.tvName.text = theme.displayName

        // Sky-to-sea gradient preview
        val grad = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(theme.skyTopColor, theme.skyBottomColor, theme.seaTopColor, theme.seaBottomColor)
        )
        holder.preview.background = grad

        holder.ivSelected.visibility =
            if (theme.id == selectedId) View.VISIBLE else View.GONE

        val strokeW = if (theme.id == selectedId) 3 else 0
        holder.card.cardElevation = if (theme.id == selectedId) 8f else 2f

        holder.card.setOnClickListener {
            val prev = selectedId
            selectedId = theme.id
            onThemeSelected(theme)
            notifyItemChanged(themes.indexOfFirst { it.id == prev })
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = themes.size
}
