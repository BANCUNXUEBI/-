package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.example.data.OcrStatus
import com.example.ocr.processing.LedgerRow
import com.example.ocr.processing.RowStatus
import com.example.viewmodel.OcrPreviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    taskId: Int,
    viewModel: OcrPreviewViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToOverview: () -> Unit
) {
    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var forceShowAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人工复核") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { forceShowAll = !forceShowAll }) {
                        Text(if (forceShowAll) "处理问题" else "查看结果", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (uiState.task != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("稍后处理")
                        }
                        Button(onClick = {
                            viewModel.confirmAndLockPage(uiState.task!!.id)
                            onNavigateToOverview()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("确认无误，通过本页")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.task == null || uiState.ledgerPage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("无法加载视图数据")
            }
        } else {
            ReviewContent(
                uiState = uiState,
                viewModel = viewModel,
                forceShowAll = forceShowAll,
                context = context,
                onUpdateRow = { rowId, adoptedValue, overrideStatus -> 
                    viewModel.updateRowManual(rowId, adoptedValue, overrideStatus)
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun ReviewContent(
    uiState: com.example.viewmodel.OcrPreviewState,
    viewModel: OcrPreviewViewModel,
    forceShowAll: Boolean,
    context: Context,
    onUpdateRow: (String, String, RowStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val task = uiState.task!!
    val page = uiState.ledgerPage!!

    var unitPrice by remember { mutableStateOf(0.9f) }
    LaunchedEffect(Unit) {
        unitPrice = viewModel.getUnitPrice()
    }

    val exceptionRows = page.rows.filter { 
        it.status == RowStatus.NEED_MANUAL_REVIEW ||
        it.status == RowStatus.INVALID_FORMAT ||
        it.status == RowStatus.ROW_MISALIGNED ||
        it.status == RowStatus.MISSING_DELIVERY ||
        it.status == RowStatus.MISSING_DATE ||
        it.status == RowStatus.FAILED ||
        it.status == RowStatus.DUPLICATE_SUSPECTED
    }
    
    val rowsToDisplay = if (forceShowAll) page.rows else exceptionRows
    
    val validSetsSum = page.rows.sumOf { 
         if (it.status == RowStatus.COMPLETE || it.status == RowStatus.NORMALIZED_COMPLETE || it.status == RowStatus.DELIVERY_AUTO_CORRECTED || it.status == RowStatus.RARE_BOX_CAPACITY || it.status == RowStatus.MANUAL_CONFIRMED) {
             it.sets ?: 0
         } else 0
     }
    val estimatedAmount = String.format("%.2f", validSetsSum * unitPrice)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AsyncImage(
                model = java.io.File(task.localUri),
                contentDescription = "账单原图",
                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
        }
        
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (exceptionRows.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                             Text("有效记录: ${page.rows.size - exceptionRows.size} 条", fontSize = 14.sp)
                             Text("问题行数: ${exceptionRows.size} 条", fontSize = 14.sp, color = if (exceptionRows.isEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("当前合计: $validSetsSum 套", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("当前金额: ¥ $estimatedAmount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    val warnings = page.warnings
                    if (warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("系统风险提示:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        warnings.forEach { w ->
                            Text("- $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
        
        if (rowsToDisplay.isEmpty() && !forceShowAll) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("没有发现严重错误，请点击下方确认本页", color = Color.Gray)
                }
            }
        }

        items(rowsToDisplay, key = { it.id }) { row ->
            ReviewRowCard(row, onUpdateRow)
        }
    }
}

@Composable
fun ReviewRowCard(row: LedgerRow, onUpdateRow: (String, String, RowStatus) -> Unit) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    
    val regex = Regex("""^\d+/\d+$""")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("日期: ${row.dateText ?: "未知"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val statusMessage = when (row.status) {
                    RowStatus.NEED_MANUAL_REVIEW -> "需人工确认"
                    RowStatus.INVALID_FORMAT -> "格式不正确"
                    RowStatus.ROW_MISALIGNED -> "行错位"
                    RowStatus.MISSING_DELIVERY -> "缺少送出值"
                    RowStatus.MISSING_DATE -> "缺少日期"
                    RowStatus.DUPLICATE_SUSPECTED -> "疑似重复"
                    else -> "需要复核"
                }
                Text(statusMessage, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("识别结果: ${row.deliveryRawText ?: "无"}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            
            if (row.warnings.isNotEmpty()) {
                 Spacer(Modifier.height(4.dp))
                 row.warnings.forEach { w ->
                      Text("- $w", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                 }
            }

            Spacer(Modifier.height(16.dp))
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { 
                        onUpdateRow(row.id, row.normalizedDeliveryText ?: row.deliveryRawText ?: "", RowStatus.MANUAL_CONFIRMED) 
                    }, modifier = Modifier.weight(1f)) {
                        Text("这条没问题", fontSize = 14.sp)
                    }
                    Button(onClick = { 
                        showEditDialog = true
                        editValue = row.normalizedDeliveryText ?: row.deliveryRawText ?: ""
                        editError = null
                    }, modifier = Modifier.weight(1f)) {
                        Text("我来修改", fontSize = 14.sp)
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { 
                        onUpdateRow(row.id, "", RowStatus.NOISE) 
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("这条不计入", fontSize = 14.sp)
                    }
                }
            }
        }
    }
    
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("手动修改送出值") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { 
                            editValue = it
                            editError = if (regex.matches(it)) null else "格式错误，必须为类似 60/3"
                        },
                        label = { Text("格式: 数量/箱数 (如 60/3)") },
                        isError = editError != null
                    )
                    if (editError != null) {
                        Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (regex.matches(editValue)) {
                            onUpdateRow(row.id, editValue, RowStatus.MANUAL_CONFIRMED)
                            showEditDialog = false
                        } else {
                            editError = "格式错误，必须为类似 60/3"
                        }
                    }
                ) {
                    Text("确认修改")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
