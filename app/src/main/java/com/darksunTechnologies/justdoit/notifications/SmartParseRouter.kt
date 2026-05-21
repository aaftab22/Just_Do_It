package com.darksunTechnologies.justdoit.notifications

import android.content.Context
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.models.RouterLog
import java.util.Calendar
import java.util.Locale

/**
 * Data class representing a parsed task extracted from notification text.
 */
data class ParsedTask(
    val title: String,
    val dueDateMillis: Long?,
    val isHighPriority: Boolean,
    val parserSource: String = "regex"
)

/**
 * Unified notification → task pipeline.
 *
 * Consolidates the former SmartParseRouter + SmartTaskParser into a single file.
 * All filter lists come from [TaskFilterDictionary].
 *
 * Pipeline:
 * 1. Non-Latin script filter
 * 2. Media/Junk filter
 * 3. Negative pattern filter (acknowledgments, spam, sender-statements)
 * 4. Regex extraction (title + date/time) — instant, 0 battery
 * 5. If regex found strong match (title+date) → return immediately
 * 6. Task intent scoring → gates AI queue escalation
 * 7. If score >= threshold → queue for deferred AI processing
 */
object SmartParseRouter {
    private const val TAG = "SmartParseRouter"
    private const val INTENT_SCORE_THRESHOLD = 3

    // Alias the dictionary for concise access
    private val D = TaskFilterDictionary

    /**
     * Unified entry point for parsing text into a task.
     * Must be called from a coroutine on Dispatchers.IO.
     *
     * @param db Pre-initialized AppDatabase instance — avoids calling getInstance() per notification.
     */
    suspend fun parseText(text: String, sourcePackage: String, senderTitle: String, context: Context, db: AppDatabase): List<ParsedTask> {
        val lowerText = text.lowercase(Locale.getDefault()).trim()

        // ── STEP 0.1: NON-LATIN SCRIPT FILTER ──
        if (!isLatinScript(text)) {
            Log.d(TAG, "BLOCKED_NON_LATIN: Blocking non-Latin message: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_NON_LATIN", 0, "Non-Latin script detected", "SCRIPT_FILTER")
            return emptyList()
        }

        // ── STEP 0: MEDIA/JUNK FILTER ──
        if (D.MEDIA_JUNK.any { lowerText.contains(it) } || D.N_MESSAGES_REGEX.containsMatchIn(lowerText)) {
            Log.d(TAG, "BLOCKED_MEDIA: Blocking media/junk message: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_MEDIA", 0, "Media/junk message detected", "MEDIA_FILTER")
            return emptyList()
        }

        // ── STEP 0.5: NEGATIVE PATTERN FILTER ──
        val words = lowerText.split(Regex("\\s+"))

        // Short acknowledgments
        if (words.size <= 3 && D.ACKNOWLEDGMENTS.any { lowerText == it || lowerText.startsWith("$it ") }) {
            Log.d(TAG, "BLOCKED_NEGATIVE: Acknowledgment detected: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_NEGATIVE", 0, "Acknowledgment detected", "NEGATIVE_FILTER")
            return emptyList()
        }
        // Spam/commerce
        if (D.SPAM_COMMERCE.any { lowerText.contains(it) }) {
            Log.d(TAG, "BLOCKED_NEGATIVE: Spam/commerce detected: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_NEGATIVE", 0, "Spam/commerce detected", "NEGATIVE_FILTER")
            return emptyList()
        }
        // Sender statements ("I sent the money", "We already paid")
        if (D.SENDER_STATEMENTS.any { it.containsMatchIn(lowerText) }) {
            Log.d(TAG, "BLOCKED_NEGATIVE: Sender statement detected: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_NEGATIVE", 0, "Sender statement detected", "NEGATIVE_FILTER")
            return emptyList()
        }

        // ── STEP 1: REGEX EXTRACTION (Instant, 0 battery cost) ──
        val regexResults = regexParse(text)

        // Strong Match: Regex found Title + Date → return immediately, skip AI
        if (regexResults.isNotEmpty() && regexResults.all { it.dueDateMillis != null }) {
            Log.d(TAG, "REGEX_STRONG_MATCH: ${regexResults.size} tasks — skipping AI")
            logDecision(db, sourcePackage, senderTitle, text, "REGEX_STRONG_MATCH", 0, "Regex extracted ${regexResults.size} task(s) with date", "REGEX")
            return regexResults
        }

        // ── STEP 1.5: TASK INTENT SCORING ──
        var score = 0
        val reasons = mutableListOf<String>()

        // Action verb at start (+3) — only within first 3 words
        val firstFewWords = words.take(3)
        if (D.ACTION_VERBS.any { firstFewWords.contains(it) }) { score += 3; reasons.add("action_verb") }

        if (D.INTENT_PHRASES.any { lowerText.contains(it) }) { score += 3; reasons.add("intent_phrase") }
        if (D.FUTURE_TIME.any { lowerText.contains(it) }) { score += 2; reasons.add("future_time") }

        // Explicit time
        if (D.STANDARD_TIME_REGEX.containsMatchIn(lowerText) ||
            D.MILITARY_TIME_REGEX.containsMatchIn(lowerText) ||
            D.WEAK_TIME_REGEX.containsMatchIn(lowerText)) {
            score += 2; reasons.add("explicit_time")
        }

        if (D.OBLIGATION.any { lowerText.contains(it) }) { score += 1; reasons.add("obligation") }
        if (D.SECOND_PERSON.any { lowerText.contains(it) }) { score += 1; reasons.add("second_person") }

        // Cap at 10
        score = kotlin.math.min(score, 10)

        if (score < INTENT_SCORE_THRESHOLD) {
            val reasonStr = if (reasons.isEmpty()) "No task signals" else "Signals: ${reasons.joinToString(", ")}; below threshold"
            Log.d(TAG, "BLOCKED_SCORE_$score: Intent score below threshold ($INTENT_SCORE_THRESHOLD) for: \"$text\"")
            logDecision(db, sourcePackage, senderTitle, text, "BLOCKED_SCORE", score, reasonStr, "INTENT_SCORER")
            return emptyList()
        }

        val reasonStr = "Signals: ${reasons.joinToString(", ")}; passed threshold"
        Log.d(TAG, "ESCALATED_SCORE_$score: Passing to AI queue for: \"$text\"")
        logDecision(db, sourcePackage, senderTitle, text, "ESCALATED_SCORE", score, reasonStr, "INTENT_SCORER")

        // ── STEP 2: AI ESCALATION TO BATCH QUEUE ──
        db.queuedMessageDao().insert(
            com.darksunTechnologies.justdoit.models.QueuedMessage(
                sourcePackage = sourcePackage,
                rawText = text,
                senderTitle = senderTitle
            )
        )

        // Schedule WorkManager (only runs when Charging + Idle)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<AiBatchProcessorWorker>()
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "AI_BATCH_PROCESSOR",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )

        // Show/update the summary notification (unless user silenced for today)
        if (!TaskActionReceiver.isSilenced(context)) {
            val pendingCount = db.queuedMessageDao().getPendingCount()

            // Show notifications only when crossing specific thresholds (10, 20, 30)
            val thresholds = listOf(10, 20, 30)
            val currentThreshold = thresholds.lastOrNull { pendingCount >= it } ?: 0

            val prefs = context.getSharedPreferences("batch_notification_prefs", Context.MODE_PRIVATE)
            val lastNotifiedThreshold = prefs.getInt("last_notified_threshold", 0)

            if (currentThreshold > lastNotifiedThreshold) {
                NotificationHelper.showBatchQueueNotification(context, pendingCount)
                prefs.edit().putInt("last_notified_threshold", currentThreshold).apply()
            } else if (currentThreshold < lastNotifiedThreshold) {
                prefs.edit().putInt("last_notified_threshold", currentThreshold).apply()
            }
        }

        // ── STEP 3: FINAL FALLBACK ──
        return regexResults
    }

    // ═══════════════════════════════════════════════════════════════
    //  REGEX PARSER (absorbed from SmartTaskParser)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Splits text into chunks by "and"/"then"/commas and extracts tasks from each chunk.
     * Shares dates across chunks in the same sentence (right-to-left propagation).
     */
    private fun regexParse(text: String): List<ParsedTask> {
        val lowerText = text.lowercase(Locale.getDefault())
            .replace("\u2019", "'")
            .replace("\u2018", "'")
            .trim()

        val chunks = lowerText.split(Regex("\\band\\b|\\bthen\\b|,"))
        val results = mutableListOf<ParsedTask>()
        var sharedDueDateMillis: Long? = null

        for (chunk in chunks.reversed()) {
            val parsed = regexParseSingle(chunk)
            if (parsed != null) {
                if (parsed.dueDateMillis != null) {
                    sharedDueDateMillis = parsed.dueDateMillis
                } else if (sharedDueDateMillis != null) {
                    results.add(parsed.copy(dueDateMillis = sharedDueDateMillis))
                    continue
                }
                results.add(parsed)
            }
        }

        return results.reversed()
    }

    /**
     * Attempts to extract a single ParsedTask from a text chunk using regex heuristics.
     * Returns null if the chunk doesn't look like a task.
     */
    private fun regexParseSingle(text: String): ParsedTask? {
        val lowerText = text.trim()

        // 1. Minimum word count
        if (lowerText.split("\\s+".toRegex()).size < 2) return null

        // 2. Question detection
        if (D.QUESTION_FILTERS.any { lowerText.contains(it) }) return null

        // 3. Intent detection & non-task tones
        val hasIntentPhrase = D.INTENT_PHRASES.any { lowerText.contains(it) }

        if (!hasIntentPhrase) {
            val hasNonTaskTone = D.NON_TASK_TONES.any { tone ->
                lowerText.contains("\\b$tone\\b".toRegex())
            }
            if (hasNonTaskTone) return null
        }

        // 4. Must start with action verb OR contain intent phrase
        val startsWithVerb = D.ACTION_VERBS.any { lowerText.startsWith("$it ") || lowerText == it }
        if (!startsWithVerb && !hasIntentPhrase) return null

        val isHighPriority = D.PRIORITY_KEYWORDS.any { lowerText.contains("\\b$it\\b".toRegex()) }

        // 5. Extract Date / Time
        var hasDate = false
        var timeFound = false
        var dueDateMillis: Long? = null
        val cal = Calendar.getInstance()

        // Date detection
        if (lowerText.contains("\\btoday\\b".toRegex())) {
            hasDate = true
        } else if (lowerText.contains("\\btomorrow\\b".toRegex())) {
            hasDate = true
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (lowerText.contains("\\bnext week\\b".toRegex())) {
            hasDate = true
            cal.add(Calendar.DAY_OF_YEAR, 7)
        } else if (lowerText.contains("\\bthis weekend\\b".toRegex())) {
            hasDate = true
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            var daysToAdd = Calendar.SATURDAY - currentDay
            if (daysToAdd <= 0) daysToAdd += 7
            cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
        } else {
            val dayMatch = D.DAY_OF_WEEK_REGEX.find(lowerText)
            if (dayMatch != null) {
                hasDate = true
                val targetDay = when (dayMatch.groupValues[1].lowercase()) {
                    "sunday" -> Calendar.SUNDAY
                    "monday" -> Calendar.MONDAY
                    "tuesday" -> Calendar.TUESDAY
                    "wednesday" -> Calendar.WEDNESDAY
                    "thursday" -> Calendar.THURSDAY
                    "friday" -> Calendar.FRIDAY
                    "saturday" -> Calendar.SATURDAY
                    else -> -1
                }
                if (targetDay != -1) {
                    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = targetDay - currentDay
                    if (daysToAdd <= 0) daysToAdd += 7
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }

        // Part-of-day mapping
        if (lowerText.contains("\\bmorning\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0); timeFound = true
        } else if (lowerText.contains("\\bafternoon\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 14); cal.set(Calendar.MINUTE, 0); timeFound = true
        } else if (lowerText.contains("\\bevening\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 18); cal.set(Calendar.MINUTE, 0); timeFound = true
        } else if (lowerText.contains("\\bnight\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 21); cal.set(Calendar.MINUTE, 0); timeFound = true
        }

        // Standard time (e.g. "5 pm")
        val stdTimeMatch = D.STANDARD_TIME_REGEX.find(lowerText)
        if (!timeFound && stdTimeMatch != null) {
            var hour = stdTimeMatch.groupValues[1].toInt()
            val minute = stdTimeMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val amPm = stdTimeMatch.groupValues[3].lowercase(Locale.getDefault())
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timeFound = true
        } else if (!timeFound) {
            // Military time (e.g. "17:00")
            val milTimeMatch = D.MILITARY_TIME_REGEX.find(lowerText)
            if (milTimeMatch != null) {
                cal.set(Calendar.HOUR_OF_DAY, milTimeMatch.groupValues[1].toInt())
                cal.set(Calendar.MINUTE, milTimeMatch.groupValues[2].toInt())
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                timeFound = true
            } else {
                // Weak time ("at 7")
                val weakTimeMatch = D.WEAK_TIME_REGEX.find(lowerText)
                if (weakTimeMatch != null) {
                    var hour = weakTimeMatch.groupValues[1].toInt()
                    if (hour in 1..11) hour += 12 // Default to PM
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    timeFound = true
                }
            }
        }

        if (timeFound || hasDate) {
            if (timeFound && cal.timeInMillis <= System.currentTimeMillis() && !lowerText.contains("\\btomorrow\\b".toRegex())) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } else if (!timeFound && lowerText.contains("\\btoday\\b".toRegex())) {
                cal.add(Calendar.HOUR_OF_DAY, 2)
            }
            dueDateMillis = cal.timeInMillis
        }

        // Clean up the title
        val title = text.replace('\n', ' ').trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .take(100)

        return ParsedTask(
            title = title,
            dueDateMillis = dueDateMillis,
            isHighPriority = isHighPriority
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  ROUTER LOG PERSISTENCE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Persists a router decision to the Room database for later analysis.
     * Message is truncated to 300 characters to prevent DB bloat.
     */
    private suspend fun logDecision(
        db: AppDatabase,
        packageName: String,
        sender: String?,
        message: String,
        decision: String,
        score: Int,
        reason: String?,
        source: String
    ) {
        try {
            db.routerLogDao().insertLog(
                RouterLog(
                    packageName = packageName,
                    sender = sender,
                    message = message.take(300),
                    decision = decision,
                    score = score,
                    reason = reason,
                    source = source
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist router log", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SCRIPT DETECTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Heuristic to determine if text is primarily written in Latin script.
     * Prevents queueing messages in Devanagari, Arabic, Cyrillic, etc.
     */
    private fun isLatinScript(text: String): Boolean {
        val lettersOnly = text.filter { it.isLetter() }
        if (lettersOnly.isEmpty()) return true
        val englishLetterCount = lettersOnly.count { it in 'a'..'z' || it in 'A'..'Z' }
        return (englishLetterCount.toFloat() / lettersOnly.length) > 0.7f
    }
}
