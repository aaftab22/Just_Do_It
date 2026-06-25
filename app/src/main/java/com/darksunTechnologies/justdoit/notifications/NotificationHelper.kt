package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.darksunTechnologies.justdoit.R

object NotificationHelper {
    private const val SMART_CHANNEL_ID = "smart_suggestions_channel"
    
    fun createSmartChannel(context: Context) {
        val channel = NotificationChannel(
            SMART_CHANNEL_ID,
            "Smart Task Suggestions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Suggestions intelligently captured from notifications"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showTaskSuggestionNotification(context: Context, taskIdRef: Int, parsedTask: ParsedTask, description: String) {
        createSmartChannel(context) // Ensure channel exists
        
        // Build intents → all point to the unified TaskActionReceiver
        val addIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_ADD_TASK
            putExtra("task_title", parsedTask.title)
            putExtra("task_description", description)
            putExtra("task_due_date", parsedTask.dueDateMillis ?: -1L)
            putExtra("task_high_priority", parsedTask.isHighPriority)
            putExtra("notification_id", taskIdRef)
        }
        val pAdd = PendingIntent.getBroadcast(
            context,
            taskIdRef, // unique request code
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ignoreIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_IGNORE
            putExtra("notification_id", taskIdRef)
        }
        val pIgnore = PendingIntent.getBroadcast(
            context,
            // Use a statistically unique request code derived from the taskIdRef
            (taskIdRef.toString() + "_ignore").hashCode(), 
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show notification
        val builder = NotificationCompat.Builder(context, SMART_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Add as task?")
            .setContentText(parsedTask.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(parsedTask.title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .addAction(0, "Add Task", pAdd)
            .addAction(0, "Ignore", pIgnore)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskIdRef, builder.build())
    }

    // ─── BATCH QUEUE SUMMARY NOTIFICATION ────────────────────────
    private const val BATCH_CHANNEL_ID = "batch_queue_channel"

    private fun createBatchChannel(context: Context) {
        val channel = NotificationChannel(
            BATCH_CHANNEL_ID,
            "Smart Queue",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent notification for queued messages waiting for AI review"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showBatchQueueNotification(context: Context, pendingCount: Int) {
        createBatchChannel(context)

        // Tap → opens the app (triggers foreground burst in onResume)
        val tapIntent = Intent(context, com.darksunTechnologies.justdoit.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pTap = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action → unified TaskActionReceiver
        val snoozeIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_BATCH_SNOOZE
        }
        val pSnooze = PendingIntent.getBroadcast(
            context, 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Silence for today action → unified TaskActionReceiver
        val silenceIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = TaskActionReceiver.ACTION_BATCH_SILENCE
        }
        val pSilence = PendingIntent.getBroadcast(
            context, 2, silenceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (pendingCount == 1) "1 message waiting for smart review"
                   else "$pendingCount messages waiting for smart review"

        val builder = NotificationCompat.Builder(context, BATCH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Smart Capture Queue")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nOpen the app to process with AI, or tap Snooze 1 Hour."))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pTap)
            .addAction(0, "Snooze 1 Hour", pSnooze)
            .addAction(0, "Silence for Today", pSilence)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(TaskActionReceiver.BATCH_NOTIFICATION_ID, builder.build())
    }
}