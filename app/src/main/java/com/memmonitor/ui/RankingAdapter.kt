package com.memmonitor.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.memmonitor.R
import com.memmonitor.core.AppMemRepository
import com.memmonitor.util.Format

class RankingAdapter : RecyclerView.Adapter<RankingAdapter.VH>() {

    private var items: List<AppMemRepository.AppMem> = emptyList()
    private var maxPss: Long = 1L

    fun submit(list: List<AppMemRepository.AppMem>) {
        items = list
        maxPss = list.maxOfOrNull { it.pssKb }?.coerceAtLeast(1L) ?: 1L
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val rank: TextView = v.findViewById(R.id.tv_rank)
        val label: TextView = v.findViewById(R.id.tv_label)
        val process: TextView = v.findViewById(R.id.tv_process)
        val pss: TextView = v.findViewById(R.id.tv_pss)
        val bar: View = v.findViewById(R.id.view_bar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_mem, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.rank.text = (position + 1).toString()
        holder.label.text = a.label
        holder.process.text = "${a.processName} · pid ${a.pid}"
        holder.pss.text = Format.kb(a.pssKb)
        // 条形图宽度按最大 PSS 归一化
        holder.bar.post {
            val parentWidth = (holder.bar.parent as View).width
            val target = (parentWidth * (a.pssKb.toFloat() / maxPss)).toInt().coerceAtLeast(4)
            holder.bar.layoutParams = holder.bar.layoutParams.apply { width = target }
        }
    }
}
