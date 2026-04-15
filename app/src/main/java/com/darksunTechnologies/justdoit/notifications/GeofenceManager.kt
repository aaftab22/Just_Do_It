package com.darksunTechnologies.justdoit.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.darksunTechnologies.justdoit.models.Task
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceManager {

    private const val TAG = "GeofenceManager"

    private fun getGeofencingClient(context: Context): GeofencingClient {
        return LocationServices.getGeofencingClient(context)
    }

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = "com.darksunTechnologies.justdoit.ACTION_GEOFENCE_EVENT"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        // Use a static request code (0) and deterministic Intent to ensure the PendingIntent is identical for add/remove
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(context: Context, task: Task) {
        // ALWAYS remove existing geofence before overriding
        removeGeofence(context, task.id)

        val lat = task.latitude
        val lng = task.longitude

        if (!task.hasLocationReminder || lat == null || lng == null) {
            Log.d(TAG, "Task ${task.id} missing location requirements. Aborting add.")
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(task.id.toString())
            .setCircularRegion(lat, lng, task.radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(0) // Safety: Off by default for controlled UX
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent(context)

        try {
            getGeofencingClient(context).addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully added geofence for task ${task.id}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add geofence for task ${task.id}. Reason: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception adding geofence: ${e.message}")
        }
    }

    fun removeGeofence(context: Context, taskId: Int) {
        try {
            getGeofencingClient(context).removeGeofences(listOf(taskId.toString()))
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully removed geofence for task $taskId")
                }
                .addOnFailureListener { e ->
                    Log.d(TAG, "No geofence removed for task $taskId (or error): ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing geofence: ${e.message}")
        }
    }
}
