package com.darksunTechnologies.justdoit.notifications

import java.util.Calendar
import java.util.Locale

data class ParsedTask(
    val title: String,
    val dueDateMillis: Long?,
    val isHighPriority: Boolean,
    val parserSource: String = "regex"
)

object SmartTaskParser {

    private val ACTION_KEYWORDS = listOf(
        "buy", "call", "send", "pick", "bring", "meet", "pay", "book", 
        "take", "go", "pickup", "remind", "schedule", "read", "reply to"
    )
    private val INTENT_PHRASES = listOf("don't forget", "remember", "make sure", "reminder", "let's", "let s", "can you", "you need to")
    private val PRIORITY_KEYWORDS = listOf("urgent", "asap", "important", "emergency")
    
    // Negative filters -> do not trigger even if it looks like there is a task
    private val NEGATIVE_FILTERS = listOf(
        "delivered", "shipped", "order", "tracking", "otp", "offer", "discount",
        "will be delivered", "has been shipped", "incoming voice call", 
        "missed video call", "incoming video call", "missed voice call", 
        "audio call", "video call", "voice call", "missed call",
        "checking for new messages", "new messages", "whatsapp web is currently active"
    )

    // Non-task tones
    private val NON_TASK_TONES = listOf("i will", "we will", "you can", "you should", "do not", "don't", "i have to")

    // Questions -> do not trigger
    private val QUESTION_FILTERS = listOf("?", "will you", "are you", "would you")

    // Simple time regexes
    private val MILITARY_TIME_REGEX = Regex("\\b([0-1]?[0-9]|2[0-3]):([0-5][0-9])\\b")
    private val STANDARD_TIME_REGEX = Regex("\\b(1[0-2]|[1-9])(?:[:.]([0-5][0-9]))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
    // Very weak time: "at 7"
    private val WEAK_TIME_REGEX = Regex("\\bat\\s+(1[0-2]|[1-9])\\b", RegexOption.IGNORE_CASE)

    fun isNegativeMatch(text: String): Boolean {
        val lowerText = text.lowercase(Locale.getDefault())
            .replace("’", "'")
            .replace("‘", "'")
            .trim()
        return NEGATIVE_FILTERS.any { lowerText.contains("\\b$it\\b".toRegex()) }
    }

    fun parse(text: String): List<ParsedTask> {
        val lowerText = text.lowercase(Locale.getDefault())
            .replace("’", "'")
            .replace("‘", "'")
            .trim()
        
        // Split by "and", "then", or commas to support multiple tasks
        val chunks = lowerText.split(Regex("\\band\\b|\\bthen\\b|,"))
        val results = mutableListOf<ParsedTask>()
        
        // State variables to carry over time to previous tasks in the same sentence if they lacked time
        // e.g. "Buy milk and call John tomorrow" -> Buy milk (tomorrow), call John (tomorrow)
        var sharedDueDateMillis: Long? = null

        for (chunk in chunks.reversed()) { // iterate in reverse to bubble up time
            val parsed = parseSingle(chunk)
            if (parsed != null) {
                if (parsed.dueDateMillis != null) {
                    sharedDueDateMillis = parsed.dueDateMillis
                } else if (sharedDueDateMillis != null) {
                    // inherit time from the next task in the sentence
                    results.add(parsed.copy(dueDateMillis = sharedDueDateMillis))
                    continue
                }
                results.add(parsed)
            }
        }
        
        return results.reversed()
    }

    private fun parseSingle(text: String): ParsedTask? {
        val lowerText = text.trim()

        // 1. Minimum Word Count (at least 2 words)
        if (lowerText.split("\\s+".toRegex()).size < 2) {
            return null
        }

        // 2. Question Detection
        if (QUESTION_FILTERS.any { lowerText.contains(it) }) {
            return null
        }

        // 3. Intent Detection & Non-Task Tones
        val hasIntentPhrase = INTENT_PHRASES.any { lowerText.contains(it) }
        
        // Check for non-task tones UNLESS it's a known intent phrase like "don't forget"
        if (!hasIntentPhrase) {
            val hasNonTaskTone = NON_TASK_TONES.any { tone -> 
                lowerText.contains("\\b$tone\\b".toRegex()) 
            }
            if (hasNonTaskTone) {
                return null
            }
        }

        // A task MUST start with an action verb OR contain an intent phrase
        // It should not just contain a verb randomly.
        val startsWithVerb = ACTION_KEYWORDS.any { lowerText.startsWith(it + " ") || lowerText == it }
        if (!startsWithVerb && !hasIntentPhrase) {
            return null 
        }

        val isHighPriority = PRIORITY_KEYWORDS.any { lowerText.contains("\\b$it\\b".toRegex()) }
        
        // 4. Extract Date / Time
        var hasDate = false
        var timeFound = false
        var dueDateMillis: Long? = null

        val cal = Calendar.getInstance()

        // Handle Date explicitly
        if (lowerText.contains("\\btoday\\b".toRegex())) {
            hasDate = true
        } else if (lowerText.contains("\\btomorrow\\b".toRegex())) {
            hasDate = true
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (lowerText.contains("\\bnext week\\b".toRegex())) {
            hasDate = true
            cal.add(Calendar.DAY_OF_YEAR, 7)
        } else if (lowerText.contains("\\bthis weekend\\b".toRegex())) {
            hasDate = true
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            var daysToAdd = Calendar.SATURDAY - currentDay
            if (daysToAdd <= 0) daysToAdd += 7
            cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
        } else {
            // Check for days of the week
            val dayOfWeekRegex = Regex("\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE)
            val dayMatch = dayOfWeekRegex.find(lowerText)
            if (dayMatch != null) {
                hasDate = true
                val targetDay = when (dayMatch.groupValues[1].lowercase()) {
                    "sunday" -> Calendar.SUNDAY
                    "monday" -> Calendar.MONDAY
                    "tuesday" -> Calendar.TUESDAY
                    "wednesday" -> Calendar.WEDNESDAY
                    "thursday" -> Calendar.THURSDAY
                    "friday" -> Calendar.FRIDAY
                    "saturday" -> Calendar.SATURDAY
                    else -> -1
                }
                if (targetDay != -1) {
                    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                    var daysToAdd = targetDay - currentDay
                    // If it's today or in the past this week, jump to next week
                    if (daysToAdd <= 0) {
                        daysToAdd += 7
                    }
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }
        }

        // Handle part of the day mapping
        if (lowerText.contains("\\bmorning\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            timeFound = true
        } else if (lowerText.contains("\\bafternoon\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 14)
            cal.set(Calendar.MINUTE, 0)
            timeFound = true
        } else if (lowerText.contains("\\bevening\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 18)
            cal.set(Calendar.MINUTE, 0)
            timeFound = true
        } else if (lowerText.contains("\\bnight\\b".toRegex())) {
            cal.set(Calendar.HOUR_OF_DAY, 21)
            cal.set(Calendar.MINUTE, 0)
            timeFound = true
        }

        // Try standard time first (e.g. 5 pm)
        val stdTimeMatch = STANDARD_TIME_REGEX.find(lowerText)
        if (!timeFound && stdTimeMatch != null) {
            var hour = stdTimeMatch.groupValues[1].toInt()
            val minute = stdTimeMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val amPm = stdTimeMatch.groupValues[3].lowercase(Locale.getDefault())
            
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            timeFound = true
        } else if (!timeFound) {
            // Try military time (e.g. 17:00)
            val milTimeMatch = MILITARY_TIME_REGEX.find(lowerText)
            if (milTimeMatch != null) {
                val hour = milTimeMatch.groupValues[1].toInt()
                val minute = milTimeMatch.groupValues[2].toInt()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                timeFound = true
            } else {
                // Try weak time "at 7"
                val weakTimeMatch = WEAK_TIME_REGEX.find(lowerText)
                if (weakTimeMatch != null) {
                    var hour = weakTimeMatch.groupValues[1].toInt()
                    // Default to PM if ambiguous and in reasonable range (1-11)
                    if (hour in 1..11) {
                        hour += 12
                    }
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    timeFound = true
                }
            }
        }

        if (timeFound || hasDate) {
            // If time was specified but it's already past for "today", move to tomorrow
            if (timeFound && cal.timeInMillis <= System.currentTimeMillis() && !lowerText.contains("\\btomorrow\\b".toRegex())) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            } else if (!timeFound && lowerText.contains("\\btoday\\b".toRegex())) {
                 // "today" but no time -> schedule it 2 hours from now as a sensible default
                 cal.add(Calendar.HOUR_OF_DAY, 2)
            }
            dueDateMillis = cal.timeInMillis
        }

        // Clean up the title 
        val title = text.replace('\n', ' ').trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }.take(100)

        return ParsedTask(
            title = title,
            dueDateMillis = dueDateMillis,
            isHighPriority = isHighPriority
        )
    }
}
