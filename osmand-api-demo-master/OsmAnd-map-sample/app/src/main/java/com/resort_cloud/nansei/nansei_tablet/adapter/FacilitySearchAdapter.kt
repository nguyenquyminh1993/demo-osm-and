package com.resort_cloud.nansei.nansei_tablet.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.resort_cloud.nansei.nansei_tablet.R
import com.resort_cloud.nansei.nansei_tablet.utils.FacilityItem

/**
 * Adapter cho RecyclerView hiển thị danh sách facilities
 * Tương tự Flutter ListView.builder với category titles và facilities
 */
class FacilitySearchAdapter(
    private val onFacilityClick: (FacilityItem.FacilityData) -> Unit
) : ListAdapter<FacilityItem, RecyclerView.ViewHolder>(FacilityDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FacilityItem.CategoryTitle -> VIEW_TYPE_CATEGORY
            is FacilityItem.FacilityData -> VIEW_TYPE_FACILITY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_facility_category, parent, false)
                CategoryViewHolder(view)
            }
            VIEW_TYPE_FACILITY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_facility, parent, false)
                FacilityViewHolder(view, onFacilityClick)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FacilityItem.CategoryTitle -> {
                (holder as CategoryViewHolder).bind(item)
            }
            is FacilityItem.FacilityData -> {
                (holder as FacilityViewHolder).bind(item, position)
            }
        }
    }

    /**
     * ViewHolder cho category title
     */
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryName: TextView = itemView.findViewById(R.id.tv_category_name)

        fun bind(item: FacilityItem.CategoryTitle) {
            categoryName.text = item.name
        }
    }

    /**
     * ViewHolder cho facility item
     */
    inner class FacilityViewHolder(
        itemView: View,
        private val onFacilityClick: (FacilityItem.FacilityData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val facilityName: TextView = itemView.findViewById(R.id.tv_facility_name)
        private val divider: View = itemView.findViewById(R.id.divider)

        fun bind(item: FacilityItem.FacilityData, position: Int) {
            facilityName.text = item.name
            
            // Ẩn divider nếu item tiếp theo là category title
            val nextItem = if (position < itemCount - 1) {
                getItem(position + 1)
            } else {
                null
            }
            divider.visibility = if (nextItem is FacilityItem.CategoryTitle) {
                View.GONE
            } else {
                View.VISIBLE
            }

            itemView.setOnClickListener {
                onFacilityClick(item)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_FACILITY = 1
    }
}

/**
 * DiffUtil callback để optimize RecyclerView updates
 */
class FacilityDiffCallback : DiffUtil.ItemCallback<FacilityItem>() {
    override fun areItemsTheSame(oldItem: FacilityItem, newItem: FacilityItem): Boolean {
        return when {
            oldItem is FacilityItem.CategoryTitle && newItem is FacilityItem.CategoryTitle ->
                oldItem.name == newItem.name
            oldItem is FacilityItem.FacilityData && newItem is FacilityItem.FacilityData ->
                oldItem.facilityId == newItem.facilityId
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: FacilityItem, newItem: FacilityItem): Boolean {
        return oldItem == newItem
    }
}

