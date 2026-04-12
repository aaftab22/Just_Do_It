package com.darksunTechnologies.justdoit.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent ?: return)
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "GeofencingEvent Error: ${geofencingEvent?.errorCode}")
            return
        }

        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(context).taskDao()
                    val prefs = context.getSharedPreferences("geofence_throttle", Context.MODE_PRIVATE)
                    val now = System.currentTimeMillis()
                    val cooldownMillis = 30 * 60 * 1000 // 30 mins prevent immediate spam

                    for (geofence in triggeringGeofences) {
                        val taskIdStr = geofence.requestId
                        val taskId = taskIdStr.toIntOrNull() ?: continue

                        // Throttle Check
                        val lastTriggered = prefs.getLong(taskIdStr, 0L)
                        if (now - lastTriggered < cooldownMillis) {
                            Log.d("GeofenceReceiver", "Task $taskId throttled. Skipping.")
                            continue
                        }

                        // DB Validation check
                        val task = dao.getTaskById(taskId)
                        if (task != null && !task.isCompleted && task.hasLocationReminder) {
                            Log.d("GeofenceReceiver", "Geofence safely triggered for Task $taskId")
                            
                            // Save new throttle timestamp
                            prefs.edit().putLong(taskIdStr, now).apply()

                            // Delegate Notification visual building to ReminderReceiver
                            val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                                putExtra("task_id", task.id)
                                putExtra("task_name", task.name)
                                putExtra("is_location_trigger", true)
                            }
                            context.sendBroadcast(alarmIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeofenceReceiver", "Error processing geofences", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
