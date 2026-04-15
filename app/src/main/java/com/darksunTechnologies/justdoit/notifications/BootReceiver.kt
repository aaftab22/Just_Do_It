package com.darksunTechnologies.justdoit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                val taskDao = db.taskDao()

                val currentTime = System.currentTimeMillis()
                Log.d("time", currentTime.toString())

                val tasks = taskDao.getActiveReminders(currentTime)

                var count = 0

                for (task in tasks){
                    AlarmHelper.scheduleReminder(context, task)
                    count++
                }

                Log.d("BootReceiver","Rescheduled $count time reminders")

                // Re-register Geofences
                val locationTasks = taskDao.getActiveLocationTasks() // Limit 100 enforced natively in Dao
                var geofenceCount = 0
                for (task in locationTasks){
                    GeofenceManager.addGeofence(context, task)
                    geofenceCount++
                }

                Log.d("BootReceiver","Rescheduled $geofenceCount location reminders")
            } finally {
                pendingResult.finish()
            }
        }
    }
}