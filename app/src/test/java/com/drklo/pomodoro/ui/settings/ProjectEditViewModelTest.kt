package com.drklo.pomodoro.ui.settings

import com.drklo.pomodoro.data.model.Project
import com.drklo.pomodoro.data.repository.ProjectStore
import com.drklo.pomodoro.project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The first ViewModel test in this project, and the point of step T3: dependencies arrive through
 * the constructor now, so asking "does this screen know it has unsaved edits" no longer requires a
 * real Application, Room and DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val existing = project(id = 7, name = "Work")

    private class FakeProjects(private val stored: Project?) : ProjectStore {
        var added: Project? = null
        var updated: Project? = null

        override val projects: Flow<List<Project>> = flowOf(listOfNotNull(stored))
        override val allProjects: Flow<List<Project>> = projects

        override suspend fun getById(id: Long): Project? = stored?.takeIf { it.id == id }

        override suspend fun add(project: Project): Long {
            added = project
            return 1L
        }

        override suspend fun update(project: Project) {
            updated = project
        }

        override suspend fun archive(project: Project, now: Long): Boolean = true
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a freshly loaded project has nothing to save`() = runTest(dispatcher) {
        val vm = ProjectEditViewModel(FakeProjects(existing))
        vm.load(existing.id)
        runCurrent()

        assertEquals("Work", vm.project.value?.name)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test
    fun `an edit is remembered as unsaved until it is saved`() = runTest(dispatcher) {
        val store = FakeProjects(existing)
        val vm = ProjectEditViewModel(store)
        vm.load(existing.id)
        runCurrent()

        vm.edit { it.copy(focusMinutes = 40) }
        assertTrue("leaving now would silently drop the edit", vm.hasUnsavedChanges)

        vm.save(fallbackName = "Pomodoro") {}
        runCurrent()

        assertEquals(40, store.updated?.focusMinutes)
        assertFalse(vm.hasUnsavedChanges)
    }

    @Test
    fun `a nameless project falls back to the caller's localized name`() = runTest(dispatcher) {
        val store = FakeProjects(null)
        val vm = ProjectEditViewModel(store)
        vm.load(-1)
        runCurrent()

        vm.edit { it.copy(name = "   ") }
        vm.save(fallbackName = "Помидорка") {}
        runCurrent()

        // The fallback comes from resources, so a Russian user does not get a Latin name.
        assertEquals("Помидорка", store.added?.name)
    }

    @Test
    fun `saving reports back only after the write`() = runTest(dispatcher) {
        val vm = ProjectEditViewModel(FakeProjects(existing))
        vm.load(existing.id)
        runCurrent()

        var done = false
        vm.save(fallbackName = "Pomodoro") { done = true }
        assertFalse("the callback must not fire before the write", done)

        runCurrent()
        assertTrue(done)
    }
}
