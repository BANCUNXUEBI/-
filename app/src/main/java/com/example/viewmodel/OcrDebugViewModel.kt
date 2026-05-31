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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File

class OcrDebugViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OcrTaskRepository
    private val settingsRepository = SettingsRepository(application)
    
    private val _taskState = MutableStateFlow<OcrTask?>(null)
    val taskState = _taskState.asStateFlow()
    
    private val _testRecords = MutableStateFlow<List<com.example.data.OcrTestRecord>>(emptyList())
    val testRecords = _testRecords.asStateFlow()
    
    private val _comparisonRunning = MutableStateFlow(false)
    val comparisonRunning = _comparisonRunning.asStateFlow()
    
    val currentOptions = settingsRepository.currentOptions
    val currentProvider = settingsRepository.currentProvider

    init {
        val ocrTaskDao = AppDatabase.getDatabase(application).ocrTaskDao()
        val ocrTestRecordDao = AppDatabase.getDatabase(application).ocrTestRecordDao()
        repository = OcrTaskRepository(ocrTaskDao, ocrTestRecordDao)
    }

    fun loadTask(taskId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = repository.getTaskById(taskId).firstOrNull()
            _taskState.value = task
            task?.id?.let {
                repository.getTestRecordsForTask(it.toString()).collect { records ->
                    _testRecords.value = records
                }
            }
        }
    }

    fun runComparisonTest() {
        val currentTask = _taskState.value ?: return
        val taskIdStr = currentTask.id.toString()
        if (_comparisonRunning.value) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _comparisonRunning.value = true
            try {
                val providerType = settingsRepository.currentProvider.first()
                val provider = OcrProviderFactory.getProvider(providerType)
                val testConfigs = listOf(
                    Pair(false, false),
                    Pair(true, false),
                    Pair(false, true),
                    Pair(true, true)
                )

                for ((orient, unwarp) in testConfigs) {
                    val configName = "Orient: ${orient}, Unwarp: ${unwarp}"
                    val options = com.example.ocr.OcrOptions(
                        useDocOrientationClassify = orient,
                        useDocUnwarping = unwarp,
                        useChartRecognition = false
                    )
                    
                    val startTime = System.currentTimeMillis()
                    val result = provider.recognizeImage(File(currentTask.localUri), options)
                    val duration = System.currentTimeMillis() - startTime
                    
                    val record = com.example.data.OcrTestRecord(
                        taskId = taskIdStr,
                        imagePath = currentTask.localUri,
                        configName = configName,
                        useDocOrientationClassify = orient,
                        useDocUnwarping = unwarp,
                        jobId = result.jobId,
                        rawJobJson = result.rawJobJson,
                        jsonlText = result.jsonlText,
                        markdownText = result.markdownText,
                        layoutParsingResultsJson = result.layoutParsingResultsJson,
                        outputImagesJson = result.outputImagesJson,
                        durationMs = duration,
                        isSuccess = result.isSuccess,
                        errorMsg = result.errorMsg,
                        provider = result.provider,
                        model = result.model
                    )
                    repository.insertTestRecord(record)
                }
            } finally {
                _comparisonRunning.value = false
            }
        }
    }

    fun saveSample(annotation: String) {
        val currentTask = _taskState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val samplesDir = File(context.filesDir, "ocr_debug_samples")
            if (!samplesDir.exists()) samplesDir.mkdirs()
            
            val timestamp = System.currentTimeMillis()
            val sampleFolder = File(samplesDir, "sample_${timestamp}")
            sampleFolder.mkdirs()
            
            File(currentTask.localUri).copyTo(File(sampleFolder, "original_image.jpg"), overwrite = true)
            
            File(sampleFolder, "markdown.md").writeText(currentTask.rawOcrText ?: "")
            File(sampleFolder, "result.jsonl").writeText(currentTask.jsonlText ?: "")
            File(sampleFolder, "layout_parsing.json").writeText(currentTask.layoutParsingResultsJson ?: "[]")
            File(sampleFolder, "annotation.txt").writeText(annotation)
            File(sampleFolder, "config.txt").writeText(
                "useDocOrientationClassify: ${currentTask.useDocOrientationClassify}\nuseDocUnwarping: ${currentTask.useDocUnwarping}"
            )
        }
    }

    fun toggleOrientation(current: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val options = currentOptions.first()
            settingsRepository.setOptions(current, options.useDocUnwarping)
        }
    }

    fun toggleUnwarping(current: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val options = currentOptions.first()
            settingsRepository.setOptions(options.useDocOrientationClassify, current)
        }
    }
    
    fun reRecognize() {
        val currentTask = _taskState.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTaskStatus(currentTask.id, OcrStatus.OCR_PROCESSING)
            _taskState.value = currentTask.copy(status = OcrStatus.OCR_PROCESSING)
            
            try {
                val providerType = settingsRepository.currentProvider.first()
                val options = settingsRepository.currentOptions.first()
                val provider = OcrProviderFactory.getProvider(providerType)
                
                val result = provider.recognizeImage(File(currentTask.localUri), options)
                if (result.isSuccess) {
                    val updatedTask = currentTask.copy(
                        status = OcrStatus.COMPLETED,
                        provider = result.provider,
                        jobId = result.jobId,
                        rawJobJson = result.rawJobJson,
                        jsonlText = result.jsonlText,
                        rawOcrText = result.markdownText,
                        rawOcrJson = null,
                        layoutParsingResultsJson = result.layoutParsingResultsJson,
                        outputImagesJson = result.outputImagesJson,
                        useDocOrientationClassify = options.useDocOrientationClassify,
                        useDocUnwarping = options.useDocUnwarping
                    )
                    repository.updateTask(updatedTask)
                    _taskState.value = updatedTask
                } else {
                    repository.updateTaskStatus(currentTask.id, OcrStatus.FAILED)
                    _taskState.value = currentTask.copy(status = OcrStatus.FAILED)
                }
            } catch (e: Exception) {
                repository.updateTaskStatus(currentTask.id, OcrStatus.FAILED)
                _taskState.value = currentTask.copy(status = OcrStatus.FAILED)
            }
        }
    }
}
