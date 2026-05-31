package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.OcrStatus
import com.example.data.OcrTestRecord
import com.example.ocr.OcrEvaluator
import com.example.viewmodel.OcrDebugViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrDebugScreen(
    taskId: Int,
    viewModel: OcrDebugViewModel,
    onNavigateBack: () -> Unit
) {
    val task by viewModel.taskState.collectAsStateWithLifecycle()
    val options by viewModel.currentOptions.collectAsStateWithLifecycle(initialValue = null)
    val testRecords by viewModel.testRecords.collectAsStateWithLifecycle()
    val comparisonRunning by viewModel.comparisonRunning.collectAsStateWithLifecycle()
    
    val context = LocalContext.current

    LaunchedEffect(taskId) {
        viewModel.loadTask(taskId)
    }

    // Recommendation heuristics
    val recommendedRecord = remember(testRecords) {
        if (testRecords.isEmpty()) null
        else {
            val evaluations = testRecords.map { it to OcrEvaluator.evaluate(it.markdownText, it.jsonlText) }
            // Sort by: lowest cross table risk, lowest suspected missing slash, highest date count, lowest duration
            evaluations.minWithOrNull(
                compareBy<Pair<OcrTestRecord, com.example.ocr.OcrEvaluationResult>> { if (it.second.hasCrossTableRisk) 1 else 0 }
                    .thenBy { it.second.suspectedMissingSlashCount }
                    .thenByDescending { it.second.dateCount }
                    .thenBy { it.first.durationMs }
            )?.first
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR 调试详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (task == null || options == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val validTask = task!!
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image preview
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = File(validTask.localUri),
                        contentDescription = "Original Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // --- Comparison Testing Section ---
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("矩阵对比测试", style = MaterialTheme.typography.titleMedium)
                        Text("使用不同配置重新识别图片，并自动评估质量寻找最优配置。", style = MaterialTheme.typography.bodySmall)
                        
                        Button(
                            onClick = { viewModel.runComparisonTest() },
                            enabled = !comparisonRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (comparisonRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Text("正在执行比较测试...")
                            } else {
                                Text("一键执行矩阵对比测试")
                            }
                        }
                    }
                }
                
                if (testRecords.isNotEmpty()) {
                    Text("测试记录", style = MaterialTheme.typography.titleMedium)
                    
                    testRecords.forEach { record ->
                        val isRecommended = recommendedRecord?.id == record.id
                        val eval = OcrEvaluator.evaluate(record.markdownText, record.jsonlText)
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRecommended) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isRecommended) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(record.configName, style = MaterialTheme.typography.titleSmall)
                                    if (isRecommended) {
                                        Badge { Text("推荐配置") }
                                    }
                                }
                                Text("耗时: ${record.durationMs}ms | 状态: ${if(record.isSuccess) "成功" else "失败"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                
                                if (record.isSuccess) {
                                    Divider(Modifier.padding(vertical = 8.dp))
                                    Text("检出日期数量: ${eval.dateCount}", style = MaterialTheme.typography.bodySmall)
                                    Text("检出数量/单价格式: ${eval.deliveryFormatCount}", style = MaterialTheme.typography.bodySmall)
                                    Text("跨表粘贴风险: ${if (eval.hasCrossTableRisk) "高" else "低"}", style = MaterialTheme.typography.bodySmall, color = if (eval.hasCrossTableRisk) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                    Text("疑似漏斜杠(/): ${eval.suspectedMissingSlashCount}", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("错误: ${record.errorMsg}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Current task details
                Divider()
                Text("当前主线任务状态", style = MaterialTheme.typography.titleMedium)
                
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("状态: ${validTask.status.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        if (validTask.jobId != null) {
                            Text("JobId: ${validTask.jobId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    StatusChip(status = validTask.status)
                }
                
                if (validTask.status == OcrStatus.FAILED && validTask.rawJobJson != null) {
                    Text("Error JSON", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    SelectionContainer {
                         Text(
                             text = validTask.rawJobJson ?: "无",
                             style = MaterialTheme.typography.bodySmall,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                                 .padding(16.dp),
                             color = MaterialTheme.colorScheme.onErrorContainer
                         )
                    }
                }

                if (validTask.status == OcrStatus.COMPLETED) {
                    
                    Text("OCR Markdown 输出", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    SelectionContainer {
                        Text(
                            text = validTask.rawOcrText ?: "无",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        )
                    }

                    Text("layoutParsingResults 结构", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    SelectionContainer {
                        Text(
                            text = validTask.layoutParsingResultsJson ?: "[]",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        )
                    }
                    
                    Text("完整 JSONL", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    SelectionContainer {
                        Text(
                            text = validTask.jsonlText ?: "无",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        )
                    }
                }

                // Actions
                var annotationText by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                validTask.rawOcrText?.let {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Markdown", it))
                                    Toast.makeText(context, "已复制 Markdown", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = validTask.rawOcrText != null
                        ) {
                            Text("复制 MD")
                        }
                        
                        Button(
                            onClick = {
                                validTask.jsonlText?.let {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("JSONL", it))
                                    Toast.makeText(context, "已复制 JSONL", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = validTask.jsonlText != null
                        ) {
                            Text("复制 JSONL")
                        }
                    }
                    
                    OutlinedTextField(
                        value = annotationText,
                        onValueChange = { annotationText = it },
                        label = { Text("保存样本备注(可选)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { 
                            viewModel.saveSample(annotationText)
                            Toast.makeText(context, "已保存到样本目录", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("保存为评测样本")
                    }

                    Button(
                        onClick = { viewModel.reRecognize() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("当前配置重新识别")
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
         content()
    }
}
