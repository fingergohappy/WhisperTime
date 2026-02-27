package com.example.whispertime.ui.record

import com.example.whispertime.data.local.dao.TimingRecordDao
import com.example.whispertime.data.local.entity.TimingRecordEntity
import com.example.whispertime.data.repository.TimingRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class RecordEditViewModelTest {

    private lateinit var fakeDao: FakeTimingRecordDao
    private lateinit var repository: TimingRecordRepository
    private val testDispatcher = StandardTestDispatcher()
    private val recordId = 1L
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeTimingRecordDao()
        repository = TimingRecordRepository(fakeDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads record data`() = runTest(testDispatcher) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + 60000
        val record = TimingRecordEntity(
            id = recordId,
            projectId = 1,
            startTime = startTime,
            endTime = endTime,
            durationMs = 60000,
            createdAt = startTime
        )
        fakeDao.addRecord(record)

        val viewModel = RecordEditViewModel(recordId, repository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(dateFormat.format(Date(startTime)), viewModel.startTimeText.value)
        assertEquals(dateFormat.format(Date(endTime)), viewModel.endTimeText.value)
        assertEquals("01:00", viewModel.durationText.value)
    }

    @Test
    fun `saveRecord updates repository with new values`() = runTest(testDispatcher) {
        val startTime = System.currentTimeMillis()
        val startTimeTruncated = dateFormat.parse(dateFormat.format(Date(startTime)))!!.time
        
        val endTime = startTime + 60000
        val record = TimingRecordEntity(
            id = recordId,
            projectId = 1,
            startTime = startTime,
            endTime = endTime,
            durationMs = 60000,
            createdAt = startTime
        )
        fakeDao.addRecord(record)

        val viewModel = RecordEditViewModel(recordId, repository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onDurationChanged("02:00")
        
        viewModel.saveRecord()
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedRecord = fakeDao.getRecord(recordId)
        
        assertEquals(120000L, updatedRecord?.durationMs)
        assertEquals(startTimeTruncated + 120000L, updatedRecord?.endTime)
    }

    class FakeTimingRecordDao : TimingRecordDao {
        private val records = MutableStateFlow<List<TimingRecordEntity>>(emptyList())

        fun addRecord(record: TimingRecordEntity) {
            val current = records.value.toMutableList()
            current.add(record)
            records.value = current
        }

        fun getRecord(id: Long): TimingRecordEntity? {
            return records.value.find { it.id == id }
        }

        override fun getByProjectId(projectId: Long): Flow<List<TimingRecordEntity>> {
            return records.map { list -> list.filter { it.projectId == projectId } }
        }

        override fun getById(id: Long): Flow<TimingRecordEntity?> {
            return records.map { list -> list.find { it.id == id } }
        }

        override suspend fun insert(record: TimingRecordEntity): Long {
            val current = records.value.toMutableList()
            current.add(record)
            records.value = current
            return record.id
        }

        override suspend fun update(record: TimingRecordEntity) {
            val current = records.value.toMutableList()
            val index = current.indexOfFirst { it.id == record.id }
            if (index != -1) {
                current[index] = record
                records.value = current
            }
        }

        override suspend fun delete(record: TimingRecordEntity) {
            val current = records.value.toMutableList()
            current.remove(record)
            records.value = current
        }

        override suspend fun deleteByIds(ids: List<Long>) {
            val current = records.value.toMutableList()
            current.removeAll { ids.contains(it.id) }
            records.value = current
        }

        override fun getTotalDuration(projectId: Long): Flow<Long?> {
             return records.map { list -> 
                 list.filter { it.projectId == projectId }.sumOf { it.durationMs }.takeIf { it > 0 }
             }
        }

        override fun getRecordCount(projectId: Long): Flow<Int> {
             return records.map { list -> 
                 list.count { it.projectId == projectId }
             }
        }
    }
}
