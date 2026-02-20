package com.darksunTechnologies.justdoit.database

import com.darksunTechnologies.justdoit.models.Task
import kotlinx.coroutines.flow.Flow

class TaskRepository (private val dao: TaskDao) {

    fun getAllTasks(): Flow<List<Task>> = dao.getAllTasks()

    suspend fun insertTask(task: Task) = dao.insertTask(task)

    suspend fun deleteTask(task: Task) = dao.deleteTask(task)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getAllTasksOnce(): List<Task> = dao.getAllTasksOnce()

    suspend fun countTasks(): Int = dao.countTasks()
}