package com.darksunTechnologies.justdoit.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darksunTechnologies.justdoit.models.Task
import com.darksunTechnologies.justdoit.models.TaskKey
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, id DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE hasReminder=1 AND dueDate > :currentTime")
    fun getActiveReminders(currentTime: Long): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @androidx.room.Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    //for migration to room from sharedPreferences
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun countTasks(): Int

    @Query("SELECT name, isHighPriority FROM tasks")
    suspend fun getTaskKeys(): List<TaskKey>

    @Query("SELECT * FROM tasks WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY isCompleted ASC, id DESC")
    suspend fun searchTasks(query: String): List<Task>
}