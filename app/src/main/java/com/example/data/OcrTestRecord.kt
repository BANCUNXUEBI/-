package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_test_records")
data class OcrTestRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: String,
    val imagePath: String,
    val configName: String,
    val useDocOrientationClassify: Boolean,
    val useDocUnwarping: Boolean,
    val jobId: String? = null,
    val rawJobJson: String? = null,
    val jsonlText: String? = null,
    val markdownText: String? = null,
    val layoutParsingResultsJson: String? = null,
    val outputImagesJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val isSuccess: Boolean = false,
    val errorMsg: String? = null,
    val provider: String = "PADDLE_OCR",
    val model: String = "PaddleOCR-VL-1.6"
)
