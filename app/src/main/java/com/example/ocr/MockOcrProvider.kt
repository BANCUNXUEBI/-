package com.example.ocr

import kotlinx.coroutines.delay
import java.io.File

class MockOcrProvider : OcrProvider {
    override suspend fun recognizeImage(
        imageFile: File, 
        options: OcrOptions?,
        onProgress: (suspend (OcrTaskProgress) -> Unit)?
    ): OcrRawResult {
        onProgress?.invoke(OcrTaskProgress(status = com.example.data.OcrStatus.UPLOADING))
        delay(500)
        onProgress?.invoke(OcrTaskProgress(status = com.example.data.OcrStatus.JOB_SUBMITTED))
        delay(500)
        onProgress?.invoke(OcrTaskProgress(status = com.example.data.OcrStatus.POLLING))
        delay(500)
        onProgress?.invoke(OcrTaskProgress(status = com.example.data.OcrStatus.OCR_DONE))
        delay(500)
        
        val mockJsonl = """
            {"result": {"layoutParsingResults": [{"type": "text", "text": "3.21"}, {"type": "text", "text": "40/2"}, {"type": "text", "text": "2"}, {"type": "text", "text": "20"}]}}
        """.trimIndent()
        
        return OcrRawResult(
            provider = "MOCK",
            model = "MOCK_MODEL",
            imagePath = imageFile.absolutePath,
            jobId = "mock_job_12345",
            rawJobJson = "{\"status\": \"done\"}",
            jsonlText = mockJsonl,
            markdownText = "3.21\n40/2\n2\n20",
            layoutParsingResultsJson = "[{\"type\": \"text\", \"text\": \"3.21\"}]",
            outputImagesJson = "[]",
            isSuccess = true
        )
    }
}
