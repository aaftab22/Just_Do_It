package com.darksunTechnologies.justdoit.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.TaskDetailActivity
import com.darksunTechnologies.justdoit.R
import com.darksunTechnologies.justdoit.models.Task

class TaskAdapter(
    private val onTaskClick: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DIFF_CALLBACK) {
    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_item_layout, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currTask = getItem(position)

        val taskTV = holder.itemView.findViewById<TextView>(R.id.task_name_TV)
        val highPriorityIcon = holder.itemView.findViewById<ImageView>(R.id.highPriority_Icon)

        taskTV.text = currTask.name
        
        // Apply strikethrough and fade if completed
        if (currTask.isCompleted) {
            taskTV.paintFlags = taskTV.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            holder.itemView.alpha = 0.6f
        } else {
            taskTV.paintFlags = taskTV.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.itemView.alpha = 1.0f
        }

        highPriorityIcon.visibility =
            if (currTask.isHighPriority) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onTaskClick(currTask)
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem == newItem
            }
        }
    }
}