package com.darksunTechnologies.justdoit.notifications

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/**
 * AI response data class matching the expected JSON output.
 * The model is prompted to output exactly this structure.
 */
data class AiResponse(
    @SerializedName("tasks") val tasks: List<AiTask>?
)

data class AiTask(
    @SerializedName("is_task") val isTask: Boolean,
    @SerializedName("title") val title: String?,
    @SerializedName("date") val date: String?,      // YYYY-MM-DD
    @SerializedName("time") val time: String?,      // HH:MM (24h)
    @SerializedName("priority") val priority: Int?  // 1=normal, 2=medium, 3=high
)

/**
 * Unified AI Task Classifier + Extractor (single NPU call).
 *
 * The prompt contains both classification logic and extraction instructions.
 * System instruction and user input are combined into a single structured prompt
 * to maximize quality from Gemini Nano's limited context window.
 *
 * All calls wrapped in 500ms timeout to prevent system hang under CPU pressure.
 */
object AiTaskExtractor {
    private const val TAG = "AiTaskExtractor"
    private val gson = Gson()

    // ─── SYSTEM PROMPT (Classification + Extraction rules) ───────
    // Combined with user input in a single call.
    private fun buildPrompt(userText: String): String {
        return """
You are a task extractor for a To-Do app. Return ONLY a JSON object, no extra text.

Rules:
- Determine if the input is an actionable task for the RECIPIENT.
- NOT a task: questions, sender-statements ("I will...", "I have to..."), shipping updates, OTP codes, ads, promotions, calls, greetings.
- If it IS a task, extract a clean title (action phrase only, no sender names).
- ONLY add a date or time if it is EXPLICITLY stated in the text. If no date or time is clearly mentioned, you MUST set "date" and "time" to null. DO NOT guess, DO NOT assume today's date.
- Split multiple tasks into separate objects in the 'tasks' array (e.g. "Buy milk and call John" -> 2 objects).
- Apply shared dates to all tasks in the same sentence if applicable.
- Time mappings: morning=09:00, afternoon=14:00, evening=18:00, night=21:00.
- Today's date: ${java.time.LocalDate.now()}

Output format: {"tasks": [{"is_task": true/false, "title": "...", "date": "YYYY-MM-DD" or null, "time": "HH:MM" or null, "priority": 1}]}

Input: "$userText"
        """.trimIndent()
    }

    // ─── MAIN EXTRACTION FUNCTION ────────────────────────────────
    /**
     * Sends text to Gemini Nano for classification + extraction in a single call.
     *
     * @return List of ParsedTask if AI classifies as task and sanity checks pass, empty list otherwise.
     *         Returns empty list on timeout, parse error, or if AI says "not a task".
     *         Callers should fall back to SmartTaskParser on empty list.
     */
    suspend fun extract(text: String): List<ParsedTask> {
        val client = GeminiNanoManager.getClient() ?: return emptyList()

        return try {
            // Increased to 60000ms (60s) for temporary deep debugging
            val responseText = withTimeout(60000) {
                Log.d(TAG, "DEBUG_STEP_1: Building prompt for text: \"$text\"")
                val prompt = buildPrompt(text)
                val request = generateContentRequest(
                    TextPart(prompt)
                ) {
                    temperature = 0.1f
                    maxOutputTokens = 128
                }
                Log.d(TAG, "DEBUG_STEP_2: Sending request to Gemini Nano NPU...")
                val startTime = System.currentTimeMillis()
                val response = client.generateContent(request)
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "DEBUG_STEP_3: Received response in ${duration}ms")
                
                val rawText = response.candidates.firstOrNull()?.text ?: ""
                Log.d(TAG, "DEBUG_STEP_4: Raw AI Output: $rawText")
                rawText
            }

            if (responseText.isBlank()) {
                Log.w(TAG, "AI_EMPTY_RESPONSE for: \"$text\"")
                return emptyList()
            }

            Log.d(TAG, "DEBUG_STEP_5: Starting JSON extraction...")
            // Extract JSON from response (model may wrap in markdown code blocks)
            val jsonStr = extractJson(responseText)
            if (jsonStr == null) {
                Log.w(TAG, "AI_PARSE_ERROR: Could not extract JSON from: $responseText")
                return emptyList()
            }
            Log.d(TAG, "DEBUG_STEP_6: Successfully extracted JSON string: $jsonStr")

            val aiResponse = gson.fromJson(jsonStr, AiResponse::class.java)
            val aiTasks = aiResponse.tasks ?: return emptyList()
            Log.d(TAG, "DEBUG_STEP_7: Gson successfully parsed ${aiTasks.size} tasks")

            var sharedDueDateMillis: Long? = null
            val validTasks = mutableListOf<ParsedTask>()
            
            // Iterate in reverse to bubble up dates from later tasks in the sentence to earlier ones
            for (ai in aiTasks.reversed()) {
                if (!ai.isTask) {
                    Log.d(TAG, "AI_CLASSIFIED_NOT_TASK: \"$text\"")
                    continue
                }

                Log.d(TAG, "AI_SUCCESS: \"$text\" → title=\"${ai.title}\", date=${ai.date}, time=${ai.time}, priority=${ai.priority}")
                val parsed = validateAndConvert(ai)
                if (parsed != null) {
                    if (parsed.dueDateMillis != null) {
                        sharedDueDateMillis = parsed.dueDateMillis
                    } else if (sharedDueDateMillis != null) {
                        validTasks.add(parsed.copy(dueDateMillis = sharedDueDateMillis))
                        continue
                    }
                    validTasks.add(parsed)
                }
            }

            return validTasks.reversed()

        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w(TAG, "AI_TIMEOUT: Inference exceeded 60000ms for: \"$text\"")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "AI_ERROR_EXCEPTION: ${e.javaClass.simpleName} - ${e.message}")
            emptyList()
        }
    }

    /**
     * Extracts JSON object from response text.
     * Handles cases where model might wrap JSON in markdown code blocks.
     */
    private fun extractJson(text: String): String? {
        val trimmed = text.trim()
        // Ideal case: response is already pure JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        // Fallback: extract between first { and last }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }

    // ─── HALLUCINATION GUARD ─────────────────────────────────────
    /**
     * Validates AI output before converting to ParsedTask.
     * Rejects: blank titles, titles >100 chars, dates >1 year away.
     */
    private fun validateAndConvert(ai: AiTask): ParsedTask? {
        val title = ai.title ?: return null

        // Guard 1: Title sanity
        if (title.isBlank() || title.length > 100) {
            Log.w(TAG, "AI_HALLUCINATION: Title invalid: \"$title\"")
            return null
        }

        // Guard 2: Parse date + time into millis
        var dueDateMillis: Long? = null
        if (ai.date != null || ai.time != null) {
            val cal = java.util.Calendar.getInstance()

            if (ai.date != null) {
                try {
                    val parts = ai.date.split("-")
                    if (parts.size == 3) {
                        cal.set(java.util.Calendar.YEAR, parts[0].toInt())
                        cal.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                        cal.set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "AI_HALLUCINATION: Bad date format: ${ai.date}")
                }
            }

            if (ai.time != null) {
                try {
                    val timeParts = ai.time.split(":")
                    if (timeParts.size >= 2) {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                        cal.set(java.util.Calendar.MINUTE, timeParts[1].toInt())
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.set(java.util.Calendar.MILLISECOND, 0)
                    }
                } catch (_: Exception) {
                    Log.w(TAG, "AI_HALLUCINATION: Bad time format: ${ai.time}")
                }
            }

            // Guard 3: No dates more than 1 year in the past or future
            val diff = abs(cal.timeInMillis - System.currentTimeMillis())
            if (diff > 365L * 24 * 60 * 60 * 1000) {
                Log.w(TAG, "AI_HALLUCINATION: Extreme date: ${ai.date}")
                return null
            }

            dueDateMillis = cal.timeInMillis
        }

        return ParsedTask(
            title = title.trim(),
            dueDateMillis = dueDateMillis,
            isHighPriority = (ai.priority ?: 1) >= 3,
            parserSource = "offline_ai"
        )
    }
}
