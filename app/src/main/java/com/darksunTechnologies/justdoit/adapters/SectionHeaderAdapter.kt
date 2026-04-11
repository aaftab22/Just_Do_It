package com.darksunTechnologies.justdoit.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.R

class SectionHeaderAdapter(
    private val title: String,
    private var count: Int,
    private var isExpanded: Boolean = true,
    private val onToggle: (Boolean) -> Unit
) : RecyclerView.Adapter<SectionHeaderAdapter.HeaderVH>() {

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvSectionTitle)
        val tvCount: TextView = itemView.findViewById(R.id.tvSectionCount)
        val ivArrow: ImageView = itemView.findViewById(R.id.ivExpandArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.section_header_layout, parent, false)
        return HeaderVH(view)
    }

    override fun onBindViewHolder(holder: HeaderVH, position: Int) {
        holder.tvTitle.text = title
        holder.tvCount.text = "$count"
        holder.ivArrow.rotation = if (isExpanded) 0f else -90f

        holder.itemView.setOnClickListener {
            isExpanded = !isExpanded
            holder.ivArrow.animate().rotation(if (isExpanded) 0f else -90f).setDuration(200).start()
            onToggle(isExpanded)
        }
    }

    override fun getItemCount(): Int = 1

    fun updateCount(newCount: Int) {
        if (this.count != newCount) {
            this.count = newCount
            notifyItemChanged(0)
        }
    }
}
