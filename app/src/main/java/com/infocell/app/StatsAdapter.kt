package com.infocell.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class StatItem(
    val label: String,
    val value: String,
    val type: Int = TYPE_NORMAL
) {
    companion object {
        const val TYPE_NORMAL = 0
        const val TYPE_HEADER = 1
        const val TYPE_GOOD   = 2
        const val TYPE_WARN   = 3
    }
}

class StatsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<StatItem>()

    fun setItems(newItems: List<StatItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == StatItem.TYPE_HEADER) {
            val v = inflater.inflate(R.layout.item_header, parent, false)
            HeaderVH(v)
        } else {
            val v = inflater.inflate(R.layout.item_stat, parent, false)
            StatVH(v)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is HeaderVH -> holder.title.text = item.value
            is StatVH -> {
                holder.label.text = item.label
                holder.value.text = item.value
                val color = when (item.type) {
                    StatItem.TYPE_GOOD -> 0xFF4CAF50.toInt()
                    StatItem.TYPE_WARN -> 0xFFFF9800.toInt()
                    else               -> 0xFF00E5FF.toInt()
                }
                holder.value.setTextColor(color)
            }
        }
    }

    override fun getItemCount() = items.size

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvHeader)
    }

    class StatVH(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tvLabel)
        val value: TextView = view.findViewById(R.id.tvValue)
    }
}
