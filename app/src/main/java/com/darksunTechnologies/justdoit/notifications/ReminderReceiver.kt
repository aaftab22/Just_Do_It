package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.darksunTechnologies.justdoit.TaskDetailActivity

class ReminderReceiver: BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "ACTION_MARK_DONE"
        const val ACTION_SNOOZE = "ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val taskId = intent?.getIntExtra("task_id", -1) ?: -1
        val taskName = intent?.getStringExtra("task_name") ?: "Task Reminder"

        android.util.Log.d("ReminderReceiver", "Alarm fired for task $taskId: $taskName")

        // --- Tap intent: opens TaskDetailActivity with proper back stack ---
        val tapIntent = Intent(context, TaskDetailActivity::class.java).apply {
            putExtra("task_id", taskId)
            putExtra("task_name", taskName)
            putExtra("start_in_edit_mode", false)
        }

        val pendingTapIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(tapIntent)
            getPendingIntent(taskId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        // --- Mark Done action button ---
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra("task_id", taskId)
        }
        val pendingDoneIntent = PendingIntent.getBroadcast(
            context,
            taskId * 10, // unique request code
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Snooze action button ---
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("task_id", taskId)
        }
        val pendingSnoozeIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10) + 1, // unique request code
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Build notification ---
        val notification = NotificationCompat.Builder(context, "task_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Task Reminder")
            .setContentText(taskName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingTapIntent)
            .addAction(android.R.drawable.ic_menu_send, "Mark Done", pendingDoneIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Snooze 10m", pendingSnoozeIntent)
            .build()

        // CRITICAL: use taskId so manager.cancel(taskId) works from action receivers
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(taskId, notification)
    }
}