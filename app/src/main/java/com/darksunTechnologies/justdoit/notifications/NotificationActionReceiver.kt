package com.darksunTechnologies.justdoit.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
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
                val repo = TaskRepository(dao)

                when (action) {
                    ReminderReceiver.ACTION_MARK_DONE -> {
                        val task = dao.getTaskById(taskId)
                        if (task != null && !task.isCompleted) {
                            // 1. Cancel the scheduled alarm so it never fires again
                            AlarmHelper.cancelReminder(context, taskId)
                            // 2. Delegate DB work to Central Repository
                            val newlySpawnedTask = repo.handleTaskCompletion(task)
                            // 3. Schedule newly spawned reminder
                            if (newlySpawnedTask != null && newlySpawnedTask.hasReminder) {
                                AlarmHelper.scheduleReminder(context, newlySpawnedTask)
                            }
                            Log.d("ActionReceiver", "Task $taskId marked done & recurring handled.")
                        }
                    }
                    ReminderReceiver.ACTION_SNOOZE -> {
                        val task = dao.getTaskById(taskId) ?: return@launch

                        // 1. Calculate time pushed forward by 10 min
                        val newTime = System.currentTimeMillis() + 600_000

                        // 2. Create updated copy ONLY for local reschedule (leaves Original DB Date intact for Recurring logic)
                        val snoozeTask = task.copy(dueDate = newTime)
                        
                        AlarmHelper.cancelReminder(context, taskId)
                        AlarmHelper.scheduleReminder(context, snoozeTask)

                        Log.d("ActionReceiver", "Task $taskId snoozed 10 mins to $newTime without mutating DB.")
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
