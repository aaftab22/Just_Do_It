package com.darksunTechnologies.justdoit.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.darksunTechnologies.justdoit.models.RouterLog

@Dao
interface RouterLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RouterLog)

    @Query("SELECT * FROM router_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatestLogs(limit: Int): List<RouterLog>

    @Query("DELETE FROM router_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteLogsOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM router_logs")
    suspend fun clearAllLogs()
}
