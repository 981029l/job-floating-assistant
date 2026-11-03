package com.assistant.job

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F7FA)
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var overlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(JobAccessibilityService.isServiceEnabledInSystem(context)) }
    var floatingServiceRunning by remember { mutableStateOf(FloatingService.isRunning) }

    var apiKey by remember { mutableStateOf(StorageManager.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(StorageManager.getBaseUrl(context)) }
    var modelName by remember { mutableStateOf(StorageManager.getModel(context)) }
    var resumeContent by remember { mutableStateOf(StorageManager.getResume(context)) }

    // 模型列表弹窗状态
    var showModelDialog by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // 简历文件选择器 (PDF / Word / TXT)
    var isParsingResume by remember { mutableStateOf(false) }
    val resumePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isParsingResume = true
            scope.launch {
                val result = ResumeParser.parseResumeUri(context, uri)
                isParsingResume = false
                result.onSuccess { parsedText ->
                    resumeContent = parsedText
                    StorageManager.saveResume(context, parsedText)
                    val fileName = ResumeParser.getFileName(context, uri)
                    Toast.makeText(context, "成功导入简历: $fileName (共 ${parsedText.length} 字)", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "解析失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Android 13+ 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 监听生命周期，从设置页面返回后自动刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = checkOverlayPermission(context)
                accessibilityGranted = JobAccessibilityService.isServiceEnabledInSystem(context)
                floatingServiceRunning = FloatingService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App 标题卡片 (采用 Material 3 原生矢量图标)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1677FF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = "AI Assistant Icon",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AI 求职悬浮助手",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "屏幕自动读屏提取 JD · 一键获取模型 · 支持上传 PDF 简历",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 1. 系统权限配置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = "Permissions",
                        tint = Color(0xFF1677FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "权限与悬浮开关",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F)
                    )
                }

                // 悬浮窗权限
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. 悬浮窗权限", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (overlayGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                contentDescription = if (overlayGranted) "Granted" else "Denied",
                                tint = if (overlayGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (overlayGranted) "已开启 (可正常弹出悬浮球)" else "未开启 (点击右侧去授权)",
                                fontSize = 12.sp,
                                color = if (overlayGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F)
                            )
                        }
                    }
                    Button(
                        onClick = { requestOverlayPermission(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayGranted) Color(0xFFF6FFED) else Color(0xFF1677FF)
                        )
                    ) {
                        Text(
                            if (overlayGranted) "已授权" else "去开启",
                            color = if (overlayGranted) Color(0xFF52C41A) else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider()

                // 无障碍权限
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("2. 无障碍读屏服务", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (accessibilityGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                contentDescription = if (accessibilityGranted) "Granted" else "Denied",
                                tint = if (accessibilityGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (accessibilityGranted) "已开启 (可在 BOSS 直聘秒级读屏)" else "未开启 (进入无障碍开启'求职助手')",
                                fontSize = 12.sp,
                                color = if (accessibilityGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F)
                            )
                        }
                    }
                    Button(
                        onClick = { requestAccessibilityPermission(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (accessibilityGranted) Color(0xFFF6FFED) else Color(0xFF1677FF)
                        )
                    ) {
                        Text(
                            if (accessibilityGranted) "已开启" else "去开启",
                            color = if (accessibilityGranted) Color(0xFF52C41A) else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider()

                // 启动 / 关闭悬浮球按钮
                Button(
                    onClick = {
                        if (!checkOverlayPermission(context)) {
                            Toast.makeText(context, "请先点击上方按钮授予【悬浮窗权限】", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val intent = Intent(context, FloatingService::class.java)
                        try {
                            if (floatingServiceRunning) {
                                context.stopService(intent)
                                floatingServiceRunning = false
                                Toast.makeText(context, "悬浮球已关闭", Toast.LENGTH_SHORT).show()
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                floatingServiceRunning = true
                                Toast.makeText(context, "悬浮球已开启！请看屏幕边缘蓝色小球", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (floatingServiceRunning) Color(0xFFFF4D4F) else Color(0xFF1677FF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (floatingServiceRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = "Toggle Service",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (floatingServiceRunning) "关闭屏幕悬浮球" else "开启屏幕悬浮球",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. 大模型配置 (支持一键拉取模型列表)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFF1677FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "大模型 API 设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F)
                    )
                }

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        StorageManager.saveApiKey(context, it)
                    },
                    label = { Text("API Key (sk-...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        StorageManager.saveBaseUrl(context, it)
                    },
                    label = { Text("Base URL 接口地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 模型选择行与获取模型按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            StorageManager.saveModel(context, it)
                        },
                        label = { Text("当前选择模型") },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "请先填写 API Key！", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isFetchingModels = true
                            scope.launch {
                                val result = DeepSeekClient.fetchModelList(context)
                                isFetchingModels = false
                                result.onSuccess { models ->
                                    fetchedModels = models
                                    showModelDialog = true
                                }.onFailure { err ->
                                    Toast.makeText(context, "拉取模型列表失败: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1.1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF52C41A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Models",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isFetchingModels) "拉取中..." else "获取模型",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 常用模型快捷标签
                Text("快捷预设模型：", fontSize = 12.sp, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("deepseek-chat", "deepseek-reasoner", "qwen-turbo").forEach { m ->
                        SuggestionChip(
                            onClick = {
                                modelName = m
                                StorageManager.saveModel(context, m)
                                Toast.makeText(context, "已切换为: $m", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(m, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // 3. 简历管理 (支持上传 PDF / DOCX / TXT)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Article,
                            contentDescription = "Resume",
                            tint = Color(0xFF1677FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "我的简历内容",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F)
                        )
                    }
                    TextButton(onClick = {
                        resumeContent = StorageManager.DEFAULT_RESUME
                        StorageManager.saveResume(context, StorageManager.DEFAULT_RESUME)
                        Toast.makeText(context, "已恢复为预置前端简历亮点", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("恢复预设", fontSize = 12.sp)
                    }
                }

                // 上传简历文件按钮
                Button(
                    onClick = {
                        resumePickerLauncher.launch("*/*")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF722ED1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UploadFile,
                        contentDescription = "Upload Resume",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isParsingResume) "正在解析简历中..." else "选择并上传简历文件 (PDF / Word / TXT)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "简历内容（已导入或在此手动修改）：",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = resumeContent,
                    onValueChange = {
                        resumeContent = it
                        StorageManager.saveResume(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 10
                )
            }
        }

        // 使用指南卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = "Tips",
                        tint = Color(0xFFD48806),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "操作技巧：",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFD48806)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. 点击【开启屏幕悬浮球】，屏幕左侧会出现蓝色悬浮球。\n" +
                            "2. 打开 BOSS直聘 进入任意岗位详情页，点击悬浮球即可自动读屏生成。\n" +
                            "3. 若某机型限制读屏，点击悬浮球后可直接【粘贴剪贴板文字】生成，双重保障！",
                    fontSize = 12.sp,
                    color = Color(0xFF595959),
                    lineHeight = 18.sp
                )
            }
        }
    }

    // 模型选择弹窗
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择模型列表 (${fetchedModels.size} 个可用)") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(fetchedModels) { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    modelName = m
                                    StorageManager.saveModel(context, m)
                                    showModelDialog = false
                                    Toast.makeText(context, "已选择模型: $m", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m, fontSize = 14.sp, color = if (m == modelName) Color(0xFF1677FF) else Color.DarkGray)
                            if (m == modelName) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF1677FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(intent)
        }
    }
}

private fun requestAccessibilityPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "无法直接打开设置，请在系统设置中搜索'无障碍'并开启", Toast.LENGTH_LONG).show()
    }
}
