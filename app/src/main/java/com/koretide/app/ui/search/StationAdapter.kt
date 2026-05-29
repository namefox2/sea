package com.koretide.app.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.koretide.app.R
import com.koretide.app.databinding.ItemStationBinding
import com.koretide.app.domain.model.Station
import com.koretide.app.domain.model.TideStatus

class StationAdapter(
    private val onStationClick: (Station) -> Unit
) : ListAdapter<StationAdapter.StationItem, StationAdapter.ViewHolder>(DIFF) {

    data class StationItem(
        val station: Station,
        val tidePercent: Float? = null,
        val tideStatus: TideStatus? = null,
        val windBft: Int? = null
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemStationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StationItem) {
            val station = item.station
            binding.tvStationName.text = station.name
            binding.tvStationCode.text = station.code
            binding.tvRegion.text = station.region.displayName

            if (item.tidePercent != null) {
                binding.tvTidePercent.text = "${(item.tidePercent * 100).toInt()}%"
                binding.progressTide.progress = (item.tidePercent * 100).toInt()
            } else {
                binding.tvTidePercent.text = "--"
                binding.progressTide.progress = 0
            }

            if (item.tideStatus != null) {
                binding.tvTideStatus.visibility = android.view.View.VISIBLE
                binding.tvTideStatus.text = item.tideStatus.displayName
                val color = when (item.tideStatus) {
                    TideStatus.RISING, TideStatus.HIGH_TIDE ->
                        ContextCompat.getColor(binding.root.context, R.color.status_rising)
                    else ->
                        ContextCompat.getColor(binding.root.context, R.color.status_falling)
                }
                binding.tvTideStatus.setTextColor(color)
            } else {
                binding.tvTideStatus.visibility = android.view.View.GONE
            }

            if (item.windBft != null) {
                binding.tvWind.visibility = android.view.View.VISIBLE
                binding.tvWind.text = "💨 ${item.windBft}bft"
            } else {
                binding.tvWind.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onStationClick(station) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<StationItem>() {
            override fun areItemsTheSame(a: StationItem, b: StationItem) =
                a.station.code == b.station.code
            override fun areContentsTheSame(a: StationItem, b: StationItem) = a == b
        }
    }
}
