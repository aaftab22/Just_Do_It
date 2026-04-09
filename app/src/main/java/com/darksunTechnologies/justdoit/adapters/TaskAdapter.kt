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

        // Due date display + overdue highlighting
        val llDueDate = holder.itemView.findViewById<android.widget.LinearLayout>(R.id.llTaskDueDate)
        val dueDateTV = holder.itemView.findViewById<TextView>(R.id.tvTaskDueDate)
        val dueDateIcon = holder.itemView.findViewById<ImageView>(R.id.ivTaskDueDateIcon)

        if (currTask.dueDate != null) {
            val formatted = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                .format(java.util.Date(currTask.dueDate))
            dueDateTV.text = "Due $formatted"
            llDueDate.visibility = View.VISIBLE

            val isOverdue = currTask.dueDate < System.currentTimeMillis() && !currTask.isCompleted
            val context = holder.itemView.context
            if (isOverdue) {
                dueDateTV.setTextColor(context.getColor(R.color.overdue_red))
                dueDateIcon.setColorFilter(context.getColor(R.color.overdue_red))
                llDueDate.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.overdue_red_soft))
            } else {
                dueDateTV.setTextColor(context.getColor(R.color.text_sec))
                dueDateIcon.setColorFilter(context.getColor(R.color.text_sec))
                llDueDate.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.grey_light))
            }
        } else {
            llDueDate.visibility = View.GONE
        }

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