package com.assistant.job

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scope = rememberCoroutineScope()

    var overlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    var floatingServiceRunning by remember { mutableStateOf(FloatingService.isRunning) }

    var apiKey by remember { mutableStateOf(StorageManager.getApiKey(context)) }
    var baseUrl by remember { mutableStateOf(StorageManager.getBaseUrl(context)) }
    var modelName by remember { mutableStateOf(StorageManager.getModel(context)) }
    var resumeContent by remember { mutableStateOf(StorageManager.getResume(context)) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App 标题
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1677FF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🤖 AI 求职悬浮助手",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "屏幕无障碍提取 JD · DeepSeek 定制招呼语 · 纯本地免服务器",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }

        // 权限检测卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "📌 必要系统权限配置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F)
                )

                // 悬浮窗权限
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("1. 悬浮窗权限", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (overlayGranted) "已开启 ✅" else "未开启（用于显示屏幕侧边悬浮球）",
                            fontSize = 12.sp,
                            color = if (overlayGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F)
                        )
                    }
                    Button(
                        onClick = {
                            requestOverlayPermission(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayGranted) Color(0xFFE6F4FF) else Color(0xFF1677FF)
                        )
                    ) {
                        Text(
                            if (overlayGranted) "去检查" else "去授权",
                            color = if (overlayGranted) Color(0xFF1677FF) else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                Divider()

                // 无障碍权限
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("2. 无障碍读屏服务", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (accessibilityGranted) "已开启 ✅" else "未开启（用于自动抓取 BOSS 的 JD）",
                            fontSize = 12.sp,
                            color = if (accessibilityGranted) Color(0xFF52C41A) else Color(0xFFFF4D4F)
                        )
                    }
                    Button(
                        onClick = {
                            requestAccessibilityPermission(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (accessibilityGranted) Color(0xFFE6F4FF) else Color(0xFF1677FF)
                        )
                    ) {
                        Text(
                            if (accessibilityGranted) "去检查" else "去开启",
                            color = if (accessibilityGranted) Color(0xFF1677FF) else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                Divider()

                // 启动悬浮窗服务
                Button(
                    onClick = {
                        if (!checkOverlayPermission(context)) {
                            Toast.makeText(context, "请先授予【悬浮窗权限】", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val intent = Intent(context, FloatingService::class.java)
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
                            Toast.makeText(context, "悬浮球已启动，请切换到 BOSS直聘 使用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (floatingServiceRunning) Color(0xFFFF4D4F) else Color(0xFF52C41A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (floatingServiceRunning) "⏹ 关闭悬浮球" else "▶ 启动悬浮球（开启后切去 BOSS 体验）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 大模型配置
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "⚙️ DeepSeek / 大模型 API 设置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F1F1F)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        StorageManager.saveApiKey(context, it)
                    },
                    label = { Text("DeepSeek API Key (sk-...)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            StorageManager.saveBaseUrl(context, it)
                        },
                        label = { Text("接口地址 (Base URL)") },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = modelName,
                        onValueChange = {
                            modelName = it
                            StorageManager.saveModel(context, it)
                        },
                        label = { Text("模型名称") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }

        // 简历管理配置
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
                    Text(
                        text = "📝 我的简历核心亮点",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F)
                    )
                    TextButton(onClick = {
                        resumeContent = StorageManager.DEFAULT_RESUME
                        StorageManager.saveResume(context, StorageManager.DEFAULT_RESUME)
                        Toast.makeText(context, "已重置为预设前端亮点简历", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("恢复默认", fontSize = 12.sp)
                    }
                }

                Text(
                    "AI 会以此简历内容与屏幕上的岗位 JD 做交集匹配：",
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

        // 使用指南
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 手机使用流程：",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFD48806)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. 填入您的 DeepSeek API Key 并点击启动悬浮球。\n" +
                            "2. 打开【BOSS直聘】或【猎聘】进入任意岗位详情页。\n" +
                            "3. 点击屏幕边缘的【AI招呼】蓝色悬浮球。\n" +
                            "4. 悬浮窗自动识别 JD 并调取大模型生成 2 种打招呼风格。\n" +
                            "5. 点击【复制此话术并关闭】，进入聊天框直接粘贴发送！",
                    fontSize = 13.sp,
                    color = Color(0xFF595959),
                    lineHeight = 20.sp
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
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }
}

private fun checkAccessibilityPermission(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    for (service in enabledServices) {
        if (service.resolveInfo.serviceInfo.packageName == context.packageName) {
            return true
        }
    }
    return false
}

private fun requestAccessibilityPermission(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    context.startActivity(intent)
}
