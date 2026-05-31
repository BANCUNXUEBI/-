package com.example.data

import kotlinx.coroutines.flow.Flow

class OcrTaskRepository(private val dao: OcrTaskDao, private val testRecordDao: OcrTestRecordDao? = null) {
    val allTasks: Flow<List<OcrTask>> = dao.getAllTasks()
    
    fun getTaskById(id: Int): Flow<OcrTask?> = dao.getTaskById(id)
    
    fun getTestRecordsForTask(taskId: String): Flow<List<OcrTestRecord>> {
        return testRecordDao?.getRecordsForTask(taskId) ?: kotlinx.coroutines.flow.emptyFlow()
    }

    suspend fun insertTestRecord(record: OcrTestRecord) {
        testRecordDao?.insert(record)
    }

    suspend fun checkDuplicate(md5: String): Boolean {
        return dao.getTaskByMd5(md5) != null
    }

    suspend fun insertTask(task: OcrTask): Long {
        return dao.insertTask(task)
    }
    
    suspend fun updateTask(task: OcrTask) {
        dao.updateTask(task)
    }

    suspend fun updateTaskStatus(id: Int, status: OcrStatus) {
        dao.updateTaskStatus(id, status)
    }

    suspend fun deleteTask(id: Int) {
        dao.deleteTaskById(id)
    }
}
