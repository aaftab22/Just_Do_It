package com.darksunTechnologies.justdoit.database

import androidx.room.TypeConverter
import com.darksunTechnologies.justdoit.models.RepeatType

class Converters {
    @TypeConverter
    fun fromRepeatType(value: RepeatType) = value.name

    @TypeConverter
    fun toRepeatType(value: String) = RepeatType.valueOf(value)
}
