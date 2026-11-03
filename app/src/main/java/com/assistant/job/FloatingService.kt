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
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
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
    private var currentGenerationJob: Job? = null

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
        val bubbleDp = StorageManager.getBubbleSize(this)
        val ballSize = (bubbleDp * density).toInt()

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
        val dialogHeight = (displayMetrics.heightPixels * 0.72).toInt()

        val params = WindowManager.LayoutParams(
            dialogWidth,
            dialogHeight,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
            setPadding((12 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0F5FF"))
                cornerRadius = 16 * density
            }
        }
        val titleView = TextView(this).apply {
            text = "AI 定制打招呼语"
            textSize = 14f
            setTextColor(Color.parseColor("#1E293B"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(this).apply {
            text = "关闭"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (32 * density).toInt())
            setOnClickListener { dismissDialog() }
        }
        headerLayout.addView(titleView)
        headerLayout.addView(closeBtn)
        rootLayout.addView(headerLayout)

        // -------------------------------------------------------------
        // 岗位 JD 识别与校对卡片 (支持查看已识别文字、手动编辑、剪贴板粘贴与重新生成)
        // -------------------------------------------------------------
        val jdCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F8FAFC"))
                cornerRadius = 14 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#E2E8F0"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * density).toInt()
                bottomMargin = (6 * density).toInt()
            }
        }

        // 1. 卡片顶栏（状态徽章 + 标题 + 展开/收起按钮）
        val jdHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val jdStatusBadge = TextView(this).apply {
            val count = initialJd.length
            if (count >= 20) {
                val isMerged = JobAccessibilityService.isLongJdMerged()
                text = if (isMerged) "已智能合并长JD · $count 字" else "已识别 · $count 字"
                setTextColor(Color.parseColor("#059669"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#ECFDF5"))
                    cornerRadius = 6 * density
                }
            } else {
                text = "未完全识别 · 需手动粘贴"
                setTextColor(Color.parseColor("#D97706"))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FFFBEB"))
                    cornerRadius = 6 * density
                }
            }
            textSize = 11f
            paint.isFakeBoldText = true
            setPadding((6 * density).toInt(), (2 * density).toInt(), (6 * density).toInt(), (2 * density).toInt())
        }

        val jdTitleView = TextView(this).apply {
            text = "岗位 JD 识别与校对"
            textSize = 12.5f
            setTextColor(Color.parseColor("#1E293B"))
            paint.isFakeBoldText = true
            setPadding((8 * density).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val toggleBtn = TextView(this).apply {
            text = if (initialJd.length >= 20) "校对/修改" else "收起"
            textSize = 11.5f
            setTextColor(Color.parseColor("#2563EB"))
            paint.isFakeBoldText = true
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EFF6FF"))
                cornerRadius = 6 * density
            }
        }

        jdHeaderRow.addView(jdStatusBadge)
        jdHeaderRow.addView(jdTitleView)
        jdHeaderRow.addView(toggleBtn)
        jdCard.addView(jdHeaderRow)

        // 2. 折叠状态下的单行预览摘要
        val previewSnippet = TextView(this).apply {
            text = if (initialJd.isNotBlank()) {
                "预览: " + initialJd.replace("\n", " ").trim()
            } else {
                "（暂无屏幕文本，点击右侧展开可直接粘贴岗位要求）"
            }
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, (6 * density).toInt(), 0, 0)
            visibility = if (initialJd.length >= 20) View.VISIBLE else View.GONE
        }
        jdCard.addView(previewSnippet)

        // 3. 展开后的完整编辑与控制区
        val jdEditSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
            visibility = if (initialJd.length >= 20) View.GONE else View.VISIBLE
        }

        val jdEditText = EditText(this).apply {
            setText(initialJd)
            hint = "若自动识别不准，可在此手动修改，或点击下方【粘贴剪贴板】..."
            textSize = 12f
            setTextColor(Color.parseColor("#1E293B"))
            minLines = 3
            maxLines = 6
            setLineSpacing(3f, 1.15f)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 10 * density
                setStroke((1 * density).toInt(), Color.parseColor("#CBD5E1"))
            }
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
        }

        // 操作按钮行（粘贴剪贴板、清空、按新JD重新生成）
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }

        val pasteBtn = Button(this).apply {
            text = "粘贴"
            textSize = 11.5f
            setTextColor(Color.parseColor("#2563EB"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EFF6FF"))
                cornerRadius = 8 * density
                setStroke((1 * density).toInt(), Color.parseColor("#BFDBFE"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 0.85f).apply {
                rightMargin = (5 * density).toInt()
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val clipText = clip.getItemAt(0).text?.toString() ?: ""
                    if (clipText.isNotBlank()) {
                        jdEditText.setText(clipText)
                        jdEditText.setSelection(clipText.length)
                        Toast.makeText(this@FloatingService, "已粘贴剪贴板内容（共 ${clipText.length} 字）", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@FloatingService, "剪贴板文本为空", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@FloatingService, "剪贴板无可用内容", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val appendBtn = Button(this).apply {
            text = "追加屏幕"
            textSize = 11.5f
            setTextColor(Color.parseColor("#059669"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#ECFDF5"))
                cornerRadius = 8 * density
                setStroke((1 * density).toInt(), Color.parseColor("#A7F3D0"))
            }
            layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply {
                rightMargin = (5 * density).toInt()
            }
            setOnClickListener {
                val latestJd = JobAccessibilityService.captureCurrentScreenText()
                if (latestJd.length >= 20) {
                    jdEditText.setText(latestJd)
                    jdEditText.setSelection(latestJd.length)
                    previewSnippet.text = "预览: " + latestJd.replace("\n", " ").trim()
                    val isMerged = JobAccessibilityService.isLongJdMerged()
                    jdStatusBadge.text = if (isMerged) "已智能合并长JD · ${latestJd.length} 字" else "已识别 · ${latestJd.length} 字"
                    Toast.makeText(this@FloatingService, "已刷新并追加屏幕文本（当前共 ${latestJd.length} 字）", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@FloatingService, "未捕获到新增屏幕内容，请稍作滑动后再试", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val clearBtn = Button(this).apply {
            text = "清空"
            textSize = 11.5f
            setTextColor(Color.parseColor("#64748B"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = 8 * density
            }
            layoutParams = LinearLayout.LayoutParams((44 * density).toInt(), (36 * density).toInt()).apply {
                rightMargin = (5 * density).toInt()
            }
            setOnClickListener {
                jdEditText.setText("")
            }
        }

        val regenerateBtn = Button(this).apply {
            text = "按此JD重新生成"
            textSize = 11.5f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = 8 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, (36 * density).toInt(), 1.35f)
        }

        actionRow.addView(pasteBtn)
        actionRow.addView(appendBtn)
        actionRow.addView(clearBtn)
        actionRow.addView(regenerateBtn)

        jdEditSection.addView(jdEditText)
        jdEditSection.addView(actionRow)
        jdCard.addView(jdEditSection)

        rootLayout.addView(jdCard)

        // 监听编辑框字符数变化，动态刷新徽章
        jdEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                if (len >= 20) {
                    jdStatusBadge.text = "已就绪 · $len 字"
                    jdStatusBadge.setTextColor(Color.parseColor("#059669"))
                    (jdStatusBadge.background as? GradientDrawable)?.setColor(Color.parseColor("#ECFDF5"))
                } else {
                    jdStatusBadge.text = "内容较短 · $len 字"
                    jdStatusBadge.setTextColor(Color.parseColor("#D97706"))
                    (jdStatusBadge.background as? GradientDrawable)?.setColor(Color.parseColor("#FFFBEB"))
                }
            }
        })

        // 折叠 / 展开交互
        val toggleAction = {
            val isExpanded = jdEditSection.visibility == View.VISIBLE
            if (isExpanded) {
                jdEditSection.visibility = View.GONE
                previewSnippet.visibility = View.VISIBLE
                val currentText = jdEditText.text.toString().trim()
                previewSnippet.text = if (currentText.isNotBlank()) {
                    "预览: " + currentText.replace("\n", " ")
                } else {
                    "（暂无岗位内容，点击展开可粘贴）"
                }
                toggleBtn.text = "校对/修改"
            } else {
                jdEditSection.visibility = View.VISIBLE
                previewSnippet.visibility = View.GONE
                toggleBtn.text = "收起"
                jdEditText.requestFocus()
            }
        }

        toggleBtn.setOnClickListener { toggleAction() }
        previewSnippet.setOnClickListener { toggleAction() }

        // 滚动内容容器（放置 AI 生成的话术卡片）
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }
        scrollView.addView(contentContainer)
        rootLayout.addView(scrollView)

        // 绑定重新生成按钮点击事件
        regenerateBtn.setOnClickListener {
            val customJd = jdEditText.text.toString().trim()
            if (customJd.length < 10) {
                Toast.makeText(this@FloatingService, "岗位 JD 至少需要 10 个字，请检查输入或点击粘贴", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 折叠编辑面板，给下方生成卡片留出最大可视空间
            jdEditSection.visibility = View.GONE
            previewSnippet.visibility = View.VISIBLE
            previewSnippet.text = "预览: " + customJd.replace("\n", " ")
            toggleBtn.text = "校对/修改"

            Toast.makeText(this@FloatingService, "已应用最新 JD，正在为您生成匹配话术...", Toast.LENGTH_SHORT).show()
            triggerAiGeneration(contentContainer, customJd)
        }

        // 底部提示栏
        val footerTip = TextView(this).apply {
            text = "提示：若岗位的任职要求较长，建议在BOSS上下滑动一次或长按复制后在此粘贴"
            textSize = 10.5f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(0, (6 * density).toInt(), 0, (2 * density).toInt())
        }
        rootLayout.addView(footerTip)

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
            // 如果初次识别没有达到 20 字，在下方展示友好引导
            contentContainer.removeAllViews()
            val promptTip = TextView(this).apply {
                text = "屏幕文字未完全识别\n请在上方卡片中检查并点击【粘贴剪贴板】，然后点击【按此JD重新生成】。"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, (24 * density).toInt(), 0, 0)
                setLineSpacing(4f, 1.2f)
            }
            contentContainer.addView(promptTip)
        }
    }

    private fun triggerAiGeneration(container: LinearLayout, jdText: String) {
        currentGenerationJob?.cancel()
        container.removeAllViews()
        val density = resources.displayMetrics.density

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val currentModel = StorageManager.getModel(this)
        val loadingText = TextView(this).apply {
            text = "正在深度解析岗位要求与简历匹配点...\n（调用 $currentModel 极速生成中）"
            setTextColor(Color.parseColor("#334155"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (16 * density).toInt(), 0, 0)
            setLineSpacing(3f, 1.15f)
        }
        container.addView(progressBar)
        container.addView(loadingText)

        currentGenerationJob = serviceScope.launch {
            val result = DeepSeekClient.generateGreetings(this@FloatingService, jdText)
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                result.onSuccess { options ->
                    options.forEach { option ->
                        val card = createOptionCard(option)
                        container.addView(card)
                    }
                }.onFailure { err ->
                    val errorCard = LinearLayout(this@FloatingService).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FEF2F2"))
                            cornerRadius = 12 * density
                            setStroke((1 * density).toInt(), Color.parseColor("#FECACA"))
                        }
                    }
                    val errorView = TextView(this@FloatingService).apply {
                        text = "生成遇到问题：${err.localizedMessage ?: "未知错误"}\n\n建议检查网络连接、API Key或在上方重新修改岗位JD。"
                        setTextColor(Color.parseColor("#DC2626"))
                        textSize = 13f
                        setLineSpacing(3f, 1.15f)
                    }
                    val retryBtn = Button(this@FloatingService).apply {
                        text = "重试当前 JD 生成"
                        textSize = 12f
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#DC2626"))
                            cornerRadius = 8 * density
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            (36 * density).toInt()
                        ).apply {
                            topMargin = (10 * density).toInt()
                        }
                        setOnClickListener {
                            triggerAiGeneration(container, jdText)
                        }
                    }
                    errorCard.addView(errorView)
                    errorCard.addView(retryBtn)
                    container.addView(errorCard)
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
                setColor(Color.parseColor("#F8FAFC"))
                cornerRadius = 14 * density
                setStroke((1.5f * density).toInt(), Color.parseColor("#E2E8F0"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * density).toInt()
            }
        }

        // 标签与右侧高回复率徽章
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val isHardcore = option.title.contains("硬核") || option.title.contains("技术")
        val tagBg = if (isHardcore) Color.parseColor("#EFF6FF") else Color.parseColor("#ECFDF5")
        val tagColor = if (isHardcore) Color.parseColor("#1677FF") else Color.parseColor("#059669")
        val badgeText = if (isHardcore) "匹配度 96%" else "高回复率"

        val tagView = TextView(this).apply {
            text = "【${option.title}】"
            setTextColor(tagColor)
            paint.isFakeBoldText = true
            textSize = 12f
            setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                setColor(tagBg)
                cornerRadius = 6 * density
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeView = TextView(this).apply {
            text = badgeText
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
        }

        topRow.addView(tagView)
        topRow.addView(badgeView)

        val bodyView = TextView(this).apply {
            text = option.text
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setLineSpacing(4f, 1.15f)
            setPadding(0, (10 * density).toInt(), 0, (14 * density).toInt())
        }

        val copyBtn = Button(this).apply {
            text = "复制此话术并关闭"
            setTextColor(Color.WHITE)
            textSize = 12f
            paint.isFakeBoldText = true
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

        card.addView(topRow)
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
