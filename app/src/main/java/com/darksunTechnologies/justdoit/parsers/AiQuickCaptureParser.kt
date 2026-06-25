package com.darksunTechnologies.justdoit.parsers

import com.darksunTechnologies.justdoit.ai.GeminiNanoManager
import com.darksunTechnologies.justdoit.notifications.AiTaskExtractor
import com.darksunTechnologies.justdoit.notifications.ParsedTask
import android.util.Log

/**
 * AI-powered parser for the Quick Capture (voice/text) path.
 *
 * Unlike the notification path, we SKIP the regex classifier here because
 * if the user typed or spoke it into the capture sheet, it IS a task by definition.
 * We go straight to AI extraction for structured parsing (date, time, priority).
 *
 * Falls back to raw text as title if AI is unavailable or fails.
 */
object AiQuickCaptureParser {
    private const val TAG = "AiQuickCaptureParser"

    /**
     * Parses messy user input (typed or voice-transcribed) into a structured task.
     * Returns a List of ParsedTasks immediately — never returns empty if input is valid.
     *
     * @param rawText The raw text from the Quick Capture input field.
     * @return List of ParsedTask with AI-extracted fields, or a basic title-only fallback.
     */
    suspend fun parseUserInput(rawText: String): List<ParsedTask> {
        // For direct user input, skip regex classification — user typed it = it IS a task.
        // Go straight to AI extraction for structured parsing.
        if (GeminiNanoManager.isAvailable) {
            try {
                val aiResults = AiTaskExtractor.extract(rawText)
                if (aiResults.isNotEmpty()) {
                    Log.d(TAG, "AI_SUCCESS: ${aiResults.size} tasks")
                    return aiResults
                }
            } catch (e: Exception) {
                Log.w(TAG, "AI failed for quick capture, using fallback", e)
            }
        }

        // Fallback: Use raw text as title, no date/time extraction
        Log.d(TAG, "FALLBACK: Using raw text as title")
        return listOf(
            ParsedTask(
                title = rawText.trim().take(100),
                dueDateMillis = null,
                isHighPriority = false,
                parserSource = "regex"
            )
        )
    }
}
