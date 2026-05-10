package com.darksunTechnologies.justdoit.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darksunTechnologies.justdoit.models.QueuedMessage

@Dao
interface QueuedMessageDao {
    @Query("SELECT * FROM queued_messages ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<QueuedMessage>

    @Query("SELECT * FROM queued_messages WHERE status = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<QueuedMessage>

    @Query("SELECT COUNT(*) FROM queued_messages WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM queued_messages WHERE status = 'PENDING'")
    fun getPendingCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: QueuedMessage)

    @Delete
    suspend fun delete(message: QueuedMessage)
    
    @Query("DELETE FROM queued_messages WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("UPDATE queued_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String)

    @Query("DELETE FROM queued_messages WHERE status = 'PROCESSED'")
    suspend fun deleteProcessed()
}
