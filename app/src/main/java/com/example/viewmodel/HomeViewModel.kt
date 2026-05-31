package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OcrStatus
import com.example.data.OcrTask
import com.example.data.OcrTaskRepository
import com.example.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ImportState(
    val isImporting: Boolean = false,
    val totalCount: Int = 0,
    val currentCount: Int = 0,
    val successCount: Int = 0,
    val fails: List<String> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OcrTaskRepository

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    init {
        val ocrTaskDao = AppDatabase.getDatabase(application).ocrTaskDao()
        repository = OcrTaskRepository(ocrTaskDao)
    }

    fun dismissImportState() {
        _importState.value = ImportState()
    }

    fun importImages(uris: List<Uri>, defaultNamePrefix: String = "gallery_img") {
        viewModelScope.launch(Dispatchers.IO) {
            _importState.value = _importState.value.copy(
                isImporting = true,
                totalCount = uris.size,
                currentCount = 0,
                successCount = 0,
                fails = emptyList()
            )

            var successes = 0
            val fails = mutableListOf<String>()

            for ((index, uri) in uris.withIndex()) {
                _importState.value = _importState.value.copy(currentCount = index + 1)
                try {
                    val result = FileUtil.copyUriToLocalAndCalculateMd5(getApplication(), uri)
                    if (result != null) {
                        val (localPath, md5) = result
                        val isDuplicate = repository.checkDuplicate(md5)
                        val status = if (isDuplicate) OcrStatus.DUPLICATE_SUSPECTED else OcrStatus.WAITING

                        val fileName = "${defaultNamePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"

                        val task = OcrTask(
                            localUri = localPath,
                            originalName = fileName,
                            md5 = md5,
                            status = status
                        )
                        repository.insertTask(task)
                        successes++
                    } else {
                        fails.add("Index $index: copyUriToLocalAndCalculateMd5 返回 null")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    fails.add("Index $index 失败: ${e.message}")
                }
            }

            _importState.value = _importState.value.copy(
                isImporting = false,
                successCount = successes,
                fails = fails
            )
        }
    }
}
