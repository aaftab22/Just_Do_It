package com.darksunTechnologies.justdoit.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.util.Log
import com.darksunTechnologies.justdoit.database.AppDatabase
import com.darksunTechnologies.justdoit.datastore.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SmartNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SmartNotifListener"
        private const val COOLDOWN_MS = 30_000L
        private const val DUPLICATE_WINDOW_MS = 300_000L
    }

    private val whitelistedPackages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "com.google.android.apps.messaging", // standard Android messages
        "com.google.android.gm", // Gmail
        "com.microsoft.office.outlook"
    )

    // Maps packageName to the time of last successful suggestion
    private val packageCooldowns = ConcurrentHashMap<String, Long>()
    
    // Maps message text hashes to the time they were seen
    private val seenHashes = ConcurrentHashMap<Int, Long>()

    // Lazily initialized database instance — avoids calling getInstance() per notification
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected and actively bound.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected.")
        try {
            requestRebind(android.content.ComponentName(this, SmartNotificationListenerService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request rebind", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        if (sbn == null) return
        
        // 1. Check if user enabled the feature at all
        if (!ThemePreferences.isSmartCaptureEnabled(applicationContext)) return

        val packageName = sbn.packageName
        
        // 2. Check whitelist and call category
        if (!whitelistedPackages.contains(packageName)) return
        if (sbn.notification?.category == Notification.CATEGORY_CALL) return

        // Dispatch heavy parsing and DB-related logics to a background IO thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processNotificationSafely(sbn, packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from $packageName", e)
            }
        }
    }

    private suspend fun processNotificationSafely(sbn: StatusBarNotification, packageName: String) {
        val extras = sbn.notification?.extras ?: return

        // 3. Null-safe extraction, focusing on text contents safely
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getString(Notification.EXTRA_TITLE_BIG) ?: ""
            
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT_LINES)?.toString()
            ?: extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        
        if (text.length < 5) return // Too short to mean anything, even with strong intent

        // 4. Rate-limiting check per-package
        val now = System.currentTimeMillis()
        val lastSuggestionTime = packageCooldowns[packageName] ?: 0L
        if (now - lastSuggestionTime < COOLDOWN_MS) {
            return // Ignored (Cooldown)
        }

        // 5. Duplication Check (Hash text)
        val textHash = "$packageName $title $text".hashCode()
        cleanOldHashes(now)
        
        // Atomic check-and-set to prevent parallel race conditions hammering the AI
        if (seenHashes.putIfAbsent(textHash, now) != null) {
            return // Ignored (Duplicate)
        }
        
        // 6. Hybrid Router: Regex-first, AI-escalate if weak/null result
        val parsedTasks = SmartParseRouter.parseText(text, packageName, title, applicationContext, db)
        
        if (parsedTasks.isNotEmpty()) {
            // Router successfully extracted tasks (via regex or AI)
            for ((index, parsedTask) in parsedTasks.withIndex()) {
                // Generate a unique ID using the current time + index
                val notificationIdReference = now.hashCode() + index

                val appName = when (packageName) {
                    "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                    "org.telegram.messenger" -> "Telegram"
                    "com.google.android.apps.messaging" -> "Messages"
                    "com.google.android.gm" -> "Gmail"
                    "com.microsoft.office.outlook" -> "Outlook"
                    else -> "Notifications"
                }
                val desc = "Added from $appName" + 
                    (if (title.isNotBlank()) " ($title)" else "") + 
                    "\n[Parsed by: ${if (parsedTask.parserSource == "offline_ai") "Offline AI 🤖" else "Regex ⚡"}]\n\n" +
                    "Original Message:\n\"$text\""

                // Pop the suggestion
                NotificationHelper.showTaskSuggestionNotification(applicationContext, notificationIdReference, parsedTask, desc)
                Log.d(TAG, "Suggested Smart Task from $packageName: ${parsedTask.title}")
            }
            
            // Mark state variables
            packageCooldowns[packageName] = now
        }
    }

    private fun cleanOldHashes(now: Long) {
        val iterator = seenHashes.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > DUPLICATE_WINDOW_MS) {
                iterator.remove()
            }
        }
    }
}
