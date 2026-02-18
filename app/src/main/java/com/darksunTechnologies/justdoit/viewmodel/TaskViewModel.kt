package com.darksunTechnologies.justdoit.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
import com.darksunTechnologies.justdoit.models.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class TaskViewModel(application: Application): AndroidViewModel(application) {

//    sealed class BackupResult {
//        data class Success(val message: String) : BackupResult()
//        data class Error(val message: String) : BackupResult()
//    }

    private val dao = AppDatabase.getInstance(application).taskDao()
    private val repository = TaskRepository(dao)
    private var recentlyDeletedTask: Task? = null
    private var recentlyDeletedTasks: List<Task>? = null
    val tasks: LiveData<List<Task>> = repository.getAllTasks().asLiveData()

//    private val _backupResult = MutableLiveData<BackupResult>()
//    val backupResult: LiveData<BackupResult> = _backupResult

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

//    fun backupTasks(context: Context) = viewModelScope.launch {
//        val list = repository.getAllTasksOnce()
//        val json = exportTasksToJson(list)
//
//        try {
//            val file = java.io.File(context.getExternalFilesDir(null), "tasks_backup.json")
//            file.writeText(json)
//            _backupResult.postValue(BackupResult.Success("Backup saved successfully! (${list.size} tasks)"))
//        } catch (e: Exception) {
//            e.printStackTrace()
//            _backupResult.postValue(BackupResult.Error("Failed to save backup: ${e.message}"))
//        }
//
//    }

//    fun restoreTasks(context: Context) = viewModelScope.launch {
//        try {
//            val file = java.io.File(context.getExternalFilesDir(null), "tasks_backup.json")
//            if (file.exists()) {
//                val json = file.readText()
//                val tasksToRestore = importTasksFromJson(json)
//                tasksToRestore.forEach { task ->
//                    repository.insertTask(task)
//                }
//                _backupResult.postValue(BackupResult.Success("Restored ${tasksToRestore.size} tasks successfully!"))
//            } else {
//                _backupResult.postValue(BackupResult.Error("No backup file found"))
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            _backupResult.postValue(BackupResult.Error("Failed to restore backup: ${e.message}"))
//        }
//    }

//    fun exportTasksToJson(tasks: List<Task>): String {
//        return Gson().toJson(tasks)
//    }

//    fun importTasksFromJson(json: String): List<Task> {
//        val type = object : TypeToken<List<Task>>() {}.type
//        return Gson().fromJson(json, type)
//    }

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