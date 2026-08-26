package com.drklo.pomodoro.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drklo.pomodoro.data.db.AppDatabase
import com.drklo.pomodoro.data.model.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Archiving against a real (in-memory) database — the rules only hold if SQL enforces them, and
 * neither the greyed-out button nor a JVM fake can prove that.
 */
@RunWith(AndroidJUnit4::class)
class ProjectArchiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProjectRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repo = ProjectRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun project(name: String) = Project(
        name = name,
        focusMinutes = 25,
        shortBreakMinutes = 5,
        pomodorosPerSession = 4,
        pomodoroColor = -1,
        breakColor = -2,
        dailyGoal = 4,
        longBreakEnabled = false,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        orderIndex = 0
    )

    @Test
    fun archivedProjectLeavesTheCarouselButStaysReadable() = runBlocking {
        val id = repo.add(project("Homework"))
        repo.add(project("Work"))

        assertTrue(repo.archive(project("Homework").copy(id = id), now = 1_700_000_000_000L))

        val active = repo.projects.first()
        assertEquals(listOf("Work"), active.map { it.name })

        val all = repo.allProjects.first()
        assertEquals(2, all.size)
        val archived = all.single { it.id == id }
        assertNotNull("the reports still need its name and colour", archived.name)
        assertTrue(archived.isArchived)
        assertEquals(1_700_000_000_000L, archived.archivedAt)
    }

    @Test
    fun theLastActiveProjectCannotBeArchived() = runBlocking {
        val first = repo.add(project("Only"))
        val second = repo.add(project("Second"))

        assertTrue(repo.archive(project("Second").copy(id = second)))
        // One left: refusing here is what keeps the main screen from having nothing to show.
        assertFalse(repo.archive(project("Only").copy(id = first)))

        assertEquals(1, repo.projects.first().size)
        assertNull(repo.allProjects.first().single { it.id == first }.archivedAt)
    }

    @Test
    fun archivingIsNotSeededOver() = runBlocking {
        repo.ensureSeeded(alreadySeeded = false)
        val seeded = repo.projects.first()
        assertEquals(3, seeded.size)

        repo.archive(seeded.first())
        assertEquals(2, repo.projects.first().size)

        // Seeding must not treat an archived project as a missing one and refill the list. Passing
        // false deliberately: the "already seeded" flag would short-circuit before the check this
        // test is about, which is that an archived row still counts as an existing one.
        repo.ensureSeeded(alreadySeeded = false)
        assertEquals(2, repo.projects.first().size)
        assertEquals(3, repo.allProjects.first().size)
    }
}
