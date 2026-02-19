package com.darksunTechnologies.justdoit.viewmodel

import android.app.Application
import android.content.Context
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
    private var recentlyDeletedTasks: List<Task>? = null
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
        recentlyDeletedTasks = tasks.value.orEmpty()
        repository.deleteAll()
    }

    fun undoDeleteAll() {
        recentlyDeletedTasks?.let { list ->
            viewModelScope.launch {
                list.forEach { repository.insertTask(it) }
            }
        }
    }

    fun migrateFromSharedPrefsIfNeeded(context: Context) = viewModelScope.launch {
        val prefs = context.getSharedPreferences("Tasks", Context.MODE_PRIVATE)
        val json = prefs.getString("taskList", null) ?: return@launch

        val type = object : com.google.gson.reflect.TypeToken<List<Task>>() {}.type
        val oldTasks: List<Task> = com.google.gson.Gson().fromJson(json, type)

        if (oldTasks.isNotEmpty() && repository.countTasks() == 0) {
            oldTasks.forEach {
                repository.insertTask(Task(name = it.name, isHighPriority = it.isHighPriority))
            }
            prefs.edit().remove("taskList").apply()
        }
    }

}