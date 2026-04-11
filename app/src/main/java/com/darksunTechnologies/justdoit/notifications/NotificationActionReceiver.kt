package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val taskId = intent?.getIntExtra("task_id", -1) ?: return

        if (taskId == -1) return

        // Prevent Android from killing receiver during DB work
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val dao = db.taskDao()

                when (action) {
                    ReminderReceiver.ACTION_MARK_DONE -> {
                        // 1. Cancel the scheduled alarm so it never fires again
                        AlarmHelper.cancelReminder(context, taskId)
                        // 2. Mark done in DB
                        dao.markTaskDone(taskId)
                        Log.d("ActionReceiver", "Task $taskId marked done & alarm cancelled.")
                    }
                    ReminderReceiver.ACTION_SNOOZE -> {
                        val task = dao.getTaskById(taskId) ?: return@launch

                        // 1. Push time forward by 10 min
                        val newTime = System.currentTimeMillis() + 600_000

                        // 2. Update DB
                        dao.updateTaskDueDate(taskId, newTime)

                        // 3. Create updated copy for reschedule (Task fields are val)
                        val updatedTask = task.copy(dueDate = newTime)
                        AlarmHelper.cancelReminder(context, taskId)
                        AlarmHelper.scheduleReminder(context, updatedTask)

                        Log.d("ActionReceiver", "Task $taskId snoozed 10 mins to $newTime.")
                    }
                }

                // Dismiss the notification
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(taskId)

            } finally {
                // Inform OS receiver work is complete
                pendingResult.finish()
            }
        }
    }
}
