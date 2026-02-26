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

class FakeTimingRecordDao : TimingRecordDao {
    val records = mutableListOf<TimingRecordEntity>()

    override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> =
        flowOf(records.filter { it.projectId == projectId })

    override fun getById(id: Long): Flow<TimingRecordEntity?> =
        flowOf(records.find { it.id == id })

    override suspend fun insert(record: TimingRecordEntity): Long {
        val newId = (records.maxOfOrNull { it.id } ?: 0) + 1
        records.add(record.copy(id = newId))
        return newId
    }

    override suspend fun update(record: TimingRecordEntity) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) records[index] = record
    }

    override suspend fun delete(record: TimingRecordEntity) {
        records.removeAll { it.id == record.id }
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        records.removeAll { ids.contains(it.id) }
    }

    override fun getTotalDuration(projectId: Long): Flow<Long?> =
        flowOf(records.filter { it.projectId == projectId }.sumOf { it.durationMs })

    override fun getRecordCount(projectId: Long): Flow<Int> =
        flowOf(records.count { it.projectId == projectId })
}

class TimingRecordRepositoryTest {

    private lateinit var fakeDao: FakeTimingRecordDao
    private lateinit var repository: TimingRecordRepository

    private fun baseRecord() = TimingRecordEntity(
        id = 1,
        projectId = 1,
        startTime = 1000L,
        endTime = 5000L,
        durationMs = 4000L,
        createdAt = 0L
    )

    @Before
    fun setup() {
        fakeDao = FakeTimingRecordDao()
        repository = TimingRecordRepository(fakeDao)
    }

    @Test
    fun editDuration_updatesEndTime() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.DURATION_MS, 6000L)

        val updated = fakeDao.records.first()
        assertEquals(6000L, updated.durationMs)
        assertEquals(7000L, updated.endTime)
        assertEquals(1000L, updated.startTime)
    }

    @Test
    fun editStartTime_updatesDuration() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.START_TIME, 2000L)

        val updated = fakeDao.records.first()
        assertEquals(2000L, updated.startTime)
        assertEquals(3000L, updated.durationMs)
        assertEquals(5000L, updated.endTime)
    }

    @Test
    fun editEndTime_updatesDuration() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.END_TIME, 3000L)

        val updated = fakeDao.records.first()
        assertEquals(3000L, updated.endTime)
        assertEquals(2000L, updated.durationMs)
        assertEquals(1000L, updated.startTime)
    }

    @Test
    fun editDuration_negativeDuration_clampsToMinimum() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.DURATION_MS, -1000L)

        val updated = fakeDao.records.first()
        assertEquals(1000L, updated.durationMs)
        assertEquals(2000L, updated.endTime)
        assertEquals(1000L, updated.startTime)
    }

    @Test
    fun editStartTime_afterEndTime_clampsToMinimum() = runTest {
        fakeDao.records.add(baseRecord())

        repository.updateRecordWithLinkedFields(baseRecord(), EditedField.START_TIME, 6000L)

        val updated = fakeDao.records.first()
        assertEquals(1000L, updated.durationMs)
        assertEquals(6000L, updated.startTime)
        assertEquals(7000L, updated.endTime)
    }

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
