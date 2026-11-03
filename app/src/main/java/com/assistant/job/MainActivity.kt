package com.assistant.job

import java.io.File

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
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

    // 模拟弹窗与尺寸切换状态
    var showSimulatedModal by remember { mutableStateOf(false) }

    // 模型弹窗与解析状态
    var showModelDialog by remember { mutableStateOf(false) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }

    // 自动更新状态管理
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    val performUpdateCheck: (Boolean) -> Unit = { isManual ->
        if (!isCheckingUpdate) {
            isCheckingUpdate = true
            if (isManual) {
                Toast.makeText(context, "正在查询云端最新版本...", Toast.LENGTH_SHORT).show()
            }
            scope.launch {
                val res = UpdateManager.checkForUpdate(context)
                isCheckingUpdate = false
                res.onSuccess { info ->
                    if (info.hasUpdate) {
                        updateInfo = info
                        downloadedApkFile = null
                        downloadError = null
                        showUpdateDialog = true
                    } else if (isManual) {
                        Toast.makeText(context, "当前已是最新版本 (v${info.currentVersion})", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    if (isManual) {
                        Toast.makeText(context, "检测更新失败: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var isParsingResume by remember { mutableStateOf(false) }
    var uploadedFileName by remember { mutableStateOf("") }

    val resumePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isParsingResume = true
            scope.launch {
                val result = ResumeParser.parseResumeUri(context, uri)
                result.onSuccess { parsedText ->
                    resumeContent = parsedText
                    StorageManager.saveResume(context, parsedText)
                    uploadedFileName = ResumeParser.getFileName(context, uri)

                    val ak = StorageManager.getApiKey(context)
                    if (ak.isNotBlank()) {
                        Toast.makeText(context, "文件读取成功，正在使用 AI 深度解析意向与经历...", Toast.LENGTH_SHORT).show()
                        val structRes = DeepSeekClient.extractStructuredResume(context, parsedText)
                        isParsingResume = false
                        structRes.onSuccess { structured ->
                            if (structured.targetRole.isNotBlank()) {
                                StorageManager.saveTargetRole(context, structured.targetRole)
                            }
                            if (structured.targetSalaryCity.isNotBlank()) {
                                StorageManager.saveTargetSalaryCity(context, structured.targetSalaryCity)
                            }
                            if (structured.coreExperience.isNotBlank()) {
                                StorageManager.saveExp1(context, structured.coreExperience)
                            }
                            if (structured.aiSummary.isNotBlank()) {
                                StorageManager.saveAiSummary(context, structured.aiSummary)
                            }
                            // 触发状态响应
                            resumeContent = parsedText
                            Toast.makeText(context, "AI 解析完成，已自动填充求职意向与工作经历", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "简历文本已导入 (AI 提取经历失败: ${it.message})", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        isParsingResume = false
                        Toast.makeText(context, "简历文本导入成功！配置 API Key 后可一键 AI 解析意向与经历", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { err ->
                    isParsingResume = false
                    Toast.makeText(context, "导入失败: ${err.message}", Toast.LENGTH_LONG).show()
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
        performUpdateCheck(false)
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
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_dashboard),
                            contentDescription = "主控",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("悬浮主控", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4F46E5),
                        selectedTextColor = Color(0xFF4F46E5),
                        indicatorColor = Color(0xFFEEF2FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_badge),
                            contentDescription = "求职档案",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("求职档案", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4F46E5),
                        selectedTextColor = Color(0xFF4F46E5),
                        indicatorColor = Color(0xFFEEF2FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lightbulb),
                            contentDescription = "面试押题",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("面试押题", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4F46E5),
                        selectedTextColor = Color(0xFF4F46E5),
                        indicatorColor = Color(0xFFEEF2FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tune),
                            contentDescription = "AI设置",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("AI设置", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4F46E5),
                        selectedTextColor = Color(0xFF4F46E5),
                        indicatorColor = Color(0xFFEEF2FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B)
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
            // 顶部精致 Mascot 顶栏
            TopHeroHeader(
                floatingServiceRunning = floatingServiceRunning,
                onOpenSettings = { selectedTab = 3 }
            )

            // 各 Tab 视窗
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(animationSpec = tween(280)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(animationSpec = tween(280)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(animationSpec = tween(280)))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "TabContentTransition"
            ) { targetTab ->
                when (targetTab) {
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
                                    Toast.makeText(context, "悬浮窗已进入休眠模式", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    floatingServiceRunning = true
                                    Toast.makeText(context, "好运猫悬浮球已就绪，快去招聘软件试试吧", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "模型底座已切换为 $m", Toast.LENGTH_SHORT).show()
                        },
                        onOpenSimulatedModal = {
                            showSimulatedModal = true
                        },
                        onCycleBubbleSize = {
                            val currentSize = StorageManager.getBubbleSize(context)
                            val sizes = listOf(56 to "标准 (56dp)", 44 to "迷你 (44dp)", 64 to "放大 (64dp)")
                            val currentIndex = sizes.indexOfFirst { it.first == currentSize }
                            val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % sizes.size
                            val (newSize, label) = sizes[nextIndex]
                            StorageManager.saveBubbleSize(context, newSize)
                            Toast.makeText(context, "浮标尺寸已调为：$label", Toast.LENGTH_SHORT).show()
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
                        onClearResume = {
                            resumeContent = ""
                            StorageManager.saveResume(context, "")
                            uploadedFileName = ""
                            Toast.makeText(context, "已清空简历内容", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> InterviewTab(
                        context = context,
                        modelName = modelName,
                        onNavigateToProfile = { selectedTab = 1 },
                        onNavigateToSettings = { selectedTab = 3 }
                    )
                    3 -> SettingsTab(
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
                                Toast.makeText(context, "请先填写 API Key", Toast.LENGTH_SHORT).show()
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
                        },
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckUpdateClick = { performUpdateCheck(true) }
                    )
                }
            }
        }
    }

    // 交互式破冰弹窗预览 Modal
    if (showSimulatedModal) {
        SimulatedFloatingDialog(
            onDismiss = { showSimulatedModal = false }
        )
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
                            Text(
                                text = m,
                                fontSize = 14.sp,
                                color = if (m == modelName) Color(0xFF4F46E5) else Color.DarkGray,
                                fontWeight = if (m == modelName) FontWeight.Bold else FontWeight.Normal
                            )
                            if (m == modelName) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = "Active",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
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

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            isDownloading = isDownloadingUpdate,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            downloadedFile = downloadedApkFile,
            onDismiss = { showUpdateDialog = false },
            onStartDownload = {
                isDownloadingUpdate = true
                downloadProgress = 0
                downloadError = null
                scope.launch {
                    val res = UpdateManager.downloadApk(context, updateInfo!!.downloadUrl) { p ->
                        downloadProgress = p
                    }
                    isDownloadingUpdate = false
                    res.onSuccess { file ->
                        downloadedApkFile = file
                        Toast.makeText(context, "新版本安装包下载完成，正在拉起系统安装...", Toast.LENGTH_SHORT).show()
                        UpdateManager.installApk(context, file)
                    }.onFailure { err ->
                        downloadError = err.message ?: "下载失败，请重试"
                    }
                }
            },
            onInstall = { file ->
                UpdateManager.installApk(context, file)
            }
        )
    }
}

// 顶部 Mascot 顶栏 (遵循 Stitch 规范)
@Composable
fun TopHeroHeader(
    floatingServiceRunning: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 好运猫头像容器 (方形圆角带轻微边框)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEEF2FF))
                        .border(1.dp, Color(0xFFE0E7FF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher_round),
                        contentDescription = "好运猫头像",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "AI 求职悬浮助手",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "在线待命中 · 实时解析岗位需求",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态指示 Pill (强制单行不折行，杜绝竖向挤压)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (floatingServiceRunning) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (floatingServiceRunning) Color(0xFFD1FAE5) else Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (floatingServiceRunning) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (floatingServiceRunning) "浮窗就绪中" else "服务待命中",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (floatingServiceRunning) Color(0xFF047857) else Color(0xFF64748B),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 快捷设置按钮
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tune),
                        contentDescription = "悬浮设置",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Tab 0: 悬浮主控 Bento 页面 (全面对齐 Stitch 设计规范)
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
    onQuickSwitchModel: (String) -> Unit,
    onOpenSimulatedModal: () -> Unit,
    onCycleBubbleSize: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Bento 1: Master Floating Hub Switch 渐变主控看板
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (floatingServiceRunning) Color(0xFF818CF8).copy(alpha = 0.4f) else Color(0xFF64748B).copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = if (floatingServiceRunning) {
                            Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF4338CA), Color(0xFF2563EB)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155), Color(0xFF1E293B)))
                        }
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // 浮标状态小徽章
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (floatingServiceRunning) Color(0xFF6EE7B7) else Color(0xFFCBD5E1))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (floatingServiceRunning) "前台浮标自适配已开启" else "悬浮服务暂休眠",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (floatingServiceRunning) "悬浮窗智能响应中" else "开启好运猫悬浮窗",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (floatingServiceRunning) {
                                    "回到桌面或切换到 BOSS 直聘，招手猫浮标随时待命，一键秒出定制破冰语"
                                } else {
                                    "悬浮窗处于休眠状态，开启后即可在BOSS直聘即时读取岗位特征"
                                },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.88f),
                                lineHeight = 16.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 原生轻量化 Master 开关
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleFloating() }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (floatingServiceRunning) 0.35f else 0.18f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                    .padding(3.dp),
                                contentAlignment = if (floatingServiceRunning) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (floatingServiceRunning) "运行中" else "已暂停",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.16f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 快捷药丸操作按钮组
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 试运行模拟弹窗
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .clickable { onOpenSimulatedModal() }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_play),
                                    contentDescription = null,
                                    tint = Color(0xFF6EE7B7),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "试运行模拟弹窗",
                                    fontSize = 11.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 调整猫咪浮标大小
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                                .clickable { onCycleBubbleSize() }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_resize),
                                    contentDescription = null,
                                    tint = Color(0xFFC7D2FE),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "调整猫咪浮标大小",
                                    fontSize = 11.5.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bento 2: 核心权限与求职战绩看板
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_security),
                                contentDescription = "Security",
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("核心权限与求职战绩", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    val allGranted = overlayGranted && accessibilityGranted
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (allGranted) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (allGranted) Color(0xFFD1FAE5) else Color(0xFFFEE2E2))
                    ) {
                        Text(
                            text = if (allGranted) "双权限已就绪" else "待完善授权",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allGranted) Color(0xFF059669) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 权限条目 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (overlayGranted) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("系统悬浮窗权限", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                            Text("保障招手猫在第三方应用顶部展示", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    if (overlayGranted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                        ) {
                            Text(
                                text = "已授权正常",
                                color = Color(0xFF047857),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestOverlay,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text("去开通", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF8FAFC))

                // 权限条目 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (accessibilityGranted) Color(0xFF10B981) else Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("无障碍自动读屏", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                            Text("实时提取HR职位描述与技术画像", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    if (accessibilityGranted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                        ) {
                            Text(
                                text = "已开通正常",
                                color = Color(0xFF047857),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onRequestAccessibility,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text("去开通", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 3列求职战绩看板
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("今日识别", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("0 次", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("破冰就绪", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("--%", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("平均响应", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("-- s", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        }
                    }
                }

                // 近期破冰战报条目
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEEF2FF).copy(alpha = 0.7f))
                        .border(1.dp, Color(0xFFE0E7FF), RoundedCornerShape(14.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF4F46E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bolt),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "待开启破冰 · 随时待命",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                                Text(
                                    text = "开启悬浮球后，在 BOSS 直聘等 App 点击猫咪即可生成",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE0E7FF))
                        ) {
                            Text(
                                text = "待触发",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bento 3: AI Model Engine 2x2 Bento 切换卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前大模型引擎", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("切换生成开场白的核心推理底模", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.dp, Color(0xFFE0E7FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F46E5))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = modelName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }

                // 2x2 引擎矩阵
                val modelConfigs = listOf(
                    Triple("deepseek-flash", "极速响应 · 1.2秒极速生成破冰词", null),
                    Triple("deepseek-r1", "深度解构岗位要求 · 匹配度极高", "深度思考"),
                    Triple("deepseek-chat", "通用多轮对话 · 语气亲切自然", null),
                    Triple("qwen-turbo", "通义千问 · 擅长互联网用语", null)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelChipItem(
                        modifier = Modifier.weight(1f),
                        id = modelConfigs[0].first,
                        desc = modelConfigs[0].second,
                        extraTag = modelConfigs[0].third,
                        currentModel = modelName,
                        onClick = { onQuickSwitchModel(modelConfigs[0].first) }
                    )
                    ModelChipItem(
                        modifier = Modifier.weight(1f),
                        id = modelConfigs[1].first,
                        desc = modelConfigs[1].second,
                        extraTag = modelConfigs[1].third,
                        currentModel = modelName,
                        onClick = { onQuickSwitchModel(modelConfigs[1].first) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelChipItem(
                        modifier = Modifier.weight(1f),
                        id = modelConfigs[2].first,
                        desc = modelConfigs[2].second,
                        extraTag = modelConfigs[2].third,
                        currentModel = modelName,
                        onClick = { onQuickSwitchModel(modelConfigs[2].first) }
                    )
                    ModelChipItem(
                        modifier = Modifier.weight(1f),
                        id = modelConfigs[3].first,
                        desc = modelConfigs[3].second,
                        extraTag = modelConfigs[3].third,
                        currentModel = modelName,
                        onClick = { onQuickSwitchModel(modelConfigs[3].first) }
                    )
                }
            }
        }

        // Bento 4: 求职实战操作技巧指引卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFFDE68A))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFFFBEB), Color(0xFFFFF7ED), Color(0xFFFFFBEB))
                        )
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_lightbulb),
                                contentDescription = "Tip",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("求职实战操作技巧", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78350F))
                    }

                    // Step 1
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "打开悬浮主控开关后，直接切换至 BOSS 直聘 浏览目标岗位；",
                            fontSize = 11.5.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }

                    // Step 2
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("2", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "长篇岗位 JD 浏览：向下自然滑动屏幕，助手自动静默去重合并全屏长文本；",
                            fontSize = 11.5.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }

                    // Step 3
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "点击屏幕侧边浮标圆形头像，系统快速解析岗位并生成定制破冰语；",
                            fontSize = 11.5.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }

                    // Step 4
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFDE68A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("4", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "点击【复制此话术并关闭】粘贴至聊天框，高响应率直达HR！",
                            fontSize = 11.5.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// 2x2 模型选项卡片
@Composable
fun ModelChipItem(
    modifier: Modifier = Modifier,
    id: String,
    desc: String,
    extraTag: String?,
    currentModel: String,
    onClick: () -> Unit
) {
    val isActive = id == currentModel || (id == "deepseek-r1" && currentModel == "deepseek-reasoner")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) Color(0xFFEEF2FF) else Color.White)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = id,
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isActive) Color(0xFF4338CA) else Color(0xFF334155)
                )
                if (extraTag != null) {
                    Surface(
                        color = if (isActive) Color(0xFFE0E7FF) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = extraTag,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF4338CA) else Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                } else if (isActive) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = desc,
                fontSize = 9.5.sp,
                color = if (isActive) Color(0xFF4F46E5).copy(alpha = 0.85f) else Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

// 模拟破冰弹窗预览 Modal Dialog (完全还原 Stitch 交互)
@Composable
fun SimulatedFloatingDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sampleGreetings = remember {
        listOf(
            "您好！看到贵司正在招聘前端架构师，我在大前端性能优化及跨端容器有5年落地经验，深度研读了职位要求，期待与您深入沟通！",
            "您好！关注贵司架构方向很久，在微前端模块化与工程效能调优方面有丰富实战经验，特来沟通！",
            "HR您好，我对该岗位的全链路性能攻坚与高并发渲染方案很有共鸣，期待交流探讨！"
        )
    }
    var greetingIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEEF2FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bolt),
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "好运猫 · 破冰弹窗预览",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "侦测到 BOSS 直聘「前端架构师」",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF059669)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "关闭",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bubble Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF).copy(alpha = 0.8f)),
                    border = BorderStroke(1.dp, Color(0xFFE0E7FF))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "智能定制破冰语",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(0.5.dp, Color(0xFFE0E7FF))
                            ) {
                                Text(
                                    text = "匹配度 96%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = sampleGreetings[greetingIndex % sampleGreetings.size],
                            fontSize = 12.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val text = sampleGreetings[greetingIndex % sampleGreetings.size]
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "破冰语已复制，直接粘贴到 BOSS 直聘！", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_copy),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("复制此话术并关闭", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            greetingIndex++
                            Toast.makeText(context, "已重新推理生成破冰语", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("换一句", fontSize = 12.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// Tab 1: 求职档案 Profile (遵循 Google Stitch 规范与真实数据流)
@Composable
fun ProfileTab(
    context: Context,
    resumeContent: String,
    uploadedFileName: String,
    isParsing: Boolean,
    onUploadClick: () -> Unit,
    onResumeChange: (String) -> Unit,
    onClearResume: () -> Unit
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var targetRole by remember { mutableStateOf(StorageManager.getTargetRole(context)) }
    var targetSalaryCity by remember { mutableStateOf(StorageManager.getTargetSalaryCity(context)) }
    var exp1Content by remember { mutableStateOf(StorageManager.getExp1(context)) }
    var aiSummary by remember { mutableStateOf(StorageManager.getAiSummary(context)) }
    var customTags by remember { mutableStateOf(StorageManager.getCustomTags(context)) }

    var isEditingIntention by remember { mutableStateOf(false) }
    var isEditingExp1 by remember { mutableStateOf(false) }
    var isEditingSummary by remember { mutableStateOf(false) }
    var isGeneratingSummary by remember { mutableStateOf(false) }

    var draftRole by remember { mutableStateOf(targetRole) }
    var draftSalaryCity by remember { mutableStateOf(targetSalaryCity) }
    var draftExp1 by remember { mutableStateOf(exp1Content) }
    var draftSummary by remember { mutableStateOf(aiSummary) }

    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }
    var isExtractingAll by remember { mutableStateOf(false) }

    LaunchedEffect(resumeContent) {
        targetRole = StorageManager.getTargetRole(context)
        targetSalaryCity = StorageManager.getTargetSalaryCity(context)
        exp1Content = StorageManager.getExp1(context)
        aiSummary = StorageManager.getAiSummary(context)
        draftRole = targetRole
        draftSalaryCity = targetSalaryCity
        draftExp1 = exp1Content
        draftSummary = aiSummary
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ResumeUploadStatusCard (简历状态与上传卡片)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                if (isParsing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF4F46E5),
                        trackColor = Color(0xFFEEF2FF)
                    )
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_document),
                                    contentDescription = "Document",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isBound = resumeContent.isNotBlank() || uploadedFileName.isNotBlank()
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isBound) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                                        border = BorderStroke(1.dp, if (isBound) Color(0xFFD1FAE5) else Color(0xFFFDE68A))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isBound) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_check),
                                                    contentDescription = null,
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = if (isBound) "已绑定简历" else "未录入简历",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isBound) Color(0xFF047857) else Color(0xFFB45309)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PDF / 文本解析",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (uploadedFileName.isNotBlank()) {
                                        uploadedFileName
                                    } else if (resumeContent.isNotBlank()) {
                                        "已录入个人简历"
                                    } else {
                                        "暂未导入简历文件"
                                    },
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
                            modifier = Modifier.clickable { onUploadClick() }
                        ) {
                            Text(
                                text = "导入",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (resumeContent.isNotBlank()) "简历已就绪 · 本地安全加密存储" else "暂无简历档案",
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_security),
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "隐私安全保护",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onUploadClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_upload),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isParsing) "正在解析文件..." else "上传 / 导入简历 (支持 PDF 与 TXT)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. StructuredCandidateInfo (结构化求职期望与经历)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("求职期望与经历卡片", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text("结构化匹配", fontSize = 9.5.sp, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text("AI在招聘软件一键打招呼时精准提取此卡片", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable {
                        targetRole = ""
                        targetSalaryCity = ""
                        exp1Content = ""
                        aiSummary = ""
                        customTags = emptyList()
                        StorageManager.saveTargetRole(context, "")
                        StorageManager.saveTargetSalaryCity(context, "")
                        StorageManager.saveExp1(context, "")
                        StorageManager.saveAiSummary(context, "")
                        StorageManager.saveCustomTags(context, emptyList())
                        Toast.makeText(context, "已清空所有档案与标签", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空档案", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                    }
                }
            }

            // 卡片 1: 求职意向
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_target),
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("求职意向", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        }

                        Text(
                            text = if (isEditingIntention) "取消" else "编辑",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5),
                            modifier = Modifier.clickable {
                                if (!isEditingIntention) {
                                    draftRole = targetRole
                                    draftSalaryCity = targetSalaryCity
                                }
                                isEditingIntention = !isEditingIntention
                            }
                        )
                    }

                    if (isEditingIntention) {
                        OutlinedTextField(
                            value = draftRole,
                            onValueChange = { draftRole = it },
                            label = { Text("目标职位") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = draftSalaryCity,
                            onValueChange = { draftSalaryCity = it },
                            label = { Text("期望薪资 / 城市") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    targetRole = draftRole
                                    targetSalaryCity = draftSalaryCity
                                    StorageManager.saveTargetRole(context, draftRole)
                                    StorageManager.saveTargetSalaryCity(context, draftSalaryCity)
                                    isEditingIntention = false
                                    Toast.makeText(context, "求职意向已同步保存", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("保存", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                    .clickable {
                                        draftRole = targetRole
                                        draftSalaryCity = targetSalaryCity
                                        isEditingIntention = true
                                    }
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("目标职位", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (targetRole.isNotBlank()) targetRole else "暂未录入目标职位",
                                        fontSize = 12.sp,
                                        fontWeight = if (targetRole.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                        color = if (targetRole.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                    .clickable {
                                        draftRole = targetRole
                                        draftSalaryCity = targetSalaryCity
                                        isEditingIntention = true
                                    }
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("期望薪资 / 城市", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (targetSalaryCity.isNotBlank()) targetSalaryCity else "暂未录入薪资/城市",
                                        fontSize = 12.sp,
                                        fontWeight = if (targetSalaryCity.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                        color = if (targetSalaryCity.isNotBlank()) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 卡片 2: 核心工作经历
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (exp1Content.isNotBlank()) Color(0xFF10B981) else Color(0xFF4F46E5))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (exp1Content.isNotBlank()) "主要项目/工作经历" else "核心工作经历 (待完善)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Text(
                                text = if (exp1Content.isNotBlank()) "已同步大模型匹配库" else "暂未录入经历，支持上方上传或右侧编辑",
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (exp1Content.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFECFDF5),
                                    border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                                ) {
                                    Text("已录入", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(exp1Content))
                                            Toast.makeText(context, "已复制经历要点到剪贴板", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_copy),
                                        contentDescription = "复制",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (isEditingExp1) "取消" else "编辑",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.clickable {
                                    if (!isEditingExp1) draftExp1 = exp1Content
                                    isEditingExp1 = !isEditingExp1
                                }
                            )
                        }
                    }

                    if (isEditingExp1) {
                        OutlinedTextField(
                            value = draftExp1,
                            onValueChange = { draftExp1 = it },
                            placeholder = { Text("在此录入您的主要业务项目亮点、技术难点或成果指标...", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 220.dp),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = {
                                    exp1Content = draftExp1
                                    StorageManager.saveExp1(context, draftExp1)
                                    isEditingExp1 = false
                                    Toast.makeText(context, "经历内容已保存", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("保存", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (exp1Content.isNotBlank()) exp1Content else "暂无工作经历记录。请在上方上传简历文件自动解析，或点击右上角【编辑】手动添加您的过往主导项目与技术亮点。",
                                fontSize = 11.5.sp,
                                color = if (exp1Content.isNotBlank()) Color(0xFF475569) else Color(0xFF94A3B8),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // 卡片 3: AI 精炼优势 (真实由大模型生成 + 彻底修复 UI 排版)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_sparkle),
                                contentDescription = null,
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI 精炼优势（即用打招呼语料）", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }

                        if (isEditingSummary) {
                            Text(
                                text = "取消",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.clickable { isEditingSummary = false }
                            )
                        } else if (aiSummary.isNotBlank()) {
                            Text(
                                text = "快捷编辑",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.clickable {
                                    draftSummary = aiSummary
                                    isEditingSummary = true
                                }
                            )
                        }
                    }

                    Text(
                        text = "基于大模型提炼个人亮点，用于在招聘软件中一键开场白破冰",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8)
                    )

                    if (isEditingSummary) {
                        OutlinedTextField(
                            value = draftSummary,
                            onValueChange = { draftSummary = it },
                            placeholder = { Text("在此直接编辑您的特色开场白语料...", fontSize = 11.5.sp, color = Color(0xFF94A3B8)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 160.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF4F46E5),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingSummary = false }) {
                                Text("放弃", fontSize = 11.5.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    aiSummary = draftSummary
                                    StorageManager.saveAiSummary(context, draftSummary)
                                    isEditingSummary = false
                                    Toast.makeText(context, "精炼优势已保存", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("保存", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        if (aiSummary.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = aiSummary,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 17.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(aiSummary))
                                        Toast.makeText(context, "已复制语料到剪贴板", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF4F46E5))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("复制语料", fontSize = 11.sp, color = Color(0xFF4F46E5))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val ak = StorageManager.getApiKey(context)
                                        if (ak.isBlank()) {
                                            Toast.makeText(context, "请先在【AI设置】配置 API Key", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (resumeContent.isBlank()) {
                                            Toast.makeText(context, "请先在下方录入简历内容", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isGeneratingSummary = true
                                        scope.launch {
                                            val res = DeepSeekClient.generateAiSummary(context)
                                            isGeneratingSummary = false
                                            res.onSuccess {
                                                aiSummary = it
                                                StorageManager.saveAiSummary(context, it)
                                                Toast.makeText(context, "AI 重新提炼成功！", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "提炼失败: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    enabled = !isGeneratingSummary
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.ic_sparkle), contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isGeneratingSummary) "提炼中..." else "AI 重新提炼", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "暂未提炼专属优势。录入简历并配置 API Key 后，可由大模型为您一键提炼 100 字核心亮点，向招聘官打招呼时效果拔群。",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 16.sp
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                draftSummary = ""
                                                isEditingSummary = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("手动填写", fontSize = 11.sp, color = Color(0xFF4F46E5))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val ak = StorageManager.getApiKey(context)
                                                if (ak.isBlank()) {
                                                    Toast.makeText(context, "请先在【AI设置】配置 API Key", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                if (resumeContent.isBlank()) {
                                                    Toast.makeText(context, "请先在下方录入或导入简历", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                isGeneratingSummary = true
                                                scope.launch {
                                                    val res = DeepSeekClient.generateAiSummary(context)
                                                    isGeneratingSummary = false
                                                    res.onSuccess {
                                                        aiSummary = it
                                                        StorageManager.saveAiSummary(context, it)
                                                        Toast.makeText(context, "AI 提炼成功！", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, "提炼失败: ${err.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp),
                                            enabled = !isGeneratingSummary
                                        ) {
                                            Icon(painter = painterResource(id = R.drawable.ic_sparkle), contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isGeneratingSummary) "提炼中..." else "AI 一键从简历提炼", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 完整简历原始文本 (供大模型全局推理)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("完整简历原始文本 (供大模型全局推理)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    if (resumeContent.isNotBlank()) {
                        TextButton(onClick = onClearResume) {
                            Text("清空文本", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }
                }

                Text("以下内容将作为打招呼时大模型读取的全局候选人信息 (修改自动保存)：", fontSize = 10.5.sp, color = Color(0xFF94A3B8))

                OutlinedTextField(
                    value = resumeContent,
                    onValueChange = onResumeChange,
                    placeholder = {
                        Text(
                            text = "可在此外粘贴您的个人简历详细正文、专业经历或项目难点，或者通过顶部按钮直接导入手机文件...",
                            fontSize = 11.5.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val ak = StorageManager.getApiKey(context)
                            if (ak.isBlank()) {
                                Toast.makeText(context, "请先在【AI设置】配置 API Key", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (resumeContent.isBlank()) {
                                Toast.makeText(context, "请先在此输入或粘贴简历正文", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isExtractingAll = true
                            scope.launch {
                                val res = DeepSeekClient.extractStructuredResume(context, resumeContent)
                                isExtractingAll = false
                                res.onSuccess { data ->
                                    if (data.targetRole.isNotBlank()) {
                                        targetRole = data.targetRole
                                        draftRole = data.targetRole
                                        StorageManager.saveTargetRole(context, data.targetRole)
                                    }
                                    if (data.targetSalaryCity.isNotBlank()) {
                                        targetSalaryCity = data.targetSalaryCity
                                        draftSalaryCity = data.targetSalaryCity
                                        StorageManager.saveTargetSalaryCity(context, data.targetSalaryCity)
                                    }
                                    if (data.coreExperience.isNotBlank()) {
                                        exp1Content = data.coreExperience
                                        draftExp1 = data.coreExperience
                                        StorageManager.saveExp1(context, data.coreExperience)
                                    }
                                    if (data.aiSummary.isNotBlank()) {
                                        aiSummary = data.aiSummary
                                        draftSummary = data.aiSummary
                                        StorageManager.saveAiSummary(context, data.aiSummary)
                                    }
                                    Toast.makeText(context, "AI 智能解析成功，已自动填入意向与经历", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "AI 解析失败: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                        enabled = !isExtractingAll
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_sparkle), contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isExtractingAll) "AI 正在全盘提取意向与经历..." else "AI 一键智能解析并填入全案",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }            }
        }
    }
}


// Tab 2: AI 设置 Settings (完全真实连通性与零假数据)
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
    onFetchModelsClick: () -> Unit,
    isCheckingUpdate: Boolean,
    onCheckUpdateClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    var isPasswordVisible by remember { mutableStateOf(false) }
    var selectedPromptStyle by remember { mutableStateOf("硬核技术型") }

    val presetEndpoints = remember {
        listOf(
            Triple("DeepSeek 官方", "https://api.deepseek.com/v1", "deepseek-chat"),
            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            Triple("阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
            Triple("OneAPI 聚合转接", "https://api.oneapi.pro/v1", "claude-3-5-sonnet")
        )
    }

    // 真实网络连通性状态 (彻底移除假 pingMs 和假 modelCount)
    var isTestingConnection by remember { mutableStateOf(false) }
    var latencyMs by remember { mutableStateOf(-1L) }
    var connectionTested by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf("") }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelDialog by remember { mutableStateOf(false) }

    // 模拟测试弹窗状态
    var showSimulationModal by remember { mutableStateOf(false) }
    var isSimulatingOnline by remember { mutableStateOf(false) }
    var simulatedGreetings by remember { mutableStateOf<List<GreetingOption>>(emptyList()) }
    var simError by remember { mutableStateOf("") }
    var simIdx by remember { mutableStateOf(0) }

    var isSavedTemporarily by remember { mutableStateOf(false) }

    LaunchedEffect(isSavedTemporarily) {
        if (isSavedTemporarily) {
            kotlinx.coroutines.delay(1600)
            isSavedTemporarily = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. API 接口配置卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF4F46E5))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("API 接口配置", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFD1FAE5))
                    ) {
                        Text(
                            text = "本地密钥加密存储",
                            fontSize = 10.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "支持 DeepSeek / 通义千问 / OpenAI / OneAPI 等所有兼容 OpenAI 规范的 API 接口",
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 15.sp
                )

                // 预设快速切换
                Text("快速填入预设基地址：", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetEndpoints.forEach { (name, url, defaultM) ->
                        val isSelected = baseUrl == url
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFFE2E8F0)),
                            modifier = Modifier.clickable {
                                onBaseUrlChange(url)
                                onModelNameChange(defaultM)
                                connectionTested = false
                                connectionError = ""
                                Toast.makeText(context, "已切换为: $name", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = name,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        onApiKeyChange(it)
                        connectionTested = false
                        connectionError = ""
                    },
                    label = { Text("API Key (必填)") },
                    placeholder = { Text("sk-...", color = Color(0xFFCBD5E1)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                painter = painterResource(id = if (isPasswordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = if (isPasswordVisible) "隐藏" else "显示",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )

                // Base URL 输入框
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        onBaseUrlChange(it)
                        connectionTested = false
                        connectionError = ""
                    },
                    label = { Text("接口 Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // 当前模型选择
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("当前推理模型", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(modelName, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "请先填写 API Key", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTestingConnection = true
                            scope.launch {
                                val result = DeepSeekClient.fetchModelListWithLatency(context)
                                isTestingConnection = false
                                connectionTested = true
                                result.onSuccess { (models, latency) ->
                                    fetchedModels = models
                                    latencyMs = latency
                                    connectionError = ""
                                    showModelDialog = true
                                    Toast.makeText(context, "连通成功！测速 ${latency}ms，已拉取 ${models.size} 个可用模型", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    connectionError = err.message ?: "连接超时"
                                    Toast.makeText(context, "测试失败: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        enabled = !isTestingConnection
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync),
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTestingConnection) "测速中..." else "测试连通并拉取模型",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }

                // 真实连通性状态卡片 (100% 拒绝假数据)
                val hasApiKey = apiKey.isNotBlank()
                val cardBg = when {
                    !hasApiKey -> Color(0xFFF8FAFC)
                    isTestingConnection -> Color(0xFFEEF2FF)
                    connectionTested && connectionError.isNotBlank() -> Color(0xFFFEF2F2)
                    connectionTested && fetchedModels.isNotEmpty() -> Color(0xFFECFDF5)
                    else -> Color(0xFFEEF2FF).copy(alpha = 0.5f)
                }
                val cardBorder = when {
                    !hasApiKey -> Color(0xFFE2E8F0)
                    isTestingConnection -> Color(0xFFC7D2FE)
                    connectionTested && connectionError.isNotBlank() -> Color(0xFFFECACA)
                    connectionTested && fetchedModels.isNotEmpty() -> Color(0xFFD1FAE5)
                    else -> Color(0xFFE0E7FF)
                }
                val dotColor = when {
                    !hasApiKey -> Color(0xFF94A3B8)
                    isTestingConnection -> Color(0xFF3B82F6)
                    connectionTested && connectionError.isNotBlank() -> Color(0xFFEF4444)
                    connectionTested && fetchedModels.isNotEmpty() -> Color(0xFF10B981)
                    else -> Color(0xFF818CF8)
                }
                val statusText = when {
                    !hasApiKey -> "未配置 API Key · 处于离线状态"
                    isTestingConnection -> "正在测试连通性并拉取模型..."
                    connectionTested && connectionError.isNotBlank() -> "连通失败: $connectionError"
                    connectionTested && fetchedModels.isNotEmpty() -> "连通正常 · 耗时 ${latencyMs}ms"
                    else -> "API Key 已保存 · 待测试连通性"
                }
                val countText = when {
                    !hasApiKey -> "0 个可用模型"
                    isTestingConnection -> "测速中..."
                    connectionTested && connectionError.isNotBlank() -> "不可用"
                    connectionTested && fetchedModels.isNotEmpty() -> "已就绪 ${fetchedModels.size} 个模型"
                    else -> "待检测"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (connectionTested && connectionError.isNotBlank()) Color(0xFFB91C1C) else if (connectionTested && fetchedModels.isNotEmpty()) Color(0xFF047857) else Color(0xFF64748B),
                                maxLines = 1
                            )
                        }
                        Text(
                            text = countText,
                            fontSize = 10.5.sp,
                            color = if (connectionTested && fetchedModels.isNotEmpty()) Color(0xFF059669) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // 2. AI Prompt Engineering 配置卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF4F46E5))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI 打招呼 Prompt 提示词模板", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }

                    Text(
                        text = "重置默认",
                        fontSize = 11.sp,
                        color = Color(0xFF4F46E5),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onPromptChange(StorageManager.DEFAULT_SYSTEM_PROMPT)
                            Toast.makeText(context, "已恢复系统默认 Prompt 模板", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Text(
                    text = "系统将把此 Prompt 作为 System 指令传递给大模型，结合候选人简历与职位 JD 进行深度推理匹配：",
                    fontSize = 10.5.sp,
                    color = Color(0xFF94A3B8)
                )

                // 风格预设快速切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("硬核技术型", "全面契合型", "名企资深型").forEach { style ->
                        val isSelected = selectedPromptStyle == style
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF818CF8) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedPromptStyle = style
                                    val newPrompt = when (style) {
                                        "硬核技术型" -> StorageManager.DEFAULT_SYSTEM_PROMPT
                                        "全面契合型" -> "你是一位务实而亲和的求职全栈工程师。\n请仔细阅读目标岗位的【JD要求】与【候选人简历】，找出最贴合岗位业务场景的 2 点优势。\n要求：语气真诚自然，言之有物，不夸大，不浮躁，直接给出适合直接向 HR/业务负责人 发送的破冰招呼语（100字左右）。"
                                        else -> "你是一位资深业务技术骨干/负责人。\n请依据目标岗位 JD，迅速提炼候选人在过往主导业务、高可用架构落地与团队协作方面的核心战绩。\n要求：展现严谨成熟的工程思维与业务责任感，言简意赅，120字内。"
                                    }
                                    onPromptChange(newPrompt)
                                    Toast.makeText(context, "已切换为：$style 预设", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = style,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, lineHeight = 16.sp)
                )
            }
        }

        // 3. 操作按钮组
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    StorageManager.saveApiKey(context, apiKey)
                    StorageManager.saveBaseUrl(context, baseUrl)
                    StorageManager.saveModel(context, modelName)
                    StorageManager.saveCustomPrompt(context, customPrompt)
                    isSavedTemporarily = true
                    Toast.makeText(context, "已成功应用到底座！", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSavedTemporarily) Color(0xFF059669) else Color(0xFF4F46E5)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSavedTemporarily) "已保存生效！" else "保存配置",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "请先填写并保存 API Key，方可在线模拟测试", Toast.LENGTH_LONG).show()
                        return@OutlinedButton
                    }
                    showSimulationModal = true
                    // 真实在线调用生成
                    isSimulatingOnline = true
                    simError = ""
                    scope.launch {
                        val testJd = "岗位职责：负责核心业务微前端架构与高并发页面研发，要求精通前端工程化、TypeScript与性能调优，具有大规模落地实践经验。"
                        val res = DeepSeekClient.generateGreetings(context, testJd)
                        isSimulatingOnline = false
                        res.onSuccess {
                            simulatedGreetings = it
                            simIdx = 0
                        }.onFailure { err ->
                            simError = err.message ?: "生成失败"
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("模拟在线破冰", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 4. 版本与在线更新卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "软件版本与在线更新",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "已启用持久签名认证，支持应用内一键覆盖升级",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5)
                    ) {
                        Text(
                            text = "官方正式版",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                val currentInstalledVersion = remember {
                    try {
                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        }
                        pInfo.versionName ?: "1.3.8"
                    } catch (e: Exception) {
                        "1.3.8"
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前安装版本：v$currentInstalledVersion",
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = onCheckUpdateClick,
                        enabled = !isCheckingUpdate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在检查...", fontSize = 11.5.sp)
                        } else {
                            Text("检查最新版本", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
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
                                    onModelNameChange(m)
                                    StorageManager.saveModel(context, m)
                                    showModelDialog = false
                                    Toast.makeText(context, "已选择: $m", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = m,
                                fontSize = 14.sp,
                                color = if (m == modelName) Color(0xFF4F46E5) else Color.DarkGray,
                                fontWeight = if (m == modelName) FontWeight.Bold else FontWeight.Normal
                            )
                            if (m == modelName) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = "Active",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("完成") }
            }
        )
    }

    // 真实在线模拟破冰弹窗 (100% 拒绝静态假前端话术)
    if (showSimulationModal) {
        Dialog(onDismissRequest = { showSimulationModal = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEEF2FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chat),
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("AI 破冰语 · 真实在线推理", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                Text("底座: $modelName", fontSize = 10.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                            }
                        }

                        IconButton(
                            onClick = { showSimulationModal = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "关闭",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isSimulatingOnline) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF4F46E5), strokeWidth = 2.dp)
                                Text("正在调用 $modelName 在线推理...", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    } else if (simError.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFEF2F2))
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text("调用失败: $simError", fontSize = 11.sp, color = Color(0xFFB91C1C))
                        }
                    } else if (simulatedGreetings.isNotEmpty()) {
                        val currentOpt = simulatedGreetings[simIdx % simulatedGreetings.size]
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEEF2FF)
                        ) {
                            Text(
                                text = "生成方案: ${currentOpt.title}",
                                fontSize = 10.sp,
                                color = Color(0xFF4338CA),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = currentOpt.text,
                                fontSize = 11.5.sp,
                                color = Color(0xFF1E293B),
                                lineHeight = 17.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(currentOpt.text))
                                    Toast.makeText(context, "破冰语已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                                    showSimulationModal = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_copy),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("采纳复制", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            if (simulatedGreetings.size > 1) {
                                Button(
                                    onClick = {
                                        simIdx++
                                    },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))
                                ) {
                                    Text("看方案二", fontSize = 11.5.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
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
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(intent)
        }
    }
}

private fun requestAccessibilityPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "无法直接打开设置，请在系统设置中找到'无障碍'", Toast.LENGTH_LONG).show()
    }
}


@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadError: String?,
    downloadedFile: File?,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit,
    onInstall: (File) -> Unit
) {
    Dialog(onDismissRequest = { if (!isDownloading) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 顶栏：新版本徽章与标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "发现新版本",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "当前: v${updateInfo.currentVersion} -> 最新: v${updateInfo.latestVersion}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF)
                    ) {
                        Text(
                            text = "v${updateInfo.latestVersion}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // 更新日志展示区
                Text(
                    text = "更新内容：",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = updateInfo.releaseNotes.ifBlank { "包含多项性能优化与体验升级。" },
                        fontSize = 11.5.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.sp
                    )
                }

                // 错误提示
                if (!downloadError.isNullOrBlank()) {
                    Text(
                        text = "下载中断: $downloadError",
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626)
                    )
                }

                // 下载进度条
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "正在下载新版本安装包...",
                                fontSize = 11.sp,
                                color = Color(0xFF4338CA)
                            )
                            Text(
                                text = "$downloadProgress%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF4F46E5),
                            trackColor = Color(0xFFEEF2FF)
                        )
                    }
                }

                // 操作按钮组
                if (isDownloading) {
                    Text(
                        text = "下载过程中请勿退出，完成后将自动拉起系统安装确认",
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (downloadedFile != null && downloadedFile.exists()) {
                    Button(
                        onClick = { onInstall(downloadedFile) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text("下载完成，立即覆盖安装", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("稍后再说", fontSize = 12.sp, color = Color(0xFF64748B))
                        }

                        Button(
                            onClick = onStartDownload,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                        ) {
                            Text(
                                text = if (downloadError != null) "重试下载" else "立即下载更新",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}


// =========================================================================
// Tab 2: 岗位针对性模拟面试 · 考点预测押题 (遵循 Google Stitch 规范)
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewTab(
    context: Context,
    modelName: String,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // 默认尝试预填无障碍服务最新捕获的岗位 JD
    var jdText by remember {
        mutableStateOf(
            if (JobAccessibilityService.lastExtractedJd.isNotBlank()) {
                JobAccessibilityService.lastExtractedJd
            } else {
                JobAccessibilityService.cachedAppScreenText
            }
        )
    }

    var isGenerating by remember { mutableStateOf(false) }
    var questions by remember { mutableStateOf<List<InterviewQuestion>>(emptyList()) }
    var selectedCategoryFilter by remember { mutableStateOf("全部考点") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandedIndices by remember { mutableStateOf(setOf(1)) }

    val fullResume = remember { StorageManager.getResume(context) }
    val aiSummary = remember { StorageManager.getAiSummary(context) }
    val targetRole = remember { StorageManager.getTargetRole(context) }

    val hasResume = fullResume.isNotBlank() || aiSummary.isNotBlank()

    val categories = remember(questions) {
        val set = mutableSetOf("全部考点")
        questions.forEach { set.add(it.category) }
        set.toList()
    }

    val filteredQuestions = remember(questions, selectedCategoryFilter) {
        if (selectedCategoryFilter == "全部考点") {
            questions
        } else {
            questions.filter { it.category == selectedCategoryFilter }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 顶部 Hero Banner 卡片
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lightbulb),
                            contentDescription = "押题",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "岗位针对性模拟面试 · 考点押题",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "结合目标岗位核心职责与您的真实项目经历，智能预测连环追问",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = "• 技术深水区原理",
                            fontSize = 11.sp,
                            color = Color(0xFF1D4ED8),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFAF5FF)
                    ) {
                        Text(
                            text = "• 项目经历真实性深挖",
                            fontSize = 11.sp,
                            color = Color(0xFF7E22CE),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFFBEB)
                    ) {
                        Text(
                            text = "• 架构场景设计",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5)
                    ) {
                        Text(
                            text = "• STAR 高分回答与避坑",
                            fontSize = 11.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 2. 目标岗位 JD 输入卡片
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "目标岗位 JD (要求与职责)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val latest = if (JobAccessibilityService.lastExtractedJd.isNotBlank()) {
                                    JobAccessibilityService.lastExtractedJd
                                } else {
                                    JobAccessibilityService.cachedAppScreenText
                                }
                                if (latest.isNotBlank()) {
                                    jdText = latest
                                    Toast.makeText(context, "已同步屏幕读取的岗位 JD (${latest.length} 字)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "屏幕上暂无岗位文本，请在BOSS浏览后再次读取", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("读取屏幕JD", fontSize = 11.5.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    jdText = clip
                                    Toast.makeText(context, "已粘贴剪贴板内容", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("粘贴", fontSize = 11.5.sp, color = Color(0xFF2563EB))
                        }

                        if (jdText.isNotBlank()) {
                            TextButton(
                                onClick = { jdText = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("清空", fontSize = 11.5.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = jdText,
                    onValueChange = { jdText = it },
                    placeholder = {
                        Text(
                            text = "请在此输入或点击上方【读取屏幕JD】同步目标岗位的技术栈要求、业务场景与工作职责...",
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 180.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4F46E5),
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val count = jdText.trim().length
                    Text(
                        text = if (count >= 20) "当前 JD 共 $count 字 (满足深度分析条件)" else if (count > 0) "当前仅 $count 字 (建议不少于 20 字以提高准度)" else "等待输入岗位要求",
                        fontSize = 11.sp,
                        color = if (count >= 20) Color(0xFF059669) else if (count > 0) Color(0xFFD97706) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. 候选人经历绑定状态卡片
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (hasResume) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
            ),
            border = BorderStroke(1.dp, if (hasResume) Color(0xFFBBF7D0) else Color(0xFFFDE68A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (hasResume) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = if (hasResume) R.drawable.ic_badge else R.drawable.ic_info),
                        contentDescription = "状态",
                        tint = if (hasResume) Color(0xFF16A34A) else Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasResume) "已关联求职档案经历" else "尚未导入个人简历",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasResume) Color(0xFF15803D) else Color(0xFFB45309)
                    )
                    Text(
                        text = if (hasResume) {
                            if (targetRole.isNotBlank()) "主修方向：$targetRole" else "已自动调取简历项目事实，将针对您的真实经历设计回答思路"
                        } else {
                            "未导入简历时仅基于岗位通用考点预测；建议导入简历，量身生成回答策略"
                        },
                        fontSize = 11.sp,
                        color = if (hasResume) Color(0xFF166534) else Color(0xFF92400E)
                    )
                }
                TextButton(
                    onClick = onNavigateToProfile,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasResume) "查看/修改" else "前往上传",
                        fontSize = 11.5.sp,
                        color = if (hasResume) Color(0xFF15803D) else Color(0xFFB45309),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. 一键生成按钮
        Button(
            onClick = {
                val jd = jdText.trim()
                if (jd.length < 10) {
                    Toast.makeText(context, "请先输入或读取至少 10 个字的岗位 JD", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isGenerating = true
                errorMessage = null
                coroutineScope.launch {
                    val result = DeepSeekClient.generateInterviewQuestions(context, jd)
                    isGenerating = false
                    result.onSuccess { list ->
                        if (list.isNotEmpty()) {
                            questions = list
                            expandedIndices = setOf(1)
                            selectedCategoryFilter = "全部考点"
                            Toast.makeText(context, "成功生成 ${list.size} 道针对性高频考题！", Toast.LENGTH_SHORT).show()
                        } else {
                            errorMessage = "未成功提取出考题，建议丰富岗位 JD 内容后重试"
                        }
                    }.onFailure { err ->
                        errorMessage = err.localizedMessage ?: "生成考题失败，请检查网络或配置"
                    }
                }
            },
            enabled = !isGenerating && jdText.trim().length >= 10,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5),
                disabledContainerColor = Color(0xFFCBD5E1)
            )
        ) {
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "正在交叉比对 JD 与简历生成深度押题中...",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (questions.isEmpty()) "开始预测岗位高频面试考题" else "重新深度预测考题",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Text(
            text = "当前调用大模型：$modelName · 涵盖底层原理、真实性深挖、架构设计与避坑沟通 4 维考核",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        // 5. 错误提示
        if (errorMessage != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "生成提示：$errorMessage",
                        fontSize = 12.5.sp,
                        color = Color(0xFFDC2626),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onNavigateToSettings,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("前往【AI设置】检查 API Key 或连通性", fontSize = 11.5.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 6. 分类筛选 Chip 条 (当已有考题时)
        if (questions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = if (cat == "全部考点") questions.size else questions.count { it.category == cat }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = {
                            Text(
                                text = "$cat ($count)",
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4F46E5),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF475569)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)
                        )
                    )
                }
            }

            // 7. 考题列表卡片流
            filteredQuestions.forEach { q ->
                val actualIndex = questions.indexOf(q) + 1
                val isExpanded = expandedIndices.contains(actualIndex)

                val (badgeBg, badgeText) = when {
                    q.category.contains("技术") -> Pair(Color(0xFFEFF6FF), Color(0xFF1D4ED8))
                    q.category.contains("项目") || q.category.contains("深挖") -> Pair(Color(0xFFFAF5FF), Color(0xFF7E22CE))
                    q.category.contains("架构") || q.category.contains("场景") -> Pair(Color(0xFFFFFBEB), Color(0xFFB45309))
                    else -> Pair(Color(0xFFECFDF5), Color(0xFF047857))
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedIndices = if (isExpanded) {
                                        expandedIndices - actualIndex
                                    } else {
                                        expandedIndices + actualIndex
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeBg
                                ) {
                                    Text(
                                        text = q.category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Q$actualIndex",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Text(
                                text = if (isExpanded) "收起解析 ▲" else "展开高分回答 ▼",
                                fontSize = 11.sp,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = q.question,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            lineHeight = 21.sp
                        )

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // 考官意图
                            if (q.intention.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "【考官核心意图与下潜陷阱】",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E293B)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = q.intention,
                                            fontSize = 12.sp,
                                            color = Color(0xFF475569),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // 回答思路 (STAR)
                            if (q.answerStrategy.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "【结合真实经历的推荐回答逻辑 (STAR 法则)】",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = q.answerStrategy,
                                            fontSize = 12.sp,
                                            color = Color(0xFF166534),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // 避坑指南
                            if (q.pitfall.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "【大厂面试避坑红线】",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = q.pitfall,
                                            fontSize = 12.sp,
                                            color = Color(0xFF991B1B),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            // 底部操作栏
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val fullText = buildString {
                                            append("【题目】：").append(q.question).append("\n\n")
                                            if (q.intention.isNotBlank()) append("【考官意图】：").append(q.intention).append("\n\n")
                                            if (q.answerStrategy.isNotBlank()) append("【回答思路】：").append(q.answerStrategy).append("\n\n")
                                            if (q.pitfall.isNotBlank()) append("【避坑指南】：").append(q.pitfall)
                                        }
                                        clipboardManager.setText(AnnotatedString(fullText))
                                        Toast.makeText(context, "已复制 Q$actualIndex 题目与回答思路到剪贴板！", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_copy),
                                        contentDescription = "复制",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("复制本题与思路", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else if (!isGenerating) {
            // 空状态提示
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lightbulb),
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无预测考题",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "在上方提供目标岗位的 JD 职责要求，并点击【开始预测岗位高频面试考题】，AI 将为您量身定制全套面试攻防策略！",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
