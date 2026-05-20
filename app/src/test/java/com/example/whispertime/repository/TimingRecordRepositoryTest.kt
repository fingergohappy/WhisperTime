package com.example.whispertime.repository

import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.EditedField
import com.example.whispertime.data.repository.TimingRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 测试用计时记录 DAO，使用内存列表模拟 Room 行为。 */
class FakeTimingRecordDao : TimingRecordDao {
    /** 内存记录列表。 */
    val records = mutableListOf<TimingRecordEntity>()

    /** 查询指定项目记录。 */
    override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        flowOf(records.filter { it.projectId == projectId })

    /** 根据主键查询记录。 */
    override fun getById(id: Long): Flow<TimingRecordEntity?> =
        flowOf(records.find { it.id == id })

    /** 插入记录并模拟自增主键。 */
    override suspend fun insert(record: TimingRecordEntity): Long {
        val newId = (records.maxOfOrNull { it.id } ?: 0) + 1
        records.add(record.copy(id = newId))
        return newId
    }

    /** 更新已有记录。 */
    override suspend fun update(record: TimingRecordEntity) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record
    }

    /** 删除单条记录。 */
    override suspend fun delete(record: TimingRecordEntity) {
        records.removeAll { it.id == record.id }
    }

    /** 批量删除记录。 */
    override suspend fun deleteByIds(ids: List<Long>) {
        records.removeAll { ids.contains(it.id) }
    }

    /** 统计项目累计时长。 */
    override fun getTotalDuration(projectId: Long): Flow<Long?> =
        flowOf(records.filter { it.projectId == projectId }.sumOf { it.durationMs })

    /** 统计项目记录数。 */
    override fun getRecordCount(projectId: Long): Flow<Int> =
        flowOf(records.count { it.projectId == projectId })
}

/** 计时记录仓库联动编辑规则测试。 */
class TimingRecordRepositoryTest {

    /** 测试用 DAO。 */
    private lateinit var fakeDao: FakeTimingRecordDao

    /** 被测仓库。 */
    private lateinit var repository: TimingRecordRepository

    /** 创建通用基础记录。 */
    private fun baseRecord() = TimingRecordEntity(
        id = 1,
        projectId = 1,
        startTime = 1000L,
        endTime = 5000L,
        durationMs = 4000L,
        createdAt = 0L
    )

    /** 每个测试前重建内存 DAO 和仓库。 */
    @Before
    fun setup() {
        fakeDao = FakeTimingRecordDao()
        repository = TimingRecordRepository(fakeDao)
    }

    /** 编辑持续时长时，结束时间随之联动。 */
    @Test
    fun editDuration_updatesEndTime() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.DURATION_MS, 6000L)

        val updated = fakeDao.records.first()
        assertEquals(6000L, updated.durationMs)
        assertEquals(7000L, updated.endTime)
        assertEquals(1000L, updated.startTime)
    }

    /** 编辑开始时间时，持续时长随之联动。 */
    @Test
    fun editStartTime_updatesDuration() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.START_TIME, 2000L)

        val updated = fakeDao.records.first()
        assertEquals(2000L, updated.startTime)
        assertEquals(3000L, updated.durationMs)
        assertEquals(5000L, updated.endTime)
    }

    /** 编辑结束时间时，持续时长随之联动。 */
    @Test
    fun editEndTime_updatesDuration() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.END_TIME, 3000L)

        val updated = fakeDao.records.first()
        assertEquals(3000L, updated.endTime)
        assertEquals(2000L, updated.durationMs)
        assertEquals(1000L, updated.startTime)
    }

    /** 持续时长为负数时会被钳制到最短时长。 */
    @Test
    fun editDuration_negativeDuration_clampsToMinimum() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.DURATION_MS, -1000L)

        val updated = fakeDao.records.first()
        assertEquals(1000L, updated.durationMs)
        assertEquals(2000L, updated.endTime)
        assertEquals(1000L, updated.startTime)
    }

    /** 开始时间晚于结束时间时会按最短时长重算结束时间。 */
    @Test
    fun editStartTime_afterEndTime_clampsToMinimum() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.START_TIME, 6000L)

        val updated = fakeDao.records.first()
        assertEquals(1000L, updated.durationMs)
        assertEquals(6000L, updated.startTime)
        assertEquals(7000L, updated.endTime)
    }

    /** 结束时间早于开始时间时会按最短时长重算结束时间。 */
    @Test
    fun editEndTime_beforeStartTime_clampsToMinimum() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.END_TIME, 500L)

        val updated = fakeDao.records.first()
        assertEquals(1000L, updated.durationMs)
        assertEquals(1000L, updated.startTime)
        assertEquals(2000L, updated.endTime)
    }
}
