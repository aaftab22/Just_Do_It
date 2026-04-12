package com.darksunTechnologies.justdoit.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RepeatType {
    NONE, DAILY, WEEKLY, MONTHLY
}

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
    val hasReminder: Boolean = false,

    @ColumnInfo(defaultValue = "'NONE'")
    val repeatType: RepeatType = RepeatType.NONE,

    @ColumnInfo(defaultValue = "0")
    val hasLocationReminder: Boolean = false,

    val latitude: Double? = null,
    val longitude: Double? = null,

    @ColumnInfo(defaultValue = "100.0")
    val radius: Float = 100f
)