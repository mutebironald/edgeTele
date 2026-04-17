package com.dimaggi.edgetele.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dimaggi.edgetele.data.model.Incident

@Database(
    entities = [Incident::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class EdgeTeleDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        const val DATABASE_NAME = "edge_tele.db"
    }
}
