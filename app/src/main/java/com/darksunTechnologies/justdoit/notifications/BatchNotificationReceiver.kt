package com.darksunTechnologies.justdoit.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import androidx.core.content.edit

/**
 * Handles Snooze and Silence actions for the batch queue summary notification.
 *
 * - SNOOZE: Dismisses the notification and re-shows it after 30 minutes.
 * - SILENCE_TODAY: Dismisses the notification and prevents re-showing until midnight.
 *   Data is NEVER deleted — only the notification is silenced.
 */
class BatchNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.darksunTechnologies.justdoit.ACTION_BATCH_SNOOZE"
        const val ACTION_SILENCE_TODAY = "com.darksunTechnologies.justdoit.ACTION_BATCH_SILENCE_TODAY"
        private const val TAG = "BatchNotifReceiver"
        private const val PREFS_NAME = "batch_notification_prefs"
        private const val KEY_SILENCED_UNTIL = "silenced_until"
        const val BATCH_NOTIFICATION_ID = 999_999

        /** Check if the user has silenced batch notifications for today. */
        fun isSilenced(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val silencedUntil = prefs.getLong(KEY_SILENCED_UNTIL, 0L)
            return System.currentTimeMillis() < silencedUntil
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        // Always dismiss the current notification first
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(BATCH_NOTIFICATION_ID)

        when (action) {
            ACTION_SNOOZE -> {
                Log.d(TAG, "Snoozed batch notification for 30 minutes")
                // Schedule re-show in 30 minutes
                val snoozeIntent = Intent(context, BatchNotificationReceiver::class.java).apply {
                    this.action = "com.darksunTechnologies.justdoit.ACTION_BATCH_RESHOW"
                }
                val pi = PendingIntent.getBroadcast(
                    context, 0, snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.set(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + 30 * 60 * 1000L, // 30 minutes
                    pi
                )
            }

            ACTION_SILENCE_TODAY -> {
                Log.d(TAG, "Silenced batch notifications until midnight")
                // Calculate midnight tonight
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

            "com.darksunTechnologies.justdoit.ACTION_BATCH_RESHOW" -> {
                Log.d(TAG, "Snooze expired — re-showing batch notification")
                // Re-show the notification by checking the queue count
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val db = com.darksunTechnologies.justdoit.database.AppDatabase.getInstance(context)
                        val count = db.queuedMessageDao().getPendingCountSync()
                        if (count > 0 && !isSilenced(context)) {
                            NotificationHelper.showBatchQueueNotification(context, count)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error re-showing batch notification", e)
                    }
                }
            }
        }
    }
}
