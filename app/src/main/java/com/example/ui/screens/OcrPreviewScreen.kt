package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.first
import com.example.ocr.processing.RowStatus
import com.example.ocr.processing.LedgerRow
import com.example.data.OcrStatus
import com.example.viewmodel.OcrPreviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPreviewScreen(
    taskId: Int,
    viewModel: OcrPreviewViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("算账结果") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else if (uiState.task != null && uiState.ledgerPage != null) {
                PreviewContent(uiState, context, onNavigateToReview, viewModel)
            } else {
                Text("暂无数据", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun PreviewContent(uiState: com.example.viewmodel.OcrPreviewState, context: Context, onNavigateToReview: () -> Unit, viewModel: OcrPreviewViewModel) {
    val task = uiState.task!!
    val page = uiState.ledgerPage!!
    
    var unitPrice by remember { mutableStateOf(0.9f) }
    var devMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        unitPrice = viewModel.getUnitPrice()
        devMode = com.example.data.SettingsRepository(context).devModeFlow.first()
    }

    val exceptionRows = page.rows.filter { it.status == RowStatus.NEED_MANUAL_REVIEW || it.status == RowStatus.INVALID_FORMAT || it.status == RowStatus.DUPLICATE_SUSPECTED || it.status == RowStatus.ROW_MISALIGNED || it.status == RowStatus.MISSING_DELIVERY || it.status == RowStatus.MISSING_DATE || it.status == RowStatus.FAILED }
    val validRows = page.rows.filter { !exceptionRows.contains(it) && it.status != RowStatus.NOISE }
    
    val totalValidSets = validRows.sumOf { it.sets ?: 0 }
    val estimatedAmount = String.format("%.2f", totalValidSets * unitPrice)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("总金额", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("¥ $estimatedAmount", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("总套数", fontSize = 14.sp)
                            Text("$totalValidSets 套", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("单价", fontSize = 14.sp)
                            Text("$unitPrice 元", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("有效记录: ${validRows.size} 条", fontSize = 14.sp)
                        Text("发现问题: ${exceptionRows.size} 条", fontSize = 14.sp, color = if (exceptionRows.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (exceptionRows.isNotEmpty()) {
                    Button(
                        onClick = onNavigateToReview,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("处理问题 (${exceptionRows.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Button(
                    onClick = { 
                        Toast.makeText(context, "账单已自动保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("确认保存", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                
                OutlinedButton(
                    onClick = { /* Could re-trigger OCR here if we passed an onRetry callback */ },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("重新识别")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
        
        item {
            Text("账单明细", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 16.dp))
        }

        items(page.rows) { row ->
            val cardColor = when (row.status) {
                RowStatus.COMPLETE, RowStatus.NORMALIZED_COMPLETE -> Color(0xFFE8F5E9)
                RowStatus.DELIVERY_AUTO_CORRECTED, RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR, RowStatus.RARE_BOX_CAPACITY, RowStatus.MANUAL_CONFIRMED -> Color(0xFFFFF9C4)
                RowStatus.NOISE -> Color(0xFFF5F5F5)
                else -> Color(0xFFFFEBEE)
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("日期: ${row.dateText ?: "未知"}", fontWeight = FontWeight.Bold)
                    Text("送出原文: ${row.deliveryRawText ?: "-"}")
                    if (row.normalizedDeliveryText != null) {
                        Text("标准化送出: ${row.normalizedDeliveryText}")
                    }
                    if (row.sets != null || row.boxes != null) {
                        val capacityStr = if (row.boxCapacity != null) "${row.boxCapacity}套箱" else ""
                        Text("采用值: ${row.sets ?: "-"}套, ${row.boxes ?: "-"}箱 $capacityStr")
                    }
                    val statusName = when (row.status) {
                        RowStatus.COMPLETE -> "正常"
                        RowStatus.NORMALIZED_COMPLETE -> "正常(已归一化)"
                        RowStatus.DELIVERY_AUTO_CORRECTED -> "自动纠错"
                        RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR -> "自动恢复分隔符"
                        RowStatus.RARE_BOX_CAPACITY -> "稀有箱型"
                        RowStatus.NEED_MANUAL_REVIEW -> "需复核"
                        RowStatus.INVALID_FORMAT -> "格式无效"
                        RowStatus.NOISE -> "已忽略(噪声)"
                        RowStatus.DUPLICATE_SUSPECTED -> "疑似重复"
                        RowStatus.ROW_MISALIGNED -> "行错位"
                        RowStatus.MISSING_DELIVERY -> "缺少送出"
                        RowStatus.MISSING_DATE -> "缺少日期"
                        RowStatus.VALID_BUT_POSSIBLY_WRONG_BY_OCR -> "疑似错误"
                        RowStatus.POSSIBLE_OCR_VALUE_CONFLICT -> "识别冲突"
                        RowStatus.MANUAL_CONFIRMED -> "人工已确认"
                        RowStatus.FAILED -> "失败"
                    }
                    val isParticipating = if (row.status == RowStatus.COMPLETE || row.status == RowStatus.NORMALIZED_COMPLETE || row.status == RowStatus.DELIVERY_AUTO_CORRECTED || row.status == RowStatus.DELIVERY_AUTO_RESTORED_SEPARATOR || row.status == RowStatus.RARE_BOX_CAPACITY || row.status == RowStatus.MANUAL_CONFIRMED) "参与计算" else "不参与计算"
                    Text("状态: $statusName，$isParticipating", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isParticipating == "不参与计算" && row.status != RowStatus.NOISE) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        }
        
        if (devMode) {
            item {
                Spacer(Modifier.height(32.dp))
                Text("开发调试区", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("HTTP JSONL length: ${task.jsonlBodyLength ?: 0}")
                    }
                }
            }
        }
    }
}
