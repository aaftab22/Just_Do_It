package com.darksunTechnologies.justdoit.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.darksunTechnologies.justdoit.models.Task

class TaskViewModel : ViewModel() {

    private val _tasks = MutableLiveData(
        listOf(
            Task("Start with your first task from VM", false),
            Task("Is it high priority task? turn of the switch on then from VM", true)
        )
    )

    fun setTasks(tasks: List<Task>) {
        _tasks.value = tasks
    }

    fun getCurrentTasks(): List<Task> {
        return _tasks.value.orEmpty()
    }

    val tasks: LiveData<List<Task>> = _tasks
    fun addTask(task: Task){
        val updatedList = _tasks.value.orEmpty().toMutableList()
        updatedList.add(task)
        _tasks.value = updatedList
    }
    fun removeTaskAt(index: Int){
        val updatedList = _tasks.value.orEmpty().toMutableList()
        updatedList.removeAt(index)
        _tasks.value = updatedList
    }
    fun clearAll(){
        _tasks.value = emptyList()
    }
}