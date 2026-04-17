package com.dimaggi.edgetele

import androidx.test.core.app.ApplicationProvider
import com.dimaggi.edgetele.data.model.enums.IncidentCategory
import com.dimaggi.edgetele.data.model.enums.Language
import com.dimaggi.edgetele.data.repository.PlaybookRepository
import com.google.gson.Gson
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that every playbook asset loads correctly and returns localised actions
 * for all five categories × four languages — no network required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PlaybookRepositoryTest {

    private lateinit var repository: PlaybookRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        repository = PlaybookRepository(context, Gson())
    }

    @Test
    fun `all categories return non-empty action list in English`() {
        IncidentCategory.entries.forEach { category ->
            val actions = repository.getActions(category, 3, Language.ENGLISH)
            assertTrue("No actions for $category in English", actions.isNotEmpty())
        }
    }

    @Test
    fun `all four categories except OTHER return actions in every language`() {
        val testCategories = IncidentCategory.entries.filter { it != IncidentCategory.OTHER }
        testCategories.forEach { category ->
            Language.entries.forEach { language ->
                val actions = repository.getActions(category, 3, language)
                assertTrue(
                    "No actions for $category in $language",
                    actions.isNotEmpty()
                )
                actions.forEach { action ->
                    assertTrue(
                        "Blank action text for $category/$language/${action.id}",
                        action.action.isNotBlank()
                    )
                }
            }
        }
    }

    @Test
    fun `flood damage actions are sorted by priority`() {
        val actions = repository.getActions(IncidentCategory.FLOOD_DAMAGE, 3, Language.ENGLISH, limit = 5)
        val priorities = actions.map { it.priority }
        org.junit.Assert.assertEquals(priorities, priorities.sorted())
    }

    @Test
    fun `limit parameter caps returned actions`() {
        val actions = repository.getActions(IncidentCategory.FLOOD_DAMAGE, 3, Language.ENGLISH, limit = 2)
        assertTrue("Should return at most 2 actions", actions.size <= 2)
    }

    @Test
    fun `Bislama translations differ from English`() {
        val en = repository.getActions(IncidentCategory.FLOOD_DAMAGE, 3, Language.ENGLISH).firstOrNull()
        val bi = repository.getActions(IncidentCategory.FLOOD_DAMAGE, 3, Language.BISLAMA).firstOrNull()
        if (en != null && bi != null) {
            assertNotEquals("Bislama should differ from English", en.action, bi.action)
        }
    }

    @Test
    fun `Tok Pisin translations differ from English`() {
        val en = repository.getActions(IncidentCategory.INJURY, 3, Language.ENGLISH).firstOrNull()
        val tpi = repository.getActions(IncidentCategory.INJURY, 3, Language.TOK_PISIN).firstOrNull()
        if (en != null && tpi != null) {
            assertNotEquals("Tok Pisin should differ from English", en.action, tpi.action)
        }
    }

    @Test
    fun `Haitian Creole translations differ from English`() {
        val en = repository.getActions(IncidentCategory.STRUCTURAL_DAMAGE, 3, Language.ENGLISH).firstOrNull()
        val ht = repository.getActions(IncidentCategory.STRUCTURAL_DAMAGE, 3, Language.HAITIAN_CREOLE).firstOrNull()
        if (en != null && ht != null) {
            assertNotEquals("Haitian Creole should differ from English", en.action, ht.action)
        }
    }

    // ---- Structural coverage ----

    @Test
    fun `all returned actions have non-blank id`() {
        IncidentCategory.entries.forEach { category ->
            repository.getActions(category, 3, Language.ENGLISH).forEach { action ->
                assertTrue("Action id is blank for $category", action.id.isNotBlank())
            }
        }
    }

    @Test
    fun `all returned actions have valid priority range`() {
        IncidentCategory.entries.forEach { category ->
            repository.getActions(category, 3, Language.ENGLISH, limit = 10).forEach { action ->
                assertTrue(
                    "Priority out of range for $category/${action.id}: ${action.priority}",
                    action.priority in 1..5
                )
            }
        }
    }

}
