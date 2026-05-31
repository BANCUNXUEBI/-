package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*
import com.example.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraGuidanceScreen(
    onNavigateBack: () -> Unit,
    onProceed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { SettingsRepository(context) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照提醒") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "拍清楚，写工整\n识别更准确",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 40.sp
            )
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GuidanceItem("1", "日期写清楚，例如 5.12。")
                    GuidanceItem("2", "送出写清楚，例如 60/2、90/3、120/4。")
                    GuidanceItem("3", "不要把数字写到格子外面。")
                    GuidanceItem("4", "小计和合计写在底部，不要写到送出栏里。")
                    GuidanceItem("5", "拍照时整张账本放平，光线充足，不要遮住右侧。")
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("我知道了，开始拍照", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            
            TextButton(
                onClick = {
                    scope.launch { 
                        prefs.setShowGuidance(false) 
                        onProceed()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("不再提示", fontSize = 16.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun GuidanceItem(num: String, text: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(num, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 18.sp, lineHeight = 26.sp)
    }
}
