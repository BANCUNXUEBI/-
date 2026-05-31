package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OcrTask
import com.example.data.OcrTaskRepository
import com.example.data.SettingsRepository
import com.example.ocr.processing.LedgerPage
import com.example.ocr.processing.PaddleHtmlTableLedgerParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class OcrPreviewState(
    val isLoading: Boolean = true,
    val task: OcrTask? = null,
    val ledgerPage: LedgerPage? = null,
    val errorMessage: String? = null
)

class OcrPreviewViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OcrTaskRepository
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(OcrPreviewState())
    val uiState: StateFlow<OcrPreviewState> = _uiState.asStateFlow()
    
    suspend fun getUnitPrice(): Float {
        return settingsRepository.unitPriceFlow.first()
    }

    init {
        val ocrTaskDao = AppDatabase.getDatabase(application).ocrTaskDao()
        val ocrTestRecordDao = AppDatabase.getDatabase(application).ocrTestRecordDao()
        repository = OcrTaskRepository(ocrTaskDao, ocrTestRecordDao)
    }

    fun loadTask(taskId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = repository.getTaskById(taskId).firstOrNull()
            if (task == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "找不到对应的任务数据"
                )
                return@launch
            }

            try {
                // Parse on the fly from jsonlText stored in DB
                val page = com.example.ocr.processing.LedgerPageBuilder.buildFromTask(task)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    task = task,
                    ledgerPage = page
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    task = task,
                    errorMessage = "解析数据失败: ${e.message}"
                )
            }
        }
    }
    
    fun confirmAndLockPage(taskId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = repository.getTaskById(taskId).firstOrNull() ?: return@launch
            repository.updateTask(task.copy(status = com.example.data.OcrStatus.LOCKED_FOR_BILLING))
        }
    }
    
    fun updateRowManual(rowId: String, newDeliveryText: String, newStatus: com.example.ocr.processing.RowStatus) {
        val currentContext = _uiState.value
        val page = currentContext.ledgerPage ?: return
        
        val newRows = page.rows.map { row ->
            if (row.id == rowId) {
                val parts = newDeliveryText.split("/")
                val sets = if (parts.size == 2) parts[0].toIntOrNull() else row.sets
                row.copy(
                   normalizedDeliveryText = newDeliveryText, 
                   status = newStatus,
                   sets = sets
                )
            } else {
                row
            }
        }
        
        _uiState.value = currentContext.copy(
             ledgerPage = com.example.ocr.processing.LedgerPage(
                 customerNameRaw = page.customerNameRaw,
                 customerMatchStatus = page.customerMatchStatus,
                 parsedTableCount = page.parsedTableCount,
                 ignoredNoises = page.ignoredNoises,
                 rows = newRows.toList(),
                 confidenceScore = page.confidenceScore,
                 confidenceLevel = page.confidenceLevel,
                 confidenceReasons = page.confidenceReasons,
                 warnings = page.warnings
             )
        )
    }
}
