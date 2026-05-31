package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrTaskDao {
    @Query("SELECT * FROM ocr_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<OcrTask>>
    
    @Query("SELECT * FROM ocr_tasks WHERE id = :id LIMIT 1")
    fun getTaskById(id: Int): Flow<OcrTask?>

    @Query("SELECT * FROM ocr_tasks WHERE md5 = :md5 LIMIT 1")
    suspend fun getTaskByMd5(md5: String): OcrTask?

    @Query("SELECT COUNT(*) FROM ocr_tasks")
    fun getTaskCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: OcrTask): Long

    @Update
    suspend fun updateTask(task: OcrTask)

    @Query("UPDATE ocr_tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Int, status: OcrStatus)

    @Query("DELETE FROM ocr_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
    @Query("DELETE FROM ocr_tasks")
    suspend fun deleteAllTasks()
}
