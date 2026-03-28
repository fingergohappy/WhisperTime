package com.example.whispertime.ui.timer

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import com.example.whispertime.data.local.dao.ProjectDao
import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.ProjectEntity
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.ProjectRepository
import com.example.whispertime.data.repository.TimingRecordRepository
import com.example.whispertime.timer.TimeSource
import com.example.whispertime.timer.TimerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

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
    fun deleteCurrentProject_deletesProjectAndEmitsCompletion() = runTest(dispatcher) {
        val projectDao = FakeTimerProjectDao()
        projectDao.seed(
            ProjectEntity(
                id = 5L,
                name = "Focus",
                timerMode = "COUNT_UP",
                defaultDurationMs = null,
                voiceIntervalMs = 30_000L,
                vibrationEnabled = false,
                prepareTimeSeconds = null,
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        val timingRecordDao = FakeTimingRecordDao()
        val viewModel = TimerViewModel(
            projectId = 5L,
            appContext = FakeAppContext(),
            projectRepository = ProjectRepository(projectDao),
            timingRecordRepository = TimingRecordRepository(timingRecordDao),
            timerEngine = TimerEngine(
                timeSource = TimeSource { 0L },
                wallClockTimeSource = { 0L },
                coroutineScope = backgroundScope
            )
        )
        val completions = mutableListOf<Boolean>()
        val collector = launch { viewModel.deleteResult.collect { completions.add(it) } }
        advanceUntilIdle()

        viewModel.deleteCurrentProject()
        advanceUntilIdle()

        assertTrue(projectDao.projects.value.isEmpty())
        assertEquals(listOf(true), completions)
        collector.cancel()
    }
}

private class FakeAppContext : ContextWrapper(null) {
    override fun startService(service: Intent): ComponentName = ComponentName("test", "TimerService")
}

private class FakeTimerProjectDao : ProjectDao {
    val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    fun seed(project: ProjectEntity) {
        projects.value = listOf(project)
    }

    override fun getAll(): Flow<List<ProjectEntity>> = projects

    override fun getById(id: Long): Flow<ProjectEntity?> = projects.map { list ->
        list.firstOrNull { it.id == id }
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

private class FakeTimingRecordDao : TimingRecordDao {
    private val records = MutableStateFlow<List<TimingRecordEntity>>(emptyList())

    override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        records.map { list -> list.filter { it.projectId == projectId } }

    override fun getById(id: Long): Flow<TimingRecordEntity?> =
        records.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun insert(record: TimingRecordEntity): Long {
        val newId = (records.value.maxOfOrNull { it.id } ?: 0L) + 1L
        records.value = records.value + record.copy(id = newId)
        return newId
    }

    override suspend fun update(record: TimingRecordEntity) {
        records.value = records.value.map { current ->
            if (current.id == record.id) record else current
        }
    }

    override suspend fun delete(record: TimingRecordEntity) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        records.value = records.value.filterNot { it.id in ids }
    }

    override fun getTotalDuration(projectId: Long): Flow<Long?> =
        records.map { list ->
            list.filter { it.projectId == projectId }.sumOf { it.durationMs }.takeIf { it > 0L }
        }

    override fun getRecordCount(projectId: Long): Flow<Int> =
        records.map { list -> list.count { it.projectId == projectId } }
}
