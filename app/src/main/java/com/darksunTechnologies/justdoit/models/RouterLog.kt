package com.darksunTechnologies.justdoit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "router_logs")
data class RouterLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val sender: String? = null,
    val message: String,           // Truncated to 300 chars max
    val decision: String,          // BLOCKED_MEDIA, BLOCKED_NEGATIVE, BLOCKED_SCORE, ESCALATED_SCORE, REGEX_STRONG_MATCH, BLOCKED_NON_LATIN
    val score: Int = 0,            // Intent score for ALL decisions (0 for pre-scoring blocks)
    val reason: String? = null,    // Human-readable reason
    val source: String             // MEDIA_FILTER, NEGATIVE_FILTER, REGEX, INTENT_SCORER, SCRIPT_FILTER
)
