package com.darksunTechnologies.justdoit.viewmodel

import androidx.lifecycle.ViewModel
import com.darksunTechnologies.justdoit.models.Task

class TaskViewModel : ViewModel() {
    val taskList: MutableList<Task> = mutableListOf(
        Task("Start with your first task from VM", false),
        Task("Is it high priority task? turn of the switch on then from VM", true)
    )

    fun addTask(task: Task){
        taskList.add(task)
    }

    fun removeTaskAt(index: Int){
        taskList.removeAt(index)
    }

    fun clearAll(){
        taskList.clear()
    }

}