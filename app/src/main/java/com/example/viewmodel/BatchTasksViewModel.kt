package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OcrStatus
import com.example.data.OcrTask
import com.example.data.OcrTaskRepository
import com.example.data.SettingsRepository
import com.example.ocr.OcrProviderFactory
import com.example.ocr.OcrProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class BatchTasksViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OcrTaskRepository
    private val settingsRepository = SettingsRepository(application)
    private val ocrSemaphore = Semaphore(2) // Max 2 concurrent OCR jobs

    init {
        val ocrTaskDao = AppDatabase.getDatabase(application).ocrTaskDao()
        val ocrTestRecordDao = AppDatabase.getDatabase(application).ocrTestRecordDao()
        repository = OcrTaskRepository(ocrTaskDao, ocrTestRecordDao)
    }

    val allTasks: StateFlow<List<OcrTask>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
    fun retryTask(task: OcrTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTaskStatus(task.id, OcrStatus.WAITING)
        }
    }
    
    fun processPendingTask(task: OcrTask) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.status == OcrStatus.WAITING) {
                // Instantly mark as UPLOADING/queued to avoid double launch from UI
                repository.updateTaskStatus(task.id, OcrStatus.PROCESSING)
                ocrSemaphore.withPermit {
                    processOcrForTask(task)
                }
            }
        }
    }

    private suspend fun processOcrForTask(task: OcrTask) {
        val file = File(task.localUri)
        if (!file.exists() || file.length() == 0L) {
            repository.updateTask(task.copy(
                status = OcrStatus.FAILED,
                errorStage = "PreCheck",
                errorMessage = "图片文件不可用 / 文件长度为 0 / Uri 转 File 失败"
            ))
            return
        }

        repository.updateTaskStatus(task.id, OcrStatus.PROCESSING)
        
        // --- 1. Preprocess Image ---
        val preprocessedPath = file.absolutePath.replace(".jpg", "_pre.jpg").replace(".png", "_pre.png")
        if (!preprocessedPath.endsWith("_pre.jpg") && !preprocessedPath.endsWith("_pre.png")) {
             // fallback
        }
        val preprocessedFile = File(preprocessedPath)
        com.example.ocr.processing.image.LedgerImagePreprocessor.preprocess(file, preprocessedFile)
        
        val devMode = settingsRepository.devModeFlow.first()
        
        // --- 2. Table Grid / ROI Detection ---
        var roiFile: File? = null
        if (devMode && preprocessedFile.exists()) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(preprocessedFile.absolutePath)
            if (bitmap != null) {
                val grid = com.example.ocr.processing.image.TableGridDetector.detect(bitmap)
                val roiPath = file.absolutePath.replace(".jpg", "_roi.jpg").replace(".png", "_roi.png")
                roiFile = File(roiPath)
                com.example.ocr.processing.image.DeliveryRoiComposer.compose(preprocessedFile, roiFile, grid)
                bitmap.recycle()
            }
        }
        
        repository.updateTaskStatus(task.id, OcrStatus.OCR_PROCESSING)
        try {
            val token = settingsRepository.getPaddleOcrToken()
            if (token.isNullOrBlank()) {
                repository.updateTask(task.copy(
                    status = OcrStatus.FAILED,
                    errorStage = "TOKEN_MISSING",
                    errorMessage = "PaddleOCR token 未配置"
                ))
                return
            }
            
            val options = settingsRepository.currentOptions.first().copy(apiKey = token)
            val providerType = settingsRepository.currentProvider.first()
            val provider = OcrProviderFactory.getProvider(providerType)
            
            var currentTaskState = task.copy(
                preprocessedImageUri = if (preprocessedFile.exists()) preprocessedFile.absolutePath else null,
                deliveryRoiImageUri = if (roiFile != null && roiFile.exists()) roiFile.absolutePath else null
            )
            repository.updateTask(currentTaskState)
            
            // --- 3. Full Page OCR ---
            val result = provider.recognizeImage(file, options) { progress ->
                currentTaskState = currentTaskState.copy(
                    status = progress.status,
                    errorStage = progress.errorStage ?: currentTaskState.errorStage,
                    errorMessage = progress.errorMessage ?: currentTaskState.errorMessage,
                    rawSubmitResponse = progress.rawSubmitResponse ?: currentTaskState.rawSubmitResponse,
                    rawPollResponse = progress.rawPollResponse ?: currentTaskState.rawPollResponse,
                    jobId = progress.jobId ?: currentTaskState.jobId,
                    jsonUrl = progress.jsonUrl ?: currentTaskState.jsonUrl,
                    jsonlHttpStatus = progress.jsonlHttpStatus ?: currentTaskState.jsonlHttpStatus,
                    jsonlBodyLength = progress.jsonlBodyLength ?: currentTaskState.jsonlBodyLength,
                    parsedTableCount = progress.parsedTableCount ?: currentTaskState.parsedTableCount,
                    ledgerRowsCount = progress.ledgerRowsCount ?: currentTaskState.ledgerRowsCount
                )
                repository.updateTask(currentTaskState)
            }
            
            // --- 4. ROI OCR ---
            var roiJsonlStr: String? = null
            var roiMarkdownStr: String? = null
            if (result.isSuccess && roiFile != null && roiFile.exists()) {
                 val roiResult = provider.recognizeImage(roiFile, options.copy(useDocUnwarping = false, useChartRecognition = false)) { /* ignore progress for second OCR to not overwrite main progress */ }
                 if (roiResult.isSuccess) {
                     roiJsonlStr = roiResult.jsonlText
                     roiMarkdownStr = roiResult.markdownText
                 }
            }
            
            if (result.isSuccess) {
                // Now parse
                var finalRowsCount = 0
                var finalTableCount = currentTaskState.parsedTableCount
                var finalStatus = OcrStatus.COMPLETED
                var errorCount = 0
                val jsonlStr = result.jsonlText
                if (!jsonlStr.isNullOrBlank()) {
                    try {
                        val ledgerPage = com.example.ocr.processing.LedgerPageBuilder.buildFromTask(currentTaskState.copy(deliveryRoiJsonlText = roiJsonlStr))
                        
                        if (ledgerPage != null) {
                            finalRowsCount = ledgerPage.rows.size
                            finalTableCount = ledgerPage.parsedTableCount
                            
                            errorCount = ledgerPage.rows.count { 
                                it.status == com.example.ocr.processing.RowStatus.NEED_MANUAL_REVIEW || 
                                it.status == com.example.ocr.processing.RowStatus.MISSING_DELIVERY ||
                                it.status == com.example.ocr.processing.RowStatus.ROW_MISALIGNED ||
                                it.status == com.example.ocr.processing.RowStatus.FAILED ||
                                it.status == com.example.ocr.processing.RowStatus.MISSING_DATE ||
                                it.status == com.example.ocr.processing.RowStatus.DUPLICATE_SUSPECTED ||
                                it.status == com.example.ocr.processing.RowStatus.INVALID_FORMAT
                            }
                            if (errorCount > 0) {
                                finalStatus = OcrStatus.NEED_REVIEW
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        finalStatus = OcrStatus.NEED_REVIEW
                    }
                } else {
                    finalStatus = OcrStatus.FAILED
                }
                
                val updatedTask = currentTaskState.copy(
                    status = finalStatus,
                    provider = result.provider,
                    jobId = result.jobId,
                    rawJobJson = result.rawJobJson,
                    jsonlText = result.jsonlText,
                    rawOcrText = result.markdownText,
                    rawOcrJson = null,
                    layoutParsingResultsJson = result.layoutParsingResultsJson,
                    outputImagesJson = result.outputImagesJson,
                    deliveryRoiJsonlText = roiJsonlStr,
                    deliveryRoiOcrText = roiMarkdownStr,
                    useDocOrientationClassify = options.useDocOrientationClassify,
                    useDocUnwarping = options.useDocUnwarping,
                    ledgerRowsCount = finalRowsCount,
                    parsedTableCount = finalTableCount
                )
                repository.updateTask(updatedTask)
            } else {
                val updatedTask = currentTaskState.copy(
                    status = OcrStatus.FAILED,
                    errorStage = "RecognizeResult",
                    errorMessage = result.errorMsg ?: "Unknown error"
                )
                repository.updateTask(updatedTask)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            repository.updateTaskStatus(task.id, OcrStatus.FAILED)
        }
    }

    fun deleteTask(task: OcrTask) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task.id)
            try {
                File(task.localUri).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun getLedgerPageForTask(task: OcrTask): com.example.ocr.processing.LedgerPage? {
        if (task.jsonlText.isNullOrBlank()) return null
        return try {
            com.example.ocr.processing.LedgerPageBuilder.buildFromTask(task)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun getUnitPrice(): Float {
        return settingsRepository.unitPriceFlow.first()
    }
}
