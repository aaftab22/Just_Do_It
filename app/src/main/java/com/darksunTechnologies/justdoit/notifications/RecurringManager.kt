package com.darksunTechnologies.justdoit.notifications

import com.darksunTechnologies.justdoit.models.RepeatType
import java.util.Calendar

object RecurringManager {
    fun calculateNextDueDate(currentDate: Long, type: RepeatType): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = currentDate
        when (type) {
            RepeatType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatType.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
            RepeatType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatType.NONE -> return currentDate
        }
        return cal.timeInMillis
    }
}
