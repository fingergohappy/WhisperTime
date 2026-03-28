package com.example.whispertime.ui.project

import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveProject_persistsVibrationSetting() = runTest(dispatcher) {
        val dao = FakeProjectDao()
        val repository = ProjectRepository(dao)
        val viewModel = ProjectEditViewModel(projectId = null, projectRepository = repository)

        viewModel.projectName.value = "Deep Work"
        viewModel.timerMode.value = "COUNT_UP"
        viewModel.voiceIntervalSeconds.value = "30"
        viewModel.vibrationEnabled.value = true

        viewModel.saveProject()
        advanceUntilIdle()

        val savedProject = dao.projects.value.single()
        assertEquals("Deep Work", savedProject.name)
        assertTrue(savedProject.vibrationEnabled)
    }

    @Test
    fun init_existingProjectLoadsVibrationSetting() = runTest(dispatcher) {
        val dao = FakeProjectDao()
        val existingProject = ProjectEntity(
            id = 7L,
            name = "Focus",
            timerMode = "COUNTDOWN",
            defaultDurationMs = 1_500_000L,
            voiceIntervalMs = 60_000L,
            vibrationEnabled = true,
            prepareTimeSeconds = 5L,
            createdAt = 10L,
            updatedAt = 20L
        )
        dao.seed(existingProject)
        val repository = ProjectRepository(dao)

        val viewModel = ProjectEditViewModel(projectId = 7L, projectRepository = repository)
        advanceUntilIdle()

        assertTrue(viewModel.vibrationEnabled.value)
        assertEquals("60", viewModel.voiceIntervalSeconds.value)
        assertEquals("5", viewModel.prepareTimeSeconds.value)
    }

    @Test
    fun deleteProject_inCreateModeEmitsCompletionWithoutPersisting() = runTest(dispatcher) {
        val dao = FakeProjectDao()
        val repository = ProjectRepository(dao)
        val viewModel = ProjectEditViewModel(projectId = null, projectRepository = repository)
        val completions = mutableListOf<Boolean>()
        val collector = launch { viewModel.saveResult.collect { completions.add(it) } }

        viewModel.projectName.value = "Unsaved"
        viewModel.deleteProject()
        advanceUntilIdle()

        assertTrue(dao.projects.value.isEmpty())
        assertEquals(listOf(true), completions)
        collector.cancel()
    }

    @Test
    fun deleteProject_inEditModeDeletesProjectAndEmitsCompletion() = runTest(dispatcher) {
        val dao = FakeProjectDao()
        val existingProject = ProjectEntity(
            id = 9L,
            name = "Delete Me",
            timerMode = "COUNT_UP",
            defaultDurationMs = null,
            voiceIntervalMs = 30_000L,
            vibrationEnabled = false,
            prepareTimeSeconds = null,
            createdAt = 10L,
            updatedAt = 20L
        )
        dao.seed(existingProject)
        val repository = ProjectRepository(dao)
        val viewModel = ProjectEditViewModel(projectId = 9L, projectRepository = repository)
        val completions = mutableListOf<Boolean>()
        val collector = launch { viewModel.saveResult.collect { completions.add(it) } }
        advanceUntilIdle()

        viewModel.deleteProject()
        advanceUntilIdle()

        assertTrue(dao.projects.value.isEmpty())
        assertEquals(listOf(true), completions)
        collector.cancel()
    }
}

private class FakeProjectDao : ProjectDao {
    val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    fun seed(project: ProjectEntity) {
        projects.value = listOf(project)
    }

    override fun getAll(): Flow<List<ProjectEntity>> = projects

    override fun getById(id: Long): Flow<ProjectEntity?> = projects.map { projectList ->
        projectList.firstOrNull { it.id == id }
    }

    override suspend fun insert(project: ProjectEntity): Long {
        val newId = (projects.value.maxOfOrNull { it.id } ?: 0L) + 1L
        projects.value = projects.value + project.copy(id = newId)
        return newId
    }

    override suspend fun update(project: ProjectEntity) {
        projects.value = projects.value.map { current ->
            if (current.id == project.id) project else current
        }
    }

    override suspend fun delete(project: ProjectEntity) {
        projects.value = projects.value.filterNot { it.id == project.id }
    }
}
