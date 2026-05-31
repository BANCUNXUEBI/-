package com.example.ocr

import com.example.data.OcrStatus
import java.io.File

data class OcrTaskProgress(
    val status: OcrStatus,
    val errorStage: String? = null,
    val errorMessage: String? = null,
    val rawSubmitResponse: String? = null,
    val rawPollResponse: String? = null,
    val jobId: String? = null,
    val jsonUrl: String? = null,
    val jsonlHttpStatus: Int? = null,
    val jsonlBodyLength: Int? = null,
    val parsedTableCount: Int? = null,
    val ledgerRowsCount: Int? = null
)

interface OcrProvider {
    suspend fun recognizeImage(
        imageFile: File, 
        options: OcrOptions? = null,
        onProgress: (suspend (OcrTaskProgress) -> Unit)? = null
    ): OcrRawResult
}

data class OcrOptions(
    val useDocOrientationClassify: Boolean = false,
    val useDocUnwarping: Boolean = false,
    val useChartRecognition: Boolean = false,
    val apiKey: String? = null
)

data class OcrRawResult(
    val provider: String,
    val model: String,
    val imagePath: String,
    val jobId: String? = null,
    val rawJobJson: String? = null,
    val jsonlText: String? = null,
    val markdownText: String? = null,
    val layoutParsingResultsJson: String? = null,
    val outputImagesJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val errorMsg: String? = null
)
