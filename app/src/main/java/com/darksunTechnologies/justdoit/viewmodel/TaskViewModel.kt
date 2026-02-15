package com.darksunTechnologies.justdoit.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
import com.darksunTechnologies.justdoit.models.Task
import kotlinx.coroutines.launch

class TaskViewModel(application: Application): AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).taskDao()
    private val repository = TaskRepository(dao)
    private var recentlyDeletedTask: Task? = null
    val tasks: LiveData<List<Task>> = repository.getAllTasks().asLiveData()

    fun addTask(task: Task) = viewModelScope.launch {
        repository.insertTask(task)
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        repository.deleteTask(task)
        recentlyDeletedTask = task
    }

    fun undoDelete() {
        recentlyDeletedTask?.let {
            viewModelScope.launch {
                repository.insertTask(it)
            }
        }
    }

    fun clearAll() = viewModelScope.launch {
        repository.deleteAll()
    }
}