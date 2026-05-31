package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.OcrStatus
import com.example.data.OcrTask
import com.example.viewmodel.BatchTasksViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchTasksScreen(
    viewModel: BatchTasksViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDebug: (Int) -> Unit,
    onNavigateToPreview: (Int) -> Unit,
    onNavigateToReview: (Int) -> Unit
) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { com.example.data.SettingsRepository(context) }
    val devMode by prefs.devModeFlow.collectAsState(initial = false)
    val unitPrice by prefs.unitPriceFlow.collectAsState(initial = 0.9f)
    
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.status == OcrStatus.COMPLETED || it.status == OcrStatus.LOCKED_FOR_BILLING || it.status == OcrStatus.HUMAN_CONFIRMED }
    val pendingTasks = tasks.count { it.status == OcrStatus.WAITING || it.status == OcrStatus.PROCESSING || it.status == OcrStatus.OCR_PROCESSING }
    val needReviewTasks = tasks.count { it.status == OcrStatus.NEED_REVIEW }
    val failedTasks = tasks.count { it.status == OcrStatus.FAILED || it.status == OcrStatus.DUPLICATE_SUSPECTED }

    LaunchedEffect(tasks) {
        tasks.filter { it.status == OcrStatus.WAITING }.forEach { task ->
            viewModel.processPendingTask(task)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待确认账单") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        devMode = devMode,
                        unitPrice = unitPrice,
                        viewModel = viewModel,
                        onNavigateToPreview = { onNavigateToPreview(task.id) },
                        onNavigateToReview = { onNavigateToReview(task.id) },
                        onNavigateToDebug = { onNavigateToDebug(task.id) },
                        onRetry = { viewModel.retryTask(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItem(
    task: OcrTask,
    devMode: Boolean,
    unitPrice: Float,
    viewModel: BatchTasksViewModel,
    onNavigateToPreview: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val ledgerPage = remember(task.jsonlText) { viewModel.getLedgerPageForTask(task) }
    
    val totalSets = ledgerPage?.rows?.sumOf { it.sets ?: 0 } ?: 0
    val estimatedAmount = String.format("%.2f", totalSets * unitPrice)
    val rawName = ledgerPage?.customerNameRaw
    val titleName = if (!rawName.isNullOrBlank()) rawName else "未命名账单"
    val errorCount = ledgerPage?.rows?.count { 
        it.status == com.example.ocr.processing.RowStatus.NEED_MANUAL_REVIEW || 
        it.status == com.example.ocr.processing.RowStatus.MISSING_DELIVERY ||
        it.status == com.example.ocr.processing.RowStatus.ROW_MISALIGNED ||
        it.status == com.example.ocr.processing.RowStatus.FAILED ||
        it.status == com.example.ocr.processing.RowStatus.MISSING_DATE ||
        it.status == com.example.ocr.processing.RowStatus.DUPLICATE_SUSPECTED ||
        it.status == com.example.ocr.processing.RowStatus.INVALID_FORMAT
    } ?: 0

    var showImageDialog by remember { mutableStateOf(false) }

    if (showImageDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImageDialog = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showImageDialog = false }) { Text("关闭") }
            },
            text = {
                AsyncImage(
                    model = File(task.localUri),
                    contentDescription = "Full Image",
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    contentScale = ContentScale.Fit
                )
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = File(task.localUri),
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showImageDialog = true },
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("总套数: $totalSets 套", style = MaterialTheme.typography.bodyMedium)
                    Text("总金额: $estimatedAmount 元", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    if (errorCount > 0) {
                        Text("发现问题: $errorCount 条", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusChip(task.status)
                }
                
                Column {
                    if (task.status == OcrStatus.FAILED || task.status == OcrStatus.DUPLICATE_SUSPECTED) {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.status == OcrStatus.NEED_REVIEW) {
                    Button(onClick = onNavigateToReview, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("处理问题", fontSize = 16.sp)
                    }
                } else if (task.status == OcrStatus.COMPLETED || task.status == OcrStatus.HUMAN_CONFIRMED || task.status == OcrStatus.LOCKED_FOR_BILLING) {
                    Button(onClick = onNavigateToPreview, modifier = Modifier.weight(1f)) {
                        Text("查看结果", fontSize = 16.sp)
                    }
                } else if (task.status == OcrStatus.WAITING || task.status == OcrStatus.PROCESSING || task.status == OcrStatus.OCR_PROCESSING) {
                     Button(onClick = {}, modifier = Modifier.weight(1f), enabled = false) {
                        Text("识别中...")
                    }
                } else {
                     Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                        Text("重新识别")
                    }
                }
                
                if (devMode) {
                    OutlinedButton(onClick = onNavigateToDebug) {
                        Text("Debug")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: OcrStatus) {
    val (bgColor, textColor) = when (status) {
        OcrStatus.WAITING -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        OcrStatus.UPLOADING, OcrStatus.JOB_SUBMITTED, OcrStatus.POLLING, 
        OcrStatus.JSONL_DOWNLOADING, OcrStatus.JSONL_DOWNLOADED, 
        OcrStatus.TABLE_EXTRACTING, OcrStatus.LEDGER_PARSING,
        OcrStatus.PROCESSING, OcrStatus.OCR_PROCESSING -> 
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
            
        OcrStatus.OCR_DONE, OcrStatus.PARSED_PREVIEW_READY, OcrStatus.COMPLETED -> 
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            
        OcrStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        OcrStatus.DUPLICATE_SUSPECTED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        OcrStatus.NEED_REVIEW -> MaterialTheme.colorScheme.surfaceTint to MaterialTheme.colorScheme.inverseOnSurface
        OcrStatus.HUMAN_CONFIRMED, OcrStatus.LOCKED_FOR_BILLING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
