package com.vompom.vmplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vompom.media.model.VideoEffectEntity
import com.vompom.vmplayer.R

/**
 * 特效适配器
 */
class EffectAdapter(
    private val onEffectSelected: (VideoEffectEntity) -> Unit,
    private val onEffectRemoved: (VideoEffectEntity) -> Unit
) : RecyclerView.Adapter<EffectAdapter.EffectViewHolder>() {
    private var effects: List<VideoEffectEntity> = emptyList()
    private val selectedPositions = mutableSetOf<Int>(0)    // 默认选中第一个（无特效时)

    fun updateEffects(newEffects: List<VideoEffectEntity>) {
        effects = newEffects
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_effect, parent, false)
        return EffectViewHolder(view)
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        val effect = effects[position]
        holder.bind(effect, selectedPositions.contains(position))

        holder.itemView.setOnClickListener {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
                onEffectRemoved(effect)
            } else {
                selectedPositions.add(position)
                onEffectSelected(effect)
            }

            holder.itemView.isSelected = selectedPositions.contains(position)
        }
    }

    override fun getItemCount(): Int = effects.size

    /**
     * 获取所有选中的特效
     */
    fun getSelectedEffects(): List<VideoEffectEntity> {
        return selectedPositions.map { effects[it] }
    }

    /**
     * 清除所有选中
     */
    fun clearSelections() {
        val oldSelected = selectedPositions.toList()
        selectedPositions.clear()
        oldSelected.forEach { notifyItemChanged(it) }
    }

    /**
     * 选择指定位置的特效
     */
    fun selectPosition(position: Int) {
        if (!selectedPositions.contains(position)) {
            selectedPositions.add(position)
            notifyItemChanged(position)
        }
    }

    /**
     * 取消选择指定位置的特效
     */
    fun deselectPosition(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            notifyItemChanged(position)
        }
    }

    /**
     * ViewHolder类
     */
    class EffectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEffect: TextView = itemView.findViewById(R.id.tv_effect)

        fun bind(effect: VideoEffectEntity, isSelected: Boolean) {
            tvEffect.text = effect.name
            itemView.isSelected = isSelected
        }
    }
}