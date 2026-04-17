package com.dimaggi.edgetele.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dimaggi.edgetele.data.model.Incident
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: Incident)

    @Update
    suspend fun update(incident: Incident)

    @Query("SELECT * FROM incidents ORDER BY timestampEpochMs DESC")
    fun observeAll(): Flow<List<Incident>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun getById(id: String): Incident?

    @Query("SELECT * FROM incidents WHERE synced = 0 ORDER BY timestampEpochMs ASC")
    suspend fun getUnsynced(): List<Incident>

    @Query("UPDATE incidents SET synced = 1, syncedAtMs = :syncedAtMs WHERE id = :id")
    suspend fun markSynced(id: String, syncedAtMs: Long)

    @Query("SELECT COUNT(*) FROM incidents WHERE synced = 0")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun totalCount(): Int

    // No auto-delete. Records persist until explicitly cleared.
    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteById(id: String)
}
