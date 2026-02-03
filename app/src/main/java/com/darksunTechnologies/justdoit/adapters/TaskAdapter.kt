package com.darksunTechnologies.justdoit.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.darksunTechnologies.justdoit.R
import com.darksunTechnologies.justdoit.models.Task

class TaskAdapter (private var yourListData: List<Task>, var deleteFunctionFromMain: (Int)->Unit ): RecyclerView.Adapter<TaskAdapter.TaskViewHolder>()
{
    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder (itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.task_item_layout, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int {
        return yourListData.size
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val currTask: Task = yourListData[position]

        // 1. Get the TextView from the row layout
        val taskTV = holder.itemView.findViewById<TextView>(R.id.task_name_TV)
        val highPriorityIcon = holder.itemView.findViewById<ImageView>(R.id.highPriority_Icon)

        // 2. Set its value
        taskTV.text = currTask.name

        if (currTask.isHighPriority) {
            highPriorityIcon.visibility = View.VISIBLE
        }
        // 3. attach a click handler to a button
        val imageView = holder.itemView.findViewById<ImageView>(R.id.delete_IV)
        imageView.setOnClickListener {
            deleteFunctionFromMain(position)
        }
    }

    fun updateList(newList: List<Task>) {
        yourListData = newList
        notifyDataSetChanged()
    }

}