package com.dimaggi.edgetele.data.repository

import com.dimaggi.edgetele.data.db.IncidentDao
import com.dimaggi.edgetele.data.model.Incident
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncidentRepository @Inject constructor(
    private val dao: IncidentDao
) {
    fun observeAll(): Flow<List<Incident>> = dao.observeAll()

    fun observeUnsyncedCount(): Flow<Int> = dao.observeUnsyncedCount()

    suspend fun save(incident: Incident) = dao.insert(incident)

    suspend fun getById(id: String): Incident? = dao.getById(id)

    suspend fun getUnsynced(): List<Incident> = dao.getUnsynced()

    suspend fun markSynced(id: String, syncedAtMs: Long) =
        dao.markSynced(id, syncedAtMs)

    suspend fun totalCount(): Int = dao.totalCount()

    // Requires explicit caller action — no silent deletes.
    suspend fun deleteById(id: String) = dao.deleteById(id)
}
