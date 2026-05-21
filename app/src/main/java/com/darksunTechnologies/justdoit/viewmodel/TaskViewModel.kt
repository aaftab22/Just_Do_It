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
import com.darksunTechnologies.justdoit.alarms.AlarmHelper
import com.darksunTechnologies.justdoit.alarms.GeofenceManager
import com.google.gson.Gson
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.darksunTechnologies.justdoit.models.TaskKey
import com.google.gson.reflect.TypeToken
import androidx.lifecycle.switchMap

class TaskViewModel(application: Application): AndroidViewModel(application) {

    sealed class BackupResult {
        data class Success(val message: String) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    private val dao = AppDatabase.getInstance(application).taskDao()
    private val repository = TaskRepository(dao)
    private var recentlyDeletedTask: Task? = null
    private var recentlyDeletedTasks: List<Task>? = null
    private val _searchQuery = MutableLiveData<String>("")
    val tasks: LiveData<List<Task>> = _searchQuery.switchMap { query ->
        if (query.isNullOrBlank()) {
            repository.getAllTasks().asLiveData()
        } else {
            // Convert the search result list to a LiveData
            val liveData = MutableLiveData<List<Task>>()
            viewModelScope.launch {
                liveData.postValue(repository.searchTasks(query))
            }
            liveData
        }
    }


    private val _backupResult = MutableLiveData<BackupResult>()
    val backupResult: LiveData<BackupResult> = _backupResult

    // One-shot event: fires when a new task is saved, carries the new row ID
    private val _lastSavedTaskId = MutableLiveData<Long?>()
    val lastSavedTaskId: LiveData<Long?> = _lastSavedTaskId

    fun clearLastSavedTaskId() {
        _lastSavedTaskId.value = null
    }

    // One-shot event: fires when a task is deleted (from anywhere), so MainActivity shows UNDO
    private val _showUndoDelete = MutableLiveData<Boolean?>()
    val showUndoDelete: LiveData<Boolean?> = _showUndoDelete

    fun clearUndoDelete() {
        _showUndoDelete.value = null
    }

    fun addTask(task: Task) = viewModelScope.launch {
        val newId = repository.insertTask(task)
        _lastSavedTaskId.postValue(newId)
    }

    fun toggleComplete(task: Task) = viewModelScope.launch {
        // If we are completing it now:
        if (!task.isCompleted) {
            // ALWAYS cancel the old alarm and geofence
            AlarmHelper.cancelReminder(getApplication(), task.id)
            GeofenceManager.removeGeofence(getApplication(), task.id)
            
            // Let Repo handle DB duplication
            val newlySpawnedTask = repository.handleTaskCompletion(task)
            
            // If a new task was created and it needs a reminder, schedule it
            if (newlySpawnedTask != null) {
                if (newlySpawnedTask.hasReminder) {
                    AlarmHelper.scheduleReminder(getApplication(), newlySpawnedTask)
                }
                if (newlySpawnedTask.hasLocationReminder) {
                    GeofenceManager.addGeofence(getApplication(), newlySpawnedTask)
                }
            }
        } else {
            // Simple un-complete logic
            repository.updateTask(task.copy(isCompleted = false))
        }
    }

    fun updateTask(task: Task) = viewModelScope.launch {
        repository.updateTask(task)
    }

    fun acceptSuggestedTask(task: Task) = viewModelScope.launch {
        val acceptedTask = task.copy(needsReview = false)
        repository.updateTask(acceptedTask)
        
        if (acceptedTask.hasReminder) {
            AlarmHelper.scheduleReminder(getApplication(), acceptedTask)
        }
        if (acceptedTask.hasLocationReminder) {
            GeofenceManager.addGeofence(getApplication(), acceptedTask)
        }
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        repository.deleteTask(task)
        AlarmHelper.cancelReminder(getApplication(), task.id)
        GeofenceManager.removeGeofence(getApplication(), task.id)
        recentlyDeletedTask = task
        _showUndoDelete.postValue(true)
    }

    fun undoDelete() {
        recentlyDeletedTask?.let { task ->
            viewModelScope.launch {
                repository.insertTask(task)
                if (task.hasReminder) {
                    AlarmHelper.scheduleReminder(getApplication(), task)
                }
                if (task.hasLocationReminder) {
                    GeofenceManager.addGeofence(getApplication(), task)
                }
            }
        }
    }

    fun clearAll() = viewModelScope.launch {
        val listToClear = tasks.value.orEmpty()
        recentlyDeletedTasks = listToClear
        listToClear.forEach { task ->
            AlarmHelper.cancelReminder(getApplication(), task.id)
            GeofenceManager.removeGeofence(getApplication(), task.id)
        }
        repository.deleteAll()
    }

    fun undoDeleteAll() {
        recentlyDeletedTasks?.let { list ->
            viewModelScope.launch {
                list.forEach { task -> 
                    repository.insertTask(task)
                    if (task.hasReminder) {
                        AlarmHelper.scheduleReminder(getApplication(), task)
                    }
                    if (task.hasLocationReminder) {
                        GeofenceManager.addGeofence(getApplication(), task)
                    }
                }
            }
        }
    }

    fun backupToUri(context: Context, uri: Uri) = viewModelScope.launch {
        try {
            val list = repository.getAllTasksOnce()
            val json = Gson().toJson(list)

            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
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

    fun searchTasks(query: String) {
        _searchQuery.value = query
    }

    // ─── HYBRID BATCH ENGINE: Foreground Burst Processing ────────
    private val _isProcessingQueue = MutableLiveData<Boolean>(false)
    val isProcessingQueue: LiveData<Boolean> = _isProcessingQueue

    private val _processingProgress = MutableLiveData<Pair<Int, Int>>()
    val processingProgress: LiveData<Pair<Int, Int>> = _processingProgress

    fun processPendingQueue() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val db = AppDatabase.getInstance(getApplication())
        val queueDao = db.queuedMessageDao()
        val pending = queueDao.getPendingMessages()
        
        if (pending.isEmpty()) return@launch
        
        _isProcessingQueue.postValue(true)
        val total = pending.size
        _processingProgress.postValue(Pair(0, total))
        android.util.Log.d("TaskViewModel", "Foreground burst: Processing $total queued messages")
        
        for ((index, msg) in pending.withIndex()) {
            _processingProgress.postValue(Pair(index + 1, total))
            try {
                val parsedTasks = com.darksunTechnologies.justdoit.notifications.AiTaskExtractor.extract(msg.rawText)
                if (parsedTasks.isNotEmpty()) {
                    for (parsed in parsedTasks) {
                        // Deduplication
                        val existing = dao.getTaskByName(parsed.title)
                        if (existing != null) continue

                        val appName = when (msg.sourcePackage) {
                            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                            "org.telegram.messenger" -> "Telegram"
                            "com.google.android.apps.messaging" -> "Messages"
                            "com.google.android.gm" -> "Gmail"
                            "com.microsoft.office.outlook" -> "Outlook"
                            else -> "Notifications"
                        }
                        val desc = "Added from $appName" +
                            (if (msg.senderTitle.isNotBlank()) " (${msg.senderTitle})" else "") +
                            "\n[Parsed by: Offline AI 🤖]\n\n" +
                            "Original Message:\n\"${msg.rawText}\""

                        val task = com.darksunTechnologies.justdoit.models.Task(
                            name = parsed.title,
                            description = desc,
                            dueDate = parsed.dueDateMillis,
                            isHighPriority = parsed.isHighPriority,
                            source = parsed.parserSource,
                            needsReview = true,
                            hasReminder = parsed.dueDateMillis != null
                        )
                        repository.insertTask(task)
                    }
                }
                queueDao.updateStatus(msg.id, "PROCESSED")
                queueDao.deleteById(msg.id)
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Error processing queued msg ${msg.id}", e)
                queueDao.updateStatus(msg.id, "FAILED")
            }
        }
        
        // Dismiss the batch summary notification
        val manager = (getApplication() as android.app.Application)
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(999_999)
        
        _isProcessingQueue.postValue(false)
    }

    fun discardAllSuggestions() = viewModelScope.launch {
        val suggestions = tasks.value?.filter { it.needsReview } ?: return@launch
        suggestions.forEach { repository.deleteTask(it) }
    }
}