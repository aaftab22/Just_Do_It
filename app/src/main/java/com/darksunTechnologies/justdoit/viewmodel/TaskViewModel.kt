package com.darksunTechnologies.justdoit.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
import com.darksunTechnologies.justdoit.models.Task
import com.google.gson.Gson
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.darksunTechnologies.justdoit.models.TaskKey
import com.google.gson.reflect.TypeToken

class TaskViewModel(application: Application): AndroidViewModel(application) {

    sealed class BackupResult {
        data class Success(val message: String) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    private val dao = AppDatabase.getInstance(application).taskDao()
    private val repository = TaskRepository(dao)
    private var recentlyDeletedTask: Task? = null
    private var recentlyDeletedTasks: List<Task>? = null
    val tasks: LiveData<List<Task>> = repository.getAllTasks().asLiveData()

    private val _backupResult = MutableLiveData<BackupResult>()
    val backupResult: LiveData<BackupResult> = _backupResult

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

    fun backupToUri(context: Context, uri: Uri) = viewModelScope.launch {
        try {
            val list = repository.getAllTasksOnce()
            val json = Gson().toJson(list)

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray())
            }

            _backupResult.postValue(
                BackupResult.Success("Backup saved (${list.size} tasks)")
            )
        } catch (e: Exception) {
            _backupResult.postValue(
                BackupResult.Error("Backup failed: ${e.message}")
            )
        }
    }

    fun restoreFromUri(context: Context, uri: Uri) = viewModelScope.launch {
        try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() } ?: return@launch

            val type = object : TypeToken<List<Task>>() {}.type

            val restoredTasks = Gson().fromJson<List<Task>>(json, type)
                ?: throw IllegalStateException("Backup file is empty or invalid")

            if (restoredTasks.isEmpty()) {
                throw IllegalStateException("Backup file contains no tasks")
            }

            val existingKeys = repository.getTaskKeys().toSet()
            var insertedCount = 0

            restoredTasks.forEach { task ->
                val key = TaskKey(task.name, task.isHighPriority)
                if (!existingKeys.contains(key)) {
                    repository.insertTask(Task(name = task.name, isHighPriority = task.isHighPriority))
                    insertedCount++
                }
            }

            _backupResult.postValue(
                BackupResult.Success("Restored $insertedCount new tasks (skipped duplicates)")
            )
        } catch (e: Exception) {
            _backupResult.postValue(
                BackupResult.Error("Restore failed: ${e.message}")
            )
        }
    }

    fun migrateFromSharedPrefsIfNeeded(context: Context) = viewModelScope.launch {
        val prefs = context.getSharedPreferences("Tasks", Context.MODE_PRIVATE)
        val json = prefs.getString("taskList", null) ?: return@launch

        val type = object : TypeToken<List<Task>>() {}.type
        val oldTasks: List<Task> = Gson().fromJson(json, type)

        if (oldTasks.isNotEmpty() && repository.countTasks() == 0) {
            oldTasks.forEach {
                repository.insertTask(Task(name = it.name, isHighPriority = it.isHighPriority))
            }
            prefs.edit { remove("taskList") }
        }
    }
}