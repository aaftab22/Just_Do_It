package com.darksunTechnologies.justdoit.notifications

import android.content.Context
import android.os.BatteryManager
import android.util.Log

/**
 * Hybrid Regex-First → AI-Escalate Router.
 *
 * Decision logic:
 * 1. Run SmartTaskParser (regex) first — it's free (<1ms, 0 battery).
 * 2. If regex returns a "Strong Match" (title + date), return immediately. Skip AI.
 * 3. If regex returns a "Weak Match" (title only, no date) or null,
 *    escalate to AiTaskExtractor if Gemini Nano is available and battery is healthy.
 * 4. If AI fails (timeout, hallucination, unavailable), fall back to weak regex result or null.
 *
 * Strong Match = ParsedTask with non-null dueDateMillis
 * Weak Match   = ParsedTask with null dueDateMillis (title found but no time/date)
 */
object SmartParseRouter {
    private const val TAG = "SmartParseRouter"

    /**
     * Unified entry point for parsing text into a task.
     * Must be called from a coroutine on Dispatchers.IO.
     */
    suspend fun parseText(text: String, sourcePackage: String, senderTitle: String, context: Context): List<ParsedTask> {

        // ── STEP 0: NEGATIVE FILTERING (Spam/Calls) ──
        if (SmartTaskParser.isNegativeMatch(text)) {
            Log.d(TAG, "NEGATIVE_MATCH: Blocking task parsing for: \"$text\"")
            return emptyList()
        }

        // ── STEP 1: REGEX FIRST (Instant, 0 battery cost) ──
        val regexResults = SmartTaskParser.parse(text)

        // Strong Match: Regex found Title + Date → return immediately, skip AI entirely
        if (regexResults.isNotEmpty() && regexResults.all { it.dueDateMillis != null }) {
            Log.d(TAG, "REGEX_STRONG_MATCH: ${regexResults.size} tasks — skipping AI")
            return regexResults
        }

        // ── STEP 2: AI ESCALATION TO BATCH QUEUE ──
        // If Regex failed or was weak, queue it for the AI Batch Engine
        Log.d(TAG, "ESCALATING_TO_QUEUE: Regex was weak or null for: \"$text\"")
        
        val db = com.darksunTechnologies.justdoit.database.AppDatabase.getInstance(context)
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
        if (!BatchNotificationReceiver.isSilenced(context)) {
            val pendingCount = db.queuedMessageDao().getPendingCount()
            if (pendingCount >= 5) {
                NotificationHelper.showBatchQueueNotification(context, pendingCount)
            }
        }

        // ── STEP 3: FINAL FALLBACK ──
        // Return whatever regex found (even if weak/partial), or empty list.
        // If the AI finds something better later, it will quietly drop it into the Inbox.
        return regexResults
    }

    /**
     * Checks if the device is in a healthy state for AI inference.
     * Only use AI if battery > 15% OR device is charging.
     * This prevents the Android Power Manager from killing the app.
     */
    private fun isDeviceInGoodState(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            level > 15 || bm.isCharging
        } catch (e: Exception) {
            // If we can't check battery, play it safe — allow AI
            true
        }
    }
}
