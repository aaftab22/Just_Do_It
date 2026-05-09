package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
import com.darksunTechnologies.justdoit.models.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartCaptureActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADD_TASK = "com.darksunTechnologies.justdoit.ACTION_ADD_TASK"
        const val ACTION_IGNORE = "com.darksunTechnologies.justdoit.ACTION_IGNORE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val notifId = intent.getIntExtra("notification_id", -1)
        if (notifId == -1) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (action == ACTION_ADD_TASK) {
                    val title = intent.getStringExtra("task_title") ?: "Unknown Task"
                    val desc = intent.getStringExtra("task_description") ?: "Captured automatically from notifications"
                    val dueDate = intent.getLongExtra("task_due_date", -1L).takeIf { it != -1L }
                    val isHighPriority = intent.getBooleanExtra("task_high_priority", false)

                    val db = AppDatabase.getInstance(context)
                    val repo = TaskRepository(db.taskDao())

                    val newTask = Task(
                        name = title,
                        description = desc,
                        dueDate = dueDate,
                        isHighPriority = isHighPriority,
                        source = "smart_capture",
                        createdAt = System.currentTimeMillis(),
                        hasReminder = dueDate != null
                    )

                    val savedId = repo.insertTask(newTask)
                    if (dueDate != null) {
                        val savedTask = db.taskDao().getTaskById(savedId.toInt())
                        if (savedTask != null) {
                            AlarmHelper.scheduleReminder(context, savedTask)
                        }
                    }
                    Log.d("SmartCaptureReceiver", "Task dynamically saved from suggestion: $title")
                }

                // Dismiss the notification for both ADD and IGNORE
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(notifId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
