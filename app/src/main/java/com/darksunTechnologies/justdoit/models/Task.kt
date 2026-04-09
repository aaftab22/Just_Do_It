package com.darksunTechnologies.justdoit.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val isHighPriority: Boolean,
    val description: String? = null,

    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),

    val dueDate: Long? = null,

    @ColumnInfo(defaultValue = "manual")
    val source: String = "manual", // "manual", "voice", "shake", "vip"

    @ColumnInfo(defaultValue = "0")
    val isCompleted: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    val hasReminder: Boolean = false
)