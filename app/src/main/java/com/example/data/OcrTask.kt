package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_tasks")
data class OcrTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localUri: String,
    val originalName: String,
    val md5: String,
    val status: OcrStatus = OcrStatus.WAITING,
    
    val preprocessedImageUri: String? = null,
    val deliveryRoiImageUri: String? = null,
    
    // OCR Results
    val provider: String = "MOCK",
    val jobId: String? = null,
    val rawJobJson: String? = null,
    val jsonlText: String? = null,
    val rawOcrText: String? = null, // Stores markdown text now
    val rawOcrJson: String? = null,
    val layoutParsingResultsJson: String? = null,
    val outputImagesJson: String? = null,
    
    val deliveryRoiJsonlText: String? = null,
    val deliveryRoiOcrText: String? = null,

    val errorStage: String? = null,
    val errorMessage: String? = null,
    val rawSubmitResponse: String? = null,
    val rawPollResponse: String? = null,
    val jsonUrl: String? = null,
    val jsonlHttpStatus: Int? = null,
    val jsonlBodyLength: Int? = null,
    val parsedTableCount: Int? = null,
    val ledgerRowsCount: Int? = null,
    
    // Configs used
    val useDocOrientationClassify: Boolean = false,
    val useDocUnwarping: Boolean = false,
    
    val createdAt: Long = System.currentTimeMillis()
)
