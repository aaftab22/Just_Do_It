package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.darksunTechnologies.justdoit.R
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.models.Task

class AiBatchProcessorWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "AiBatchProcessor"
        private const val CHANNEL_ID = "batch_processing_channel"
        private const val NOTIFICATION_ID = 999_998
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker started. Fetching pending messages.")

        // Elevate to Foreground Service to bypass ErrorCode 30 (Background NPU Block)
        setForeground(createForegroundInfo())

        val db = AppDatabase.getInstance(applicationContext)
        val queueDao = db.queuedMessageDao()
        val taskDao = db.taskDao()

        val pending = queueDao.getPendingMessages()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending messages. Exiting.")
            return Result.success()
        }

        Log.d(TAG, "Processing ${pending.size} pending messages.")

        if (!GeminiNanoManager.isAvailable) {
            Log.w(TAG, "Gemini Nano not available. Will retry later.")
            return Result.retry()
        }

        for (msg in pending) {
            try {
                val parsedTasks = AiTaskExtractor.extract(msg.rawText)
                if (parsedTasks.isNotEmpty()) {
                    for (parsed in parsedTasks) {
                        // Deduplication: skip if a task with the same name already exists
                        val existing = taskDao.getTaskByName(parsed.title)
                        if (existing != null) {
                            Log.d(TAG, "Skipping duplicate task: ${parsed.title}")
                            continue
                        }

                        // Build description like SmartNotificationListenerService does
                        val appName = when (msg.sourcePackage) {
                            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                            "org.telegram.messenger" -> "Telegram"
                            "com.google.android.apps.messaging" -> "Messages"
                            "com.google.android.gm" -> "Gmail"
                            "com.microsoft.office.outlook" -> "Outlook"
                            else -> "Notifications"
                        }
                        val desc = "Added from $appName" +
                            (if (msg.senderTitle.isNotBlank()) " (${msg.senderTitle})" else "") +
                            "\n[Parsed by: Offline AI 🤖]"

                        val task = Task(
                            name = parsed.title,
                            description = desc,
                            dueDate = parsed.dueDateMillis,
                            isHighPriority = parsed.isHighPriority,
                            source = parsed.parserSource,
                            needsReview = true,
                            hasReminder = parsed.dueDateMillis != null
                        )
                        taskDao.insertTask(task)
                        Log.d(TAG, "Inserted review task: ${parsed.title}")
                    }
                }
                // Mark as processed and remove from queue
                queueDao.updateStatus(msg.id, "PROCESSED")
                queueDao.deleteById(msg.id)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing msg ${msg.id}: ${e.message}", e)
                queueDao.updateStatus(msg.id, "FAILED")
            }
        }

        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        // Ensure channel exists
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Processing",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Shows when the app is processing smart captures"
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Processing smart captures...")
            .setContentText("Analyzing queued messages with AI")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
