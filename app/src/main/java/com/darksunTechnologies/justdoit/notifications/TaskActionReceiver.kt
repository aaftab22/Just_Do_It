package com.darksunTechnologies.justdoit.notifications

import com.darksunTechnologies.justdoit.alarms.AlarmHelper
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.database.TaskRepository
import com.darksunTechnologies.justdoit.models.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Unified BroadcastReceiver for all notification action buttons.
 *
 * Consolidates the former:
 *   - SmartCaptureActionReceiver  (Add Task / Ignore)
 *   - BatchNotificationReceiver   (Batch Snooze / Silence / Reshow)
 *   - NotificationActionReceiver  (Mark Done / Snooze 10m)
 *
 * Routes by Intent.action to the appropriate handler.
 */
class TaskActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskActionReceiver"

        // ── Smart Capture actions ──
        const val ACTION_ADD_TASK = "com.darksunTechnologies.justdoit.ACTION_ADD_TASK"
        const val ACTION_IGNORE = "com.darksunTechnologies.justdoit.ACTION_IGNORE"

        // ── Batch Queue actions ──
        const val ACTION_BATCH_SNOOZE = "com.darksunTechnologies.justdoit.ACTION_BATCH_SNOOZE"
        const val ACTION_BATCH_SILENCE = "com.darksunTechnologies.justdoit.ACTION_BATCH_SILENCE_TODAY"
        const val ACTION_BATCH_RESHOW = "com.darksunTechnologies.justdoit.ACTION_BATCH_RESHOW"
        const val BATCH_NOTIFICATION_ID = 999_999

        // ── Reminder actions ──
        const val ACTION_MARK_DONE = "ACTION_MARK_DONE"
        const val ACTION_SNOOZE_TASK = "ACTION_SNOOZE"

        // ── Batch Silence Prefs ──
        private const val PREFS_NAME = "batch_notification_prefs"
        private const val KEY_SILENCED_UNTIL = "silenced_until"

        /** Check if the user has silenced batch notifications for today. */
        fun isSilenced(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val silencedUntil = prefs.getLong(KEY_SILENCED_UNTIL, 0L)
            return System.currentTimeMillis() < silencedUntil
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ── Lightweight sync actions (no DB required) ────────────
        when (action) {
            ACTION_IGNORE -> {
                val notifId = intent.getIntExtra("notification_id", -1)
                if (notifId != -1) manager.cancel(notifId)
                return
            }
            ACTION_BATCH_SNOOZE -> {
                manager.cancel(BATCH_NOTIFICATION_ID)
                scheduleBatchReshow(context)
                Log.d(TAG, "Snoozed batch notification for 1 hour")
                return
            }
            ACTION_BATCH_SILENCE -> {
                manager.cancel(BATCH_NOTIFICATION_ID)
                silenceUntilMidnight(context)
                Log.d(TAG, "Silenced batch notifications until midnight")
                return
            }
        }

        // ── Async actions (need DB access) ───────────────────────
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                when (action) {
                    ACTION_ADD_TASK -> handleAddTask(context, intent, db, manager)
                    ACTION_BATCH_RESHOW -> handleBatchReshow(context, db)
                    ACTION_MARK_DONE -> handleMarkDone(context, intent, db, manager)
                    ACTION_SNOOZE_TASK -> handleTaskSnooze(context, intent, db, manager)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling action $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Smart Capture Handlers
    // ═══════════════════════════════════════════════════════════════

    private suspend fun handleAddTask(
        context: Context, intent: Intent, db: AppDatabase, manager: NotificationManager
    ) {
        val notifId = intent.getIntExtra("notification_id", -1)
        val title = intent.getStringExtra("task_title") ?: "Unknown Task"
        val desc = intent.getStringExtra("task_description") ?: "Captured automatically from notifications"
        val dueDate = intent.getLongExtra("task_due_date", -1L).takeIf { it != -1L }
        val isHighPriority = intent.getBooleanExtra("task_high_priority", false)

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
        Log.d(TAG, "Task saved from suggestion: $title")

        if (notifId != -1) manager.cancel(notifId)
    }

    // ═══════════════════════════════════════════════════════════════
    // Batch Queue Handlers
    // ═══════════════════════════════════════════════════════════════

    private fun scheduleBatchReshow(context: Context) {
        val reshowIntent = Intent(context, TaskActionReceiver::class.java).apply {
            action = ACTION_BATCH_RESHOW
        }
        val pi = PendingIntent.getBroadcast(
            context, 0, reshowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.RTC,
            System.currentTimeMillis() + 60 * 60 * 1000L, // 1 hour
            pi
        )
    }

    private fun silenceUntilMidnight(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_SILENCED_UNTIL, cal.timeInMillis)
            }
    }

    private suspend fun handleBatchReshow(context: Context, db: AppDatabase) {
        Log.d(TAG, "Snooze expired — re-showing batch notification")
        try {
            val count = db.queuedMessageDao().getPendingCountSync()
            if (count > 0 && !isSilenced(context)) {
                NotificationHelper.showBatchQueueNotification(context, count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error re-showing batch notification", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Reminder Action Handlers
    // ═══════════════════════════════════════════════════════════════

    private suspend fun handleMarkDone(
        context: Context, intent: Intent, db: AppDatabase, manager: NotificationManager
    ) {
        val taskId = intent.getIntExtra("task_id", -1)
        if (taskId == -1) return

        val dao = db.taskDao()
        val repo = TaskRepository(dao)
        val task = dao.getTaskById(taskId) ?: return

        if (!task.isCompleted) {
            // 1. Cancel the scheduled alarm so it never fires again
            AlarmHelper.cancelReminder(context, taskId)
            // 2. Delegate DB work to Central Repository (handles recurring logic)
            val newlySpawnedTask = repo.handleTaskCompletion(task)
            // 3. Schedule newly spawned recurring task reminder
            if (newlySpawnedTask != null && newlySpawnedTask.hasReminder) {
                AlarmHelper.scheduleReminder(context, newlySpawnedTask)
            }
            Log.d(TAG, "Task $taskId marked done & recurring handled.")
        }

        manager.cancel(taskId)
    }

    private suspend fun handleTaskSnooze(
        context: Context, intent: Intent, db: AppDatabase, manager: NotificationManager
    ) {
        val taskId = intent.getIntExtra("task_id", -1)
        if (taskId == -1) return

        val task = db.taskDao().getTaskById(taskId) ?: return

        // Push forward by 10 min — local reschedule only, leaves original DB date intact for recurring logic
        val newTime = System.currentTimeMillis() + 600_000
        val snoozeTask = task.copy(dueDate = newTime)

        AlarmHelper.cancelReminder(context, taskId)
        AlarmHelper.scheduleReminder(context, snoozeTask)

        Log.d(TAG, "Task $taskId snoozed 10 mins to $newTime without mutating DB.")
        manager.cancel(taskId)
    }
}
