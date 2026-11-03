package com.assistant.job

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingService : Service() {

    companion object {
        const val CHANNEL_ID = "job_assistant_floating_channel"
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private var floatingBallView: View? = null
    private var dialogOverlayView: View? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        createFloatingBall()
    }

    private fun startForegroundNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "求职助手悬浮窗运行中",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("求职悬浮助手已就绪")
                .setContentText("悬浮球已显示在屏幕侧边，点击即可针对当前岗位生成打招呼语")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val ballSize = (64 * density).toInt()

        val params = WindowManager.LayoutParams(
            ballSize,
            ballSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = (resources.displayMetrics.heightPixels * 0.35).toInt()
        }

        val ballView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_round)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke((2.5f * density).toInt(), Color.parseColor("#E0E0E0"))
            }
            elevation = 24f
            clipToOutline = true
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        ballView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(ballView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        onFloatingBallClicked()
                    }
                    true
                }
                else -> false
            }
        }

        floatingBallView = ballView
        try {
            windowManager.addView(ballView, params)
            Toast.makeText(this, "悬浮球已显示在屏幕左侧，可随意拖动！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "悬浮窗添加失败: ${e.message}，请确认已开启悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun onFloatingBallClicked() {
        val jdText = JobAccessibilityService.captureCurrentScreenText()
        showGenerationDialog(jdText)
    }

    private fun showGenerationDialog(initialJd: String) {
        if (dialogOverlayView != null) {
            dismissDialog()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val dialogWidth = (displayMetrics.widthPixels * 0.92).toInt()
        val dialogHeight = (displayMetrics.heightPixels * 0.70).toInt()

        val params = WindowManager.LayoutParams(
            dialogWidth,
            dialogHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val density = resources.displayMetrics.density

        // 根容器
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24 * density
                setStroke(2, Color.parseColor("#E0E0E0"))
            }
            elevation = 28f
        }

        // 顶栏（标题 + 关闭按钮）
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(this).apply {
            text = "AI 定制招呼语"
            textSize = 17f
            setTextColor(Color.parseColor("#1F1F1F"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismissDialog() }
        }
        headerLayout.addView(titleView)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        // 滚动内容容器
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)

        dialogOverlayView = rootLayout
        try {
            windowManager.addView(rootLayout, params)
        } catch (e: Exception) {
            Toast.makeText(this, "弹窗失败: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }

        if (initialJd.length >= 20) {
            triggerAiGeneration(contentContainer, initialJd)
        } else {
            // 如果自动读屏由于权限或厂商屏蔽未读到内容，显示手动输入/粘贴面板
            showManualJdInput(contentContainer)
        }
    }

    private fun showManualJdInput(container: LinearLayout) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val tipText = TextView(this).apply {
            text = "提示：未能通过无障碍自动读到屏幕文字。\n您可直接点击下方【粘贴剪贴板】快速生成："
            setTextColor(Color.parseColor("#D48806"))
            textSize = 13f
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val jdInput = EditText(this).apply {
            hint = "此处长按粘贴当前岗位的职责或任职要求..."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            minLines = 4
            maxLines = 6
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = 12 * density
                setStroke(1, Color.LTGRAY)
            }
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (12 * density).toInt(), 0, 0)
        }

        val pasteBtn = Button(this).apply {
            text = "粘贴剪贴板"
            textSize = 12f
            setTextColor(Color.parseColor("#1677FF"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6F4FF"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (8 * density).toInt()
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString() ?: ""
                    jdInput.setText(text)
                } else {
                    Toast.makeText(this@FloatingService, "剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val generateBtn = Button(this).apply {
            text = "立即生成"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1677FF"))
                cornerRadius = 10 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            setOnClickListener {
                val input = jdInput.text.toString().trim()
                if (input.length < 10) {
                    Toast.makeText(this@FloatingService, "请先输入或粘贴至少 10 个字的岗位 JD", Toast.LENGTH_SHORT).show()
                } else {
                    triggerAiGeneration(container, input)
                }
            }
        }

        btnLayout.addView(pasteBtn)
        btnLayout.addView(generateBtn)

        container.addView(tipText)
        container.addView(jdInput)
        container.addView(btnLayout)
    }

    private fun triggerAiGeneration(container: LinearLayout, jdText: String) {
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val loadingText = TextView(this).apply {
            text = "正在深度匹配您的简历与岗位 JD...\n（调用 DeepSeek 极速生成中）"
            setTextColor(Color.DKGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
        }
        container.addView(progressBar)
        container.addView(loadingText)

        serviceScope.launch {
            val result = DeepSeekClient.generateGreetings(this@FloatingService, jdText)
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                result.onSuccess { options ->
                    options.forEach { option ->
                        val card = createOptionCard(option)
                        container.addView(card)
                    }
                }.onFailure { err ->
                    val errorView = TextView(this@FloatingService).apply {
                        text = "生成失败：${err.localizedMessage}\n\n请检查主界面中的 API Key、模型名称或网络连接。"
                        setTextColor(Color.RED)
                        textSize = 14f
                    }
                    val retryBtn = Button(this@FloatingService).apply {
                        text = "重试 / 手动输入"
                        setOnClickListener { showManualJdInput(container) }
                    }
                    container.addView(errorView)
                    container.addView(retryBtn)
                }
            }
        }
    }

    private fun createOptionCard(option: GreetingOption): View {
        val density = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8F9FA"))
                cornerRadius = 14 * density
                setStroke(2, Color.parseColor("#E9ECEF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
        }

        val tagView = TextView(this).apply {
            text = "【${option.title}】"
            setTextColor(Color.parseColor("#1677FF"))
            paint.isFakeBoldText = true
            textSize = 14f
        }

        val bodyView = TextView(this).apply {
            text = option.text
            setTextColor(Color.parseColor("#333333"))
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, (12 * density).toInt())
        }

        val copyBtn = Button(this).apply {
            text = "复制此话术并关闭"
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1677FF"))
                cornerRadius = 10 * density
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Greeting", option.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@FloatingService, "已复制到剪贴板，快去聊天框粘贴发送吧！", Toast.LENGTH_SHORT).show()
                dismissDialog()
            }
        }

        card.addView(tagView)
        card.addView(bodyView)
        card.addView(copyBtn)
        return card
    }

    private fun dismissDialog() {
        dialogOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            dialogOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        floatingBallView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            floatingBallView = null
        }
        dismissDialog()
    }
}
