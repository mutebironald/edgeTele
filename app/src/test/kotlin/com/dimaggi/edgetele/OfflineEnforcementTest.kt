package com.dimaggi.edgetele

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dimaggi.edgetele.data.db.EdgeTeleDatabase
import com.dimaggi.edgetele.data.repository.IncidentRepository
import com.dimaggi.edgetele.sync.SyncPacketGenerator
import com.dimaggi.edgetele.sync.SyncUploader
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that the sync pipeline short-circuits cleanly when no network is available.
 *
 * Robolectric's ConnectivityManager has no active network by default, so
 * SyncUploader.isOnline() returns false and syncNow() must return SyncResult.Offline
 * without attempting any HTTP calls or touching the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class OfflineEnforcementTest {

    private lateinit var db: EdgeTeleDatabase
    private lateinit var uploader: SyncUploader

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EdgeTeleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = IncidentRepository(db.incidentDao())
        val generator = SyncPacketGenerator(repository, Gson())
        uploader = SyncUploader(context, repository, generator)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `syncNow returns Offline when no network is available`() = runTest {
        val result = uploader.syncNow()
        assertTrue(
            "Expected SyncResult.Offline but got $result",
            result is SyncUploader.SyncResult.Offline
        )
    }

    @Test
    fun `syncNow does not throw when database is empty and offline`() = runTest {
        // Any unhandled exception here would fail the test
        uploader.syncNow()
    }
}
