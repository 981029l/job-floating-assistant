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
            .setContentTitle("求职助手已就绪")
            .setContentText("悬浮球已显示，点击即可针对当前岗位生成打招呼语")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            150,
            150,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 500
        }

        val ballView = TextView(this).apply {
            text = "AI\n招呼"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1677FF")) // 科技蓝
                setStroke(4, Color.WHITE)
            }
            elevation = 16f
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
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(ballView, params)
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
        windowManager.addView(ballView, params)
    }

    private fun onFloatingBallClicked() {
        if (!JobAccessibilityService.isServiceRunning()) {
            Toast.makeText(this, "请先在主界面开启【无障碍读取权限】！", Toast.LENGTH_LONG).show()
            return
        }

        val jdText = JobAccessibilityService.captureCurrentScreenText()
        if (jdText.isBlank()) {
            Toast.makeText(this, "未在当前屏幕提取到有效文字，请在岗位详情页再试", Toast.LENGTH_SHORT).show()
            return
        }

        showGenerationDialog(jdText)
    }

    private fun showGenerationDialog(jdText: String) {
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
        val dialogWidth = (displayMetrics.widthPixels * 0.9).toInt()
        val dialogHeight = (displayMetrics.heightPixels * 0.65).toInt()

        val params = WindowManager.LayoutParams(
            dialogWidth,
            dialogHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // 根容器
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 32f
                setStroke(2, Color.parseColor("#E0E0E0"))
            }
            elevation = 24f
        }

        // 顶栏（标题 + 关闭按钮）
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(this).apply {
            text = "🤖 AI 针对当前 JD 定制招呼语"
            textSize = 16f
            setTextColor(Color.parseColor("#1F1F1F"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { dismissDialog() }
        }
        headerLayout.addView(titleView)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        // 中间滚动区域
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)

        // 初始加载状态
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val loadingText = TextView(this).apply {
            text = "正在深度匹配您的简历与当前岗位 JD...\n（调用 DeepSeek 极速生成中）"
            setTextColor(Color.DKGRAY)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        contentContainer.addView(progressBar)
        contentContainer.addView(loadingText)

        dialogOverlayView = rootLayout
        windowManager.addView(rootLayout, params)

        // 异步请求 DeepSeek API
        serviceScope.launch {
            val result = DeepSeekClient.generateGreetings(this@FloatingService, jdText)
            withContext(Dispatchers.Main) {
                contentContainer.removeAllViews()
                result.onSuccess { options ->
                    options.forEach { option ->
                        val card = createOptionCard(option)
                        contentContainer.addView(card)
                    }
                }.onFailure { err ->
                    val errorView = TextView(this@FloatingService).apply {
                        text = "生成失败：${err.localizedMessage}\n\n请检查设置中的 API Key 或网络连通性。"
                        setTextColor(Color.RED)
                        textSize = 14f
                    }
                    contentContainer.addView(errorView)
                }
            }
        }
    }

    private fun createOptionCard(option: GreetingOption): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8F9FA"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#E9ECEF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
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
            setPadding(0, 12, 0, 16)
        }

        val copyBtn = Button(this).apply {
            text = "📋 复制此话术并关闭"
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1677FF"))
                cornerRadius = 12f
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
