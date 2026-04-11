package com.darksunTechnologies.justdoit.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.darksunTechnologies.justdoit.models.Task

object AlarmHelper {
    fun cancelReminder(context: Context, taskId: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            android.util.Log.d("AlarmHelper", "Cancelled existing alarm for task $taskId")
        }
    }

     //Schedules an exact alarm for the task's due date.
     //Always call cancelReminder() before this to avoid ghost alarms.
    fun scheduleReminder(context: Context, task: Task) {
        android.util.Log.d("AlarmHelper", "Scheduling alarm for task: ${task.name} at ${task.dueDate}")
        if (!task.hasReminder || task.dueDate == null) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_name", task.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                android.util.Log.e("AlarmHelper", "Cannot schedule exact alarms - permission missing")
                return
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.dueDate,
                pendingIntent
            )
            android.util.Log.d("AlarmHelper", "Alarm set successfully for ${task.dueDate}")
        } catch (e: Exception) {
            android.util.Log.e("AlarmHelper", "Failed to set alarm", e)
        }
    }
}