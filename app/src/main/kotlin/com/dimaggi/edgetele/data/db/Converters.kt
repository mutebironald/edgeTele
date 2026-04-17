package com.dimaggi.edgetele.data.db

import androidx.room.TypeConverter
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language

class Converters {

    @TypeConverter
    fun fromIncidentCategory(value: IncidentCategory): String = value.name

    @TypeConverter
    fun toIncidentCategory(value: String): IncidentCategory =
        IncidentCategory.valueOf(value)

    @TypeConverter
    fun fromLanguage(value: Language): String = value.name

    @TypeConverter
    fun toLanguage(value: String): Language = Language.valueOf(value)

    @TypeConverter
    fun fromConfidenceLevel(value: ConfidenceLevel): String = value.name

    @TypeConverter
    fun toConfidenceLevel(value: String): ConfidenceLevel =
        ConfidenceLevel.valueOf(value)
}
