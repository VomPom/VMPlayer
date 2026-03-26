package com.vompom.vmplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vompom.vmplayer.R

/**
 * 功能操作按钮适配器
 * 用于将底部功能按钮（停止、导出、添加贴纸、清除贴纸等）以 RecyclerView 形式展示，
 * 方便未来扩展更多功能。
 */
class ActionAdapter(
    private val onActionClick: (ActionItem) -> Unit
) : RecyclerView.Adapter<ActionAdapter.ActionViewHolder>() {

    private val actions = mutableListOf<ActionItem>()

    fun updateActions(newActions: List<ActionItem>) {
        actions.clear()
        actions.addAll(newActions)
        notifyDataSetChanged()
    }

    /**
     * 动态添加一个功能项
     */
    fun addAction(action: ActionItem) {
        actions.add(action)
        notifyItemInserted(actions.size - 1)
    }

    /**
     * 根据 id 更新某个功能项的文本
     */
    fun updateActionText(id: String, newText: String) {
        val index = actions.indexOfFirst { it.id == id }
        if (index >= 0) {
            actions[index] = actions[index].copy(name = newText)
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        val action = actions[position]
        holder.bind(action)
        holder.itemView.setOnClickListener {
            onActionClick(action)
        }
    }

    override fun getItemCount(): Int = actions.size

    class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAction: TextView = itemView.findViewById(R.id.tv_action)

        fun bind(action: ActionItem) {
            tvAction.text = action.name
        }
    }
}

/**
 * 功能操作项数据类
 * @param id 唯一标识，用于区分不同功能
 * @param name 显示名称
 */
data class ActionItem(
    val id: String,
    val name: String
)