package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.HomeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    guidanceAction: String? = null,
    onNavigateToBatchTasks: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCameraGuidance: (String) -> Unit,
    onNavigateToPaddleTest: () -> Unit
) {
    val context = LocalContext.current
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraFileName by remember { mutableStateOf("") }
    
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            viewModel.importImages(listOf(tempCameraUri!!), "camera_img")
        } else {
            errorMsg = "已取消拍照或拍照失败"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris, "gallery_img")
        }
    }
    
    val checkPermissionAndDispatch = {
        val permissionInfo = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val hasCameraPermissionInManifest = permissionInfo.requestedPermissions?.contains(android.Manifest.permission.CAMERA) == true
        
        if (hasCameraPermissionInManifest) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // ... same logic
                try {
                    val file = File(File(context.cacheDir, "camera_images").apply { mkdirs() }, "camera_${System.currentTimeMillis()}.jpg")
                    tempCameraFileName = file.name
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    tempCameraUri = uri
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    errorMsg = "启动相机失败: ${e.message}"
                }
            } else {
                errorMsg = "缺少相机权限"
            }
        } else {
            // ... same logic
            try {
                val file = File(File(context.cacheDir, "camera_images").apply { mkdirs() }, "camera_${System.currentTimeMillis()}.jpg")
                tempCameraFileName = file.name
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                errorMsg = "启动相机失败: ${e.message}"
            }
        }
    }
    
    LaunchedEffect(guidanceAction) {
        if (guidanceAction == "camera") {
            checkPermissionAndDispatch()
        } else if (guidanceAction == "gallery") {
            galleryLauncher.launch("image/*")
        }
    }

    fun dispatchTakePictureIntent() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "JPEG_${timeStamp}_.jpg"
            val cacheDir = File(context.cacheDir, "camera_images").apply { mkdirs() }
            val file = File(cacheDir, fileName)
            tempCameraFileName = fileName
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = "启动相机失败: ${e.message}"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            dispatchTakePictureIntent()
        } else {
            errorMsg = "需要相机权限才能拍照"
        }
    }

    fun checkPermissionAndDispatch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            dispatchTakePictureIntent()
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(importState.isImporting) {
        if (!importState.isImporting && importState.successCount > 0) {
            viewModel.dismissImportState()
            onNavigateToBatchTasks()
        }
    }

    if (importState.isImporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("正在处理...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("请稍候, 正在导入...")
                }
            },
            confirmButton = {}
        )
    } else if (importState.totalCount > 0 && importState.successCount == 0) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportState() },
            title = { Text("导入失败") },
            text = { Text("无法导入所选照片") },
            confirmButton = {
                Button(onClick = { viewModel.dismissImportState() }) {
                    Text("关闭")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "餐具账本 OCR",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "拍照识别，自动算账",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { onNavigateToSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "系统设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (errorMsg != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = errorMsg!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Setup Preferences to check guidance settings
                val prefs = remember { com.example.data.SettingsRepository(context) }
                val showGuidance by prefs.showGuidanceFlow.collectAsState(initial = true)
                val devMode by prefs.devModeFlow.collectAsState(initial = false)

                // Quick Action Cards Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HomePrimaryActionCard(
                        title = "拍照算账",
                        icon = Icons.Default.CameraAlt,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        iconBgColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            if (showGuidance) onNavigateToCameraGuidance("camera") 
                            else checkPermissionAndDispatch() 
                        }
                    )
                    HomePrimaryActionCard(
                        title = "上传照片",
                        icon = Icons.Default.PhotoLibrary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        iconBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            if (showGuidance) onNavigateToCameraGuidance("gallery") 
                            else galleryLauncher.launch("image/*") 
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                
                SecondaryActionButton(
                    title = "待确认账单",
                    icon = Icons.AutoMirrored.Filled.List,
                    iconBgColor = Color(0xFFFFEDD5), // orange-100
                    iconColor = Color(0xFFC2410C), // orange-700
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToBatchTasks
                )

                SecondaryActionButton(
                    title = "历史结果",
                    icon = Icons.Default.DateRange,
                    iconBgColor = Color(0xFFDBEAFE), // blue-100
                    iconColor = Color(0xFF1D4ED8), // blue-700
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToHistory
                )
                
                if (devMode) {
                    Spacer(Modifier.height(16.dp))
                    Text("Developer Zone", fontWeight = FontWeight.Bold, color = Color.Red)
                    SecondaryActionButton(
                        title = "PaddleOCR 真实测试",
                        icon = Icons.Default.Person,
                        iconBgColor = Color(0xFFE0E7FF),
                        iconColor = Color(0xFF4338CA),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToPaddleTest
                    )
                    
                    // Debug Information Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Camera Debug Info", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            Text("Camera Permission: $isGranted", fontSize = 12.sp)
                            Text("Image URI: ${tempCameraUri?.toString() ?: "null"}", fontSize = 12.sp)
                            Text("File Name: ${tempCameraFileName.ifEmpty { "empty" }}", fontSize = 12.sp)
                            Text("Authority: ${context.packageName}.fileprovider", fontSize = 12.sp)
                            Text("Error: ${errorMsg ?: "none"}", fontSize = 12.sp)
                            
                            val file = if (tempCameraFileName.isNotEmpty()) {
                                File(File(context.cacheDir, "camera_images"), tempCameraFileName)
                            } else null
                            Text("File Exists: ${file?.exists() ?: false}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomePrimaryActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    iconBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(iconBgColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = contentColor
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun SecondaryActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
