package com.example.ocr.paddle

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PaddleOcrSubmitRequest(
    val useDocOrientationClassify: Boolean,
    val useDocUnwarping: Boolean,
    val useChartRecognition: Boolean
)

@JsonClass(generateAdapter = true)
data class PaddleOcrSubmitResponse(
    val jobId: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class PaddleOcrJobStatusResponse(
    val id: String? = null,
    val state: String? = null,
    val error: String? = null,
    val extractProgress: ExtractProgress? = null,
    val data: JobData? = null
)

@JsonClass(generateAdapter = true)
data class ExtractProgress(
    val totalPages: Int? = null,
    val extractedPages: Int? = null
)

@JsonClass(generateAdapter = true)
data class JobData(
    val resultUrl: ResultUrl? = null
)

@JsonClass(generateAdapter = true)
data class ResultUrl(
    val jsonUrl: String? = null,
    val originalPdfUrl: String? = null
)

// For parsing the JSONL line
@JsonClass(generateAdapter = true)
data class PaddleJsonlLine(
    val result: PaddleResult? = null
)

@JsonClass(generateAdapter = true)
data class PaddleResult(
    val layoutParsingResults: List<Any>? = null,
    val markdown: MarkdownResult? = null,
    val outputImages: List<Any>? = null
)

@JsonClass(generateAdapter = true)
data class MarkdownResult(
    val text: String? = null,
    val images: List<Any>? = null
)
