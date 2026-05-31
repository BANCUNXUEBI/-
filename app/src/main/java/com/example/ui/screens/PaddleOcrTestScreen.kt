package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.viewmodel.PaddleOcrTestViewModel
import java.io.File
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PaddleOcrTestScreen(
    viewModel: PaddleOcrTestViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.setImageUri(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkToken()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PaddleOCR 真实测试") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. PaddleOCR token setup check
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PaddingValues(16.dp)
                Text(
                    "Token 状态: ${uiState.tokenStatus}",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            // Buttons: 选择图片, 开始真实 OCR, 重新轮询
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { galleryLauncher.launch("image/*") }) {
                    Text("1. 选择图片")
                }
                Button(
                    onClick = { viewModel.startTest() },
                    enabled = uiState.imageFile != null && uiState.pollingStatus != "running" && uiState.pollingStatus != "pending"
                ) {
                    Text("2. 开始真实 OCR")
                }
                Button(
                    onClick = { viewModel.pollJobManually() },
                    enabled = uiState.jobId != null
                ) {
                    Text("重新轮询")
                }
            }

            // 1. Current image thumbnail
            if (uiState.imageFile != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                ) {
                    AsyncImage(
                        model = uiState.imageFile,
                        contentDescription = "测试图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Status Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3. 请求状态: ${uiState.requestStatus}", fontWeight = FontWeight.Bold)
                    Text("4. HTTP 状态码: ${uiState.httpStatusCode ?: "N/A"}")
                    Text("5. jobId: ${uiState.jobId ?: "N/A"}")
                    Text("6. 轮询状态: ${uiState.pollingStatus}")
                    Text("7. 进度: ${uiState.extractedPages ?: 0} / ${uiState.totalPages ?: 0}")
                    Text("8. jsonUrl: ${uiState.jsonUrl ?: "N/A"}")
                    if (uiState.errorMsg != null) {
                        Text("11. 错误信息: ${uiState.errorMsg}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            // Raw Submit Response Panel
            if (uiState.rawSubmitResponse != null || uiState.rawSubmitErrorBody != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("提交接口原始响应 Raw Submit Response", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        
                        if (uiState.modelParamsLog != null) {
                            Text("Params", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.modelParamsLog!!, fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        Text("Response Body: ", fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(uiState.rawSubmitResponse ?: "N/A", fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                        
                        if (uiState.rawSubmitErrorBody != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Error Body: ", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.rawSubmitErrorBody!!, fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                        }
                    }
                }
            }

            // Raw Poll Response Panel
            if (uiState.rawPollResponse != null || uiState.rawPollErrorBody != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("轮询接口原始响应 Raw Poll Response", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        
                        Text("GET URL: ${uiState.pollUrl ?: "N/A"}", fontWeight = FontWeight.Bold)
                        Text("HTTP Status: ${uiState.pollHttpStatusCode ?: "N/A"}", fontWeight = FontWeight.Bold)
                        Text("Current State: ${uiState.pollingStatus}", fontWeight = FontWeight.Bold)
                        Text("Current jsonUrl: ${uiState.jsonUrl ?: "N/A"}", fontWeight = FontWeight.Bold)
                        
                        Spacer(Modifier.height(8.dp))
                        Text("Response Body: ", fontWeight = FontWeight.Bold)
                        SelectionContainer {
                            Text(uiState.rawPollResponse ?: "N/A", fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                        
                        if (uiState.rawPollErrorBody != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Error Body: ", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.rawPollErrorBody!!, fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                            }
                        }
                    }
                }
            }

            // Buttons: 复制 Markdown, 复制 JSONL, 保存样本, 导出调试结果
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Markdown", uiState.markdownText ?: "")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Markdown 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.markdownText != null
                ) {
                    Text("复制 Markdown")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("JSONL", uiState.jsonlText ?: "")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "JSONL 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.jsonlText != null
                ) {
                    Text("复制 JSONL")
                }
                Button(onClick = { Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show() }) {
                    Text("保存样本")
                }
                Button(onClick = { Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show() }) {
                    Text("导出调试结果")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("RawResponse", uiState.rawSubmitResponse ?: "N/A")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Raw Response 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.rawSubmitResponse != null
                ) {
                    Text("复制提交响应")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("ErrorBody", uiState.rawSubmitErrorBody ?: "N/A")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Error Body 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.rawSubmitErrorBody != null
                ) {
                    Text("复制错误信息")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("RequestParams", uiState.modelParamsLog ?: "N/A")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Request Params 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.modelParamsLog != null
                ) {
                    Text("复制请求摘要")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("PollResponse", uiState.rawPollResponse ?: "N/A")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "轮询响应 已复制", Toast.LENGTH_SHORT).show()
                    },
                    enabled = uiState.rawPollResponse != null
                ) {
                    Text("复制轮询响应")
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipStr = """
                            --- Params ---
                            ${uiState.modelParamsLog}
                            --- Submit Response ---
                            ${uiState.rawSubmitResponse}
                            --- Submit Error ---
                            ${uiState.rawSubmitErrorBody}
                            --- Poll Response ---
                            ${uiState.rawPollResponse}
                            --- Poll Error ---
                            ${uiState.rawPollErrorBody}
                        """.trimIndent()
                        val clip = ClipData.newPlainText("AllDebugInfo", clipStr)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "全部调试信息已复制", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("复制全部调试信息")
                }
            }

            // Results Panel
            
            // JSONL Download Status Panel
            if (uiState.jsonlDownloadStatus != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("JSONL 下载与解析调试区", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("jsonUrl (length=${uiState.jsonUrl?.length ?: 0}): ${uiState.jsonUrl ?: "null"}", fontSize = 10.sp)
                        Text("Download Status: ${uiState.jsonlDownloadStatus} (HTTP ${uiState.jsonlHttpStatusCode ?: "N/A"})")
                        Text("Body Length: ${uiState.jsonlBodyLength ?: "N/A"} chars")
                        Text("Parsed Table Count: ${uiState.jsonlParsedTableCount ?: 0}")
                        Text("LegderRows Parsed: ${uiState.jsonlLedgerRowsCount ?: 0}")
                        
                        if (uiState.jsonlFirstTableHtml != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("First Table Html Prefix:", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.jsonlFirstTableHtml!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                        
                        if (uiState.jsonlLedgerRowsPreview != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Rows Preview:", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.jsonlLedgerRowsPreview!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }

                        if (uiState.jsonlErrorBody != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Error Body:", fontWeight = FontWeight.Bold)
                            SelectionContainer {
                                Text(uiState.jsonlErrorBody!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                        
                        if (uiState.jsonlParseErrorTrace != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Parse Error Exception:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            SelectionContainer {
                                Text(uiState.jsonlParseErrorTrace!!, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            if (uiState.markdownText != null) {
                Text("9. Markdown 文本:", fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.markdownText!!,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (uiState.jsonlText != null) {
                Text("10. JSONL 原文:", fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.jsonlText!!,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (uiState.layoutParsingResultsJson != null) {
                Text("layoutParsingResults:", fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.layoutParsingResultsJson!!,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
