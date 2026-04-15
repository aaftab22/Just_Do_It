package com.darksunTechnologies.justdoit.database

import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.models.RepeatType
import com.darksunTechnologies.justdoit.notifications.RecurringManager
import kotlinx.coroutines.flow.Flow

class TaskRepository (private val dao: TaskDao) {

    fun getAllTasks(): Flow<List<Task>> = dao.getAllTasks()

    suspend fun insertTask(task: Task): Long = dao.insertTask(task)

    suspend fun updateTask(task: Task) = dao.updateTask(task)

    suspend fun handleTaskCompletion(task: Task): Task? {
        // 1. Mark current task as done in DB
        dao.updateTask(task.copy(isCompleted = true))

        // 2. Spawning logic
        if (task.repeatType != RepeatType.NONE && task.dueDate != null) {
            val nextDate = RecurringManager.calculateNextDueDate(task.dueDate, task.repeatType)
            
            // Create the new instance
            val nextTask = task.copy(
                id = 0,               // New row for DB
                isCompleted = false,  // Reset status
                dueDate = nextDate,   // New calculated date
                source = "recurring"  // Track source for debugging/analytics
            )

            // Save to DB and return the full object with its NEW ID
            val newId = dao.insertTask(nextTask)
            return nextTask.copy(id = newId.toInt())
        }
        
        // Return null if no new task was spawned
        return null
    }

    suspend fun deleteTask(task: Task) = dao.deleteTask(task)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllTasksOnce(): List<Task> = dao.getAllTasksOnce()

    suspend fun countTasks(): Int = dao.countTasks()

    suspend fun getTaskKeys() = dao.getTaskKeys()

    suspend fun searchTasks(query: String) = dao.searchTasks(query)
}