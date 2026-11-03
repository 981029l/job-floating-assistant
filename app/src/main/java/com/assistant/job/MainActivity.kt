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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
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
                    color = Color(0xFFF8FAFC)
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
    var customPrompt by remember { mutableStateOf(StorageManager.getCustomPrompt(context)) }

    var selectedTab by remember { mutableStateOf(0) } // 0: 悬浮主控, 1: 简历档案, 2: AI设置

    // 模型弹窗与解析状态
    var showModelDialog by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    var isParsingResume by remember { mutableStateOf(false) }
    var uploadedFileName by remember { mutableStateOf("") }

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
                    uploadedFileName = ResumeParser.getFileName(context, uri)
                    Toast.makeText(context, "成功导入: $uploadedFileName", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "解析失败: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.RocketLaunch, contentDescription = "主控") },
                    label = { Text("悬浮主控", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1677FF),
                        selectedTextColor = Color(0xFF1677FF),
                        indicatorColor = Color(0xFFE6F4FF)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Badge, contentDescription = "求职档案") },
                    label = { Text("求职档案", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF722ED1),
                        selectedTextColor = Color(0xFF722ED1),
                        indicatorColor = Color(0xFFF9F0FF)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = "AI智囊") },
                    label = { Text("AI设置", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1677FF),
                        selectedTextColor = Color(0xFF1677FF),
                        indicatorColor = Color(0xFFE6F4FF)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8FAFC))
        ) {
            // 顶部 C1 好运猫沉浸 Hero 栏
            TopHeroHeader(floatingServiceRunning = floatingServiceRunning)

            // 各 Tab 视窗
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        context = context,
                        overlayGranted = overlayGranted,
                        accessibilityGranted = accessibilityGranted,
                        floatingServiceRunning = floatingServiceRunning,
                        modelName = modelName,
                        onToggleFloating = {
                            if (!checkOverlayPermission(context)) {
                                Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                                return@DashboardTab
                            }
                            val intent = Intent(context, FloatingService::class.java)
                            try {
                                if (floatingServiceRunning) {
                                    context.stopService(intent)
                                    floatingServiceRunning = false
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    floatingServiceRunning = true
                                    Toast.makeText(context, "好运猫悬浮球已就绪，快去 BOSS直聘 试试吧！", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onRequestOverlay = { requestOverlayPermission(context) },
                        onRequestAccessibility = { requestAccessibilityPermission(context) },
                        onQuickSwitchModel = { m ->
                            modelName = m
                            StorageManager.saveModel(context, m)
                            Toast.makeText(context, "已切换为: $m", Toast.LENGTH_SHORT).show()
                        }
                    )
                    1 -> ProfileTab(
                        context = context,
                        resumeContent = resumeContent,
                        uploadedFileName = uploadedFileName,
                        isParsing = isParsingResume,
                        onUploadClick = { resumePickerLauncher.launch("*/*") },
                        onResumeChange = {
                            resumeContent = it
                            StorageManager.saveResume(context, it)
                        },
                        onResetDefault = {
                            resumeContent = StorageManager.DEFAULT_RESUME
                            StorageManager.saveResume(context, StorageManager.DEFAULT_RESUME)
                            uploadedFileName = ""
                            Toast.makeText(context, "已重置为预设前端亮点简历", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> SettingsTab(
                        context = context,
                        scope = scope,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        modelName = modelName,
                        customPrompt = customPrompt,
                        isFetchingModels = isFetchingModels,
                        onApiKeyChange = {
                            apiKey = it
                            StorageManager.saveApiKey(context, it)
                        },
                        onBaseUrlChange = {
                            baseUrl = it
                            StorageManager.saveBaseUrl(context, it)
                        },
                        onModelNameChange = {
                            modelName = it
                            StorageManager.saveModel(context, it)
                        },
                        onPromptChange = {
                            customPrompt = it
                            StorageManager.saveCustomPrompt(context, it)
                        },
                        onFetchModelsClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "请先填写 API Key！", Toast.LENGTH_SHORT).show()
                                return@SettingsTab
                            }
                            isFetchingModels = true
                            scope.launch {
                                val result = DeepSeekClient.fetchModelList(context)
                                isFetchingModels = false
                                result.onSuccess { models ->
                                    fetchedModels = models
                                    showModelDialog = true
                                }.onFailure { err ->
                                    Toast.makeText(context, "获取失败: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 模型选择对话框
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("选择可用模型 (${fetchedModels.size} 个)") },
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
                                    Toast.makeText(context, "已选择: $m", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m, fontSize = 14.sp, color = if (m == modelName) Color(0xFF1677FF) else Color.DarkGray)
                            if (m == modelName) {
                                Icon(Icons.Rounded.Check, contentDescription = "Active", tint = Color(0xFF1677FF), modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("取消") }
            }
        )
    }
}

// 顶部 Hero 沉浸栏 (带 C1 招手好运猫)
@Composable
fun TopHeroHeader(floatingServiceRunning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // C1 好运猫圆形 IP 头像
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_round),
                    contentDescription = "C1 招手好运猫",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFFE2E8F0), CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "AI 求职悬浮助手",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (floatingServiceRunning) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (floatingServiceRunning) "好运猫待命中 · 悬浮球运行中" else "好运猫待命 · 随时开启",
                            fontSize = 11.sp,
                            color = if (floatingServiceRunning) Color(0xFF059669) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// Tab 0: 悬浮主控 Bento 页面
@Composable
fun DashboardTab(
    context: Context,
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    floatingServiceRunning: Boolean,
    modelName: String,
    onToggleFloating: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onQuickSwitchModel: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bento 1: 全局悬浮球主开关 (大卡片)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleFloating() },
            colors = CardDefaults.cardColors(
                containerColor = if (floatingServiceRunning) Color(0xFF1E293B) else Color(0xFF1677FF)
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (floatingServiceRunning) "⏹ 正在屏幕停靠" else "▶ 点击开启悬浮窗",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (floatingServiceRunning) "点击即可关闭屏幕侧边的招手好运猫浮标" else "开启后桌面将出现可爱的招手猫浮标，在 BOSS直聘 点它一键生成招呼语",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Icon(
                    imageVector = if (floatingServiceRunning) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                    contentDescription = "Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        // Bento 2: 权限状态并列卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, contentDescription = "Security", tint = Color(0xFF1677FF), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("核心权限自检", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("悬浮窗权限", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (overlayGranted) "已授权正常" else "未授权，悬浮球无法弹出",
                            fontSize = 11.sp,
                            color = if (overlayGranted) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                    Button(
                        onClick = onRequestOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayGranted) Color(0xFFF1F5F9) else Color(0xFF1677FF)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (overlayGranted) "已开通" else "去开通", color = if (overlayGranted) Color(0xFF64748B) else Color.White, fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("无障碍自动读屏", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (accessibilityGranted) "已开通正常" else "未开通，可在浮窗内粘贴JD兜底",
                            fontSize = 11.sp,
                            color = if (accessibilityGranted) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                    Button(
                        onClick = onRequestAccessibility,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (accessibilityGranted) Color(0xFFF1F5F9) else Color(0xFF1677FF)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (accessibilityGranted) "已开通" else "去开通", color = if (accessibilityGranted) Color(0xFF64748B) else Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Bento 3: 当前运行模型与快捷切换
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前生效大模型", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    Text(modelName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1677FF))
                }
                Text("快捷切换模型引擎：", fontSize = 11.sp, color = Color(0xFF64748B))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("deepseek-chat", "deepseek-reasoner", "qwen-turbo").forEach { m ->
                        val active = m == modelName
                        SuggestionChip(
                            onClick = { onQuickSwitchModel(m) },
                            label = { Text(m, fontSize = 11.sp, color = if (active) Color(0xFF1677FF) else Color(0xFF475569)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (active) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = if (active) Color(0xFF93C5FD) else Color(0xFFE2E8F0)
                            )
                        )
                    }
                }
            }
        }

        // 操作指引卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = "Tip", tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("求职实战操作技巧：", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. 打开悬浮球后直接去 BOSS直聘 看岗位；\n2. 点击屏幕侧边猫猫头像，浮窗瞬间生成招呼语；\n3. 点击【复制此话术并关闭】直接去粘贴发送，快速拿下面试！",
                    fontSize = 11.sp,
                    color = Color(0xFF78350F),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// Tab 1: 求职档案中心 Profile
@Composable
fun ProfileTab(
    context: Context,
    resumeContent: String,
    uploadedFileName: String,
    isParsing: Boolean,
    onUploadClick: () -> Unit,
    onResumeChange: (String) -> Unit,
    onResetDefault: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 上传文件卡片 (紫色高光)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF5FF)),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFFD8B4FE), Color(0xFFC084FC))))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = "Upload", tint = Color(0xFF722ED1), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("导入本地简历文件", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF581C87))
                        Text("支持 PDF、Word (.docx) 及纯文本格式", fontSize = 11.sp, color = Color(0xFF7E22CE))
                    }
                }

                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF722ED1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isParsing) "正在解析文件中..." else "📁 选择手机简历文件导入", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                if (uploadedFileName.isNotBlank()) {
                    Text("最近导入：$uploadedFileName", fontSize = 11.sp, color = Color(0xFF6B21A8), fontWeight = FontWeight.Medium)
                }
            }
        }

        // 简历核心亮点标签
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("简历核心提取亮点", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    TextButton(onClick = onResetDefault) {
                        Text("恢复预设", fontSize = 12.sp, color = Color(0xFF1677FF))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("7年前端", "Vue3+TS", "大模型SSE", "直播Canvas", "全日制科班").forEach { tag ->
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(tag, fontSize = 10.sp, color = Color(0xFF475569), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                Text("以下内容将作为打招呼时的核心匹配依据（可直接编辑）：", fontSize = 11.sp, color = Color(0xFF64748B))

                OutlinedTextField(
                    value = resumeContent,
                    onValueChange = onResumeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 18.sp)
                )
            }
        }
    }
}

// Tab 2: AI 智囊设置 Settings
@Composable
fun SettingsTab(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    apiKey: String,
    baseUrl: String,
    modelName: String,
    customPrompt: String,
    isFetchingModels: Boolean,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onFetchModelsClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("API 接口配置", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key (sk-...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL 接口地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = onModelNameChange,
                        label = { Text("模型名称") },
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = onFetchModelsClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isFetchingModels) "拉取中..." else "🔄 联网拉取", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 自定义 Prompt 规则
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI 打招呼 Prompt 规则模板", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    TextButton(onClick = {
                        onPromptChange(StorageManager.DEFAULT_SYSTEM_PROMPT)
                        Toast.makeText(context, "已重置为默认 Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("重置默认", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, lineHeight = 16.sp)
                )
            }
        }
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
