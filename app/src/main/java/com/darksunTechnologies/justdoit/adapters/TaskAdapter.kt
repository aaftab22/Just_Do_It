package com.darksunTechnologies.justdoit.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.R
import com.darksunTechnologies.justdoit.models.Task

class TaskAdapter( private val deleteFunctionFromMain: (Task) -> Unit ) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DIFF_CALLBACK) {

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
        highPriorityIcon.visibility =
            if (currTask.isHighPriority) View.VISIBLE else View.GONE

        holder.itemView.findViewById<ImageView>(R.id.delete_IV).setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                deleteFunctionFromMain(currTask)
            }
        }
    }
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem.name == newItem.name
            }

            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem == newItem
            }
        }
    }
}