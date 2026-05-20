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

/** 项目编辑 ViewModel 测试，覆盖震动配置保存、加载和删除行为。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectEditViewModelTest {

    /** 主线程测试调度器。 */
    private val dispatcher = StandardTestDispatcher()

    /** 每个测试前替换 Main dispatcher。 */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /** 每个测试后恢复 Main dispatcher。 */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 验证新建项目时会保存震动开关。 */
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

    /** 验证编辑已有项目时会加载震动和准备倒计时配置。 */
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

    /** 验证新建模式删除不会写入项目，只发送完成事件。 */
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

    /** 验证编辑模式删除会删除原项目并发送完成事件。 */
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

/** 项目编辑测试用内存 DAO。 */
private class FakeProjectDao : ProjectDao {
    /** 内存项目流。 */
    val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    /** 写入初始项目。 */
    fun seed(project: ProjectEntity) {
        projects.value = listOf(project)
    }

    /** 获取全部项目。 */
    override fun getAll(): Flow<List<ProjectEntity>> = projects

    /** 根据主键获取项目。 */
    override fun getById(id: Long): Flow<ProjectEntity?> = projects.map { projectList ->
        projectList.firstOrNull { it.id == id }
    }

    /** 插入项目并模拟自增主键。 */
    override suspend fun insert(project: ProjectEntity): Long {
        val newId = (projects.value.maxOfOrNull { it.id } ?: 0L) + 1L
        projects.value = projects.value + project.copy(id = newId)
        return newId
    }

    /** 更新项目。 */
    override suspend fun update(project: ProjectEntity) {
        projects.value = projects.value.map { current ->
            if (current.id == project.id) project else current
        }
    }

    /** 删除项目。 */
    override suspend fun delete(project: ProjectEntity) {
        projects.value = projects.value.filterNot { it.id == project.id }
    }
}
