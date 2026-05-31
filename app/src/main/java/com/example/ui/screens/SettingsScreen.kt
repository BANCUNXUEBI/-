package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    
    var unitPriceStr by remember { mutableStateOf("") }
    var devMode by remember { mutableStateOf(false) }
    var showGuidance by remember { mutableStateOf(true) }
    var ocrToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        unitPriceStr = prefs.unitPriceFlow.first().toString()
        devMode = prefs.devModeFlow.first()
        showGuidance = prefs.showGuidanceFlow.first()
        ocrToken = prefs.paddleOcrToken.first() ?: ""
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("系统设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
             Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                 CircularProgressIndicator()
             }
        } else {
             Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                 OutlinedTextField(
                     value = unitPriceStr,
                     onValueChange = { unitPriceStr = it },
                     label = { Text("套件单价 (元)") },
                     modifier = Modifier.fillMaxWidth()
                 )
                 
                 OutlinedTextField(
                     value = ocrToken,
                     onValueChange = { ocrToken = it },
                     label = { Text("自定义 OCR API Token (可选)") },
                     modifier = Modifier.fillMaxWidth()
                 )
                 
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                     Text("拍照前显示书写提示", fontSize = 16.sp)
                     Switch(checked = showGuidance, onCheckedChange = { showGuidance = it })
                 }
                 
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                     Text("开发者调试模式", fontSize = 16.sp)
                     Switch(checked = devMode, onCheckedChange = { devMode = it })
                 }
                 
                 Spacer(Modifier.height(16.dp))
                 
                 OutlinedButton(
                     onClick = {
                         scope.launch {
                             // Let's clear tasks. 
                             com.example.data.AppDatabase.getDatabase(context).ocrTaskDao().deleteAllTasks()
                             android.widget.Toast.makeText(context, "历史数据已清空", android.widget.Toast.LENGTH_SHORT).show()
                         }
                     },
                     modifier = Modifier.fillMaxWidth(),
                     colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                 ) {
                     Text("清空历史记录")
                 }
                 
                 Text("智能账本小助手 v1.0\n专为中老年人设计的看账神器", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
                 
                 Spacer(Modifier.weight(1f))
                 
                 Button(
                     onClick = {
                         scope.launch {
                             prefs.setUnitPrice(unitPriceStr.toFloatOrNull() ?: 0.9f)
                             prefs.setDevMode(devMode)
                             prefs.setShowGuidance(showGuidance)
                             prefs.setPaddleOcrToken(ocrToken.ifBlank { null })
                             onNavigateBack()
                         }
                     },
                     modifier = Modifier.fillMaxWidth().height(56.dp)
                 ) {
                     Text("保存设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                 }
             }
        }
    }
}
