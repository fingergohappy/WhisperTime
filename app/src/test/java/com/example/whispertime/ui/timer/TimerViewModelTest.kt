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

/** 计时页 ViewModel 测试，覆盖项目删除事件。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

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

    /** 验证删除当前项目会删除项目并发出完成事件。 */
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

/** 测试用应用 Context，只提供启动服务返回值。 */
private class FakeAppContext : ContextWrapper(null) {
    /** 模拟 startService 返回组件名。 */
    override fun startService(service: Intent): ComponentName = ComponentName("test", "TimerService")
}

/** 计时 ViewModel 测试用项目 DAO。 */
private class FakeTimerProjectDao : ProjectDao {
    /** 内存项目流。 */
    val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    /** 写入初始项目。 */
    fun seed(project: ProjectEntity) {
        projects.value = listOf(project)
    }

    /** 获取全部项目。 */
    override fun getAll(): Flow<List<ProjectEntity>> = projects

    /** 根据主键获取项目。 */
    override fun getById(id: Long): Flow<ProjectEntity?> = projects.map { list ->
        list.firstOrNull { it.id == id }
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

/** 计时 ViewModel 测试用记录 DAO。 */
private class FakeTimingRecordDao : TimingRecordDao {
    /** 内存记录流。 */
    private val records = MutableStateFlow<List<TimingRecordEntity>>(emptyList())

    /** 查询指定项目记录。 */
    override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        records.map { list -> list.filter { it.projectId == projectId } }

    /** 根据主键查询记录。 */
    override fun getById(id: Long): Flow<TimingRecordEntity?> =
        records.map { list -> list.firstOrNull { it.id == id } }

    /** 插入记录并模拟自增主键。 */
    override suspend fun insert(record: TimingRecordEntity): Long {
        val newId = (records.value.maxOfOrNull { it.id } ?: 0L) + 1L
        records.value = records.value + record.copy(id = newId)
        return newId
    }

    /** 更新记录。 */
    override suspend fun update(record: TimingRecordEntity) {
        records.value = records.value.map { current ->
            if (current.id == record.id) record else current
        }
    }

    /** 删除记录。 */
    override suspend fun delete(record: TimingRecordEntity) {
        records.value = records.value.filterNot { it.id == record.id }
    }

    /** 批量删除记录。 */
    override suspend fun deleteByIds(ids: List<Long>) {
        records.value = records.value.filterNot { it.id in ids }
    }

    /** 统计项目累计时长。 */
    override fun getTotalDuration(projectId: Long): Flow<Long?> =
        records.map { list ->
            list.filter { it.projectId == projectId }.sumOf { it.durationMs }.takeIf { it > 0L }
        }

    /** 统计项目记录数。 */
    override fun getRecordCount(projectId: Long): Flow<Int> =
        records.map { list -> list.count { it.projectId == projectId } }
}
