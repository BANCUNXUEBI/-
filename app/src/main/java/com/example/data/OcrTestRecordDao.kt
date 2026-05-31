package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrTestRecordDao {
    @Insert
    suspend fun insert(record: OcrTestRecord)

    @Query("SELECT * FROM ocr_test_records WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getRecordsForTask(taskId: String): Flow<List<OcrTestRecord>>
    
    @Query("DELETE FROM ocr_test_records WHERE taskId = :taskId")
    suspend fun deleteRecordsForTask(taskId: String)
}
