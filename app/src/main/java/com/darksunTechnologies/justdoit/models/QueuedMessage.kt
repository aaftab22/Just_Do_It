package com.darksunTechnologies.justdoit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_messages")
data class QueuedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sourcePackage: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING",       // PENDING, PROCESSED, FAILED
    val senderTitle: String = ""          // Notification sender name (e.g., "Mom", "John")
)
