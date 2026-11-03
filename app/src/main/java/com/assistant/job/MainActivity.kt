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
                    Toast.makeText(context, "成功导入简历文件", Toast.LENGTH_SHORT).show()
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
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_rocket),
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
                onOpenSettings = { selectedTab = 2 }
            )

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
                        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI 求职悬浮助手",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEEF2FF),
                            border = BorderStroke(1.dp, Color(0xFFE0E7FF))
                        ) {
                            Text(
                                text = "PRO",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "BOSS直聘 · 实时感知与秒速破冰",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态指示 Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (floatingServiceRunning) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (floatingServiceRunning) Color(0xFFD1FAE5) else Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (floatingServiceRunning) Color(0xFF10B981) else Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (floatingServiceRunning) "浮窗就绪中" else "服务待命中",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (floatingServiceRunning) Color(0xFF047857) else Color(0xFF64748B)
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
                            Text("18 次", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("高回复率", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("83%", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFE2E8F0)))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("平均响应", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("1.1s", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4F46E5))
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
                                    text = "近期破冰：高级前端开发 · 匹配3大优势",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                                Text(
                                    text = "BOSS直聘 · 腾讯音乐 · HR 2分钟前已读",
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
                                text = "已破冰",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4338CA),
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
                            text = "点击屏幕侧边浮标圆形头像，系统在0.2秒内快速解析岗位并生成定制破冰语；",
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
                if (isActive) {
                    Surface(
                        color = Color(0xFF4F46E5),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "当前",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                } else if (extraTag != null) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = extraTag,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
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

// Tab 1: 求职档案中心 Profile (修复标签折断、删除预设文案)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 上传文件卡片 (品牌紫蓝高光)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E7FF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_upload),
                        contentDescription = "Upload",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("导入本地简历文件", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF312E81))
                        Text("支持 PDF、Word (.docx) 及纯文本格式", fontSize = 11.sp, color = Color(0xFF4338CA))
                    }
                }

                Button(
                    onClick = onUploadClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isParsing) "正在解析文件中..." else "选择手机简历文件导入",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uploadedFileName.isNotBlank()) {
                    Text("最近导入：$uploadedFileName", fontSize = 11.sp, color = Color(0xFF4338CA), fontWeight = FontWeight.Medium)
                }
            }
        }

        // 简历核心亮点标签 (支持水平平滑滑动，杜绝文字垂直折叠)
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
                    Text("简历核心提取亮点", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    if (resumeContent.isNotBlank()) {
                        TextButton(onClick = onClearResume) {
                            Text("清空简历", fontSize = 11.5.sp, color = Color(0xFFDC2626))
                        }
                    }
                }

                // 动态匹配高亮标签并横向滑动排版
                val matchedTags = remember(resumeContent) {
                    val candidateKeywords = listOf("前端", "后端", "全栈", "Vue", "React", "TypeScript", "Node.js", "Java", "Python", "Go", "Android", "iOS", "AI", "大模型", "科班", "本科", "硕士", "架构", "性能优化")
                    candidateKeywords.filter { resumeContent.contains(it, ignoreCase = true) }
                }

                if (matchedTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matchedTags.forEach { tag ->
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (resumeContent.isBlank()) "暂未录入简历内容，可直接在下方粘贴或上方导入文件" else "自定义简历内容已录入",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Text("以下内容将作为打招呼时的核心匹配依据（自动保存）：", fontSize = 11.sp, color = Color(0xFF64748B))

                OutlinedTextField(
                    value = resumeContent,
                    onValueChange = onResumeChange,
                    placeholder = {
                        Text(
                            text = "请在此处粘贴您的个人简历正文、求职意向或核心技能亮点，或者点击上方直接选择手机简历文件导入...",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 340.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, lineHeight = 18.sp)
                )
            }
        }
    }
}

// Tab 2: AI 智囊设置 Settings (API Key 密码掩码 + 统一高度拉取按钮)
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
    var apiKeyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("API 接口配置", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

                // API Key 密码遮罩输入框，支持眼睛图标明暗切换
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key (sk-...)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                painter = painterResource(id = if (apiKeyVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                contentDescription = if (apiKeyVisible) "隐藏秘钥" else "显示秘钥",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Base URL 接口地址") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // 模型名称与联网拉取按钮（高度 56dp 严格对齐，主色调品牌紫蓝）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = onModelNameChange,
                        label = { Text("模型名称") },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    Button(
                        onClick = onFetchModelsClick,
                        modifier = Modifier
                            .height(56.dp)
                            .weight(0.9f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isFetchingModels) "拉取中..." else "联网拉取",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 自定义 Prompt 规则
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
                    Text("AI 打招呼 Prompt 规则模板", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    TextButton(onClick = {
                        onPromptChange(StorageManager.DEFAULT_SYSTEM_PROMPT)
                        Toast.makeText(context, "已重置为默认 Prompt", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("重置默认", fontSize = 11.5.sp)
                    }
                }

                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
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
