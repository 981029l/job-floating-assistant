package com.assistant.job

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class JobAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JobAccessibilityService? = null
            private set

        var cachedAppScreenText: String = ""
        var lastExtractedJd: String = ""

        // 方案 A：用户滑动自然累积增量缓冲区
        private val accumulatedLines = LinkedHashSet<String>()
        private var lastActivePackage: String = ""
        private var lastWindowClassName: String = ""
        private var isMergedLongJd: Boolean = false

        data class SceneInspection(
            val mode: GenerationMode,
            val jdText: String,
            val chatHistory: String
        )

        fun inspectCurrentScene(): SceneInspection {
            val service = instance
            if (service == null) {
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, cachedAppScreenText, "")
            }

            var hasChatInput = false
            var hasReadStatus = false
            var hasUnreadOrDelivered = false
            val allTextLines = mutableListOf<String>()

            fun scan(node: AccessibilityNodeInfo?) {
                if (node == null) return
                val cls = node.className?.toString() ?: ""
                if (cls.contains("EditText")) {
                    hasChatInput = true
                }

                val txt = node.text?.toString()?.trim() ?: ""
                if (txt.isNotBlank()) {
                    if (txt == "已读" || txt.contains("已读")) {
                        hasReadStatus = true
                    }
                    if (txt == "送达" || txt == "已送达" || txt == "未读" || txt.contains("送达")) {
                        hasUnreadOrDelivered = true
                    }
                    if (txt.length >= 6 && !IGNORED_FIXED_TEXTS.contains(txt)) {
                        allTextLines.add(txt)
                    }
                }

                for (i in 0 until node.childCount) {
                    try {
                        scan(node.getChild(i))
                    } catch (_: Exception) {}
                }
            }

            val windows = try { service.windows } catch (_: Exception) { null }
            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        w.root?.let { scan(it) }
                    }
                }
            }
            if (allTextLines.isEmpty()) {
                val root = try { service.rootInActiveWindow } catch (_: Exception) { null }
                if (root != null) scan(root)
            }

            if (hasChatInput || hasReadStatus || hasUnreadOrDelivered) {
                val mode = when {
                    hasReadStatus -> GenerationMode.READ_UNREPLIED
                    hasUnreadOrDelivered -> GenerationMode.UNREAD_NUDGE
                    else -> GenerationMode.READ_UNREPLIED
                }
                val historyCandidates = allTextLines.filter { it.length >= 12 && !it.startsWith("预览") }
                val chatHistory = historyCandidates.takeLast(2).joinToString("\n")
                val jdText = if (lastExtractedJd.length >= 20) lastExtractedJd else allTextLines.joinToString("\n").take(300)
                return SceneInspection(mode, jdText, chatHistory)
            } else {
                val jd = captureCurrentScreenText()
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, jd, "")
            }
        }

        fun isLongJdMerged(): Boolean = isMergedLongJd

        fun clearAccumulation() {
            accumulatedLines.clear()
            isMergedLongJd = false
        }

        fun isServiceRunning(): Boolean = instance != null

        fun isServiceEnabledInSystem(context: Context): Boolean {
            if (instance != null) return true
            val expectedServiceName = "${context.packageName}/${JobAccessibilityService::class.java.name}"
            val simpleServiceName = "${context.packageName}/.JobAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(expectedServiceName, ignoreCase = true) ||
                    component.equals(simpleServiceName, ignoreCase = true) ||
                    component.contains(context.packageName)
                ) {
                    return true
                }
            }
            return false
        }

        // 过滤常见固定底栏/干扰行
        private val IGNORED_FIXED_TEXTS = setOf(
            "立即沟通", "发简历", "打招呼", "聊一聊", "收藏", "分享", "举报", "投递",
            "点我聊聊", "在线", "今日活跃", "刚刚活跃", "查看全部", "返回", "更多"
        )

        fun captureCurrentScreenText(): String {
            val service = instance ?: return cachedAppScreenText

            val currentLines = mutableListOf<String>()

            // 1. 优先尝试从所有非系统应用窗口提取 (避免悬浮窗遮挡导致的焦点丢失)
            val windows = try {
                service.windows
            } catch (_: Exception) {
                null
            }

            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        w.root?.let { collectNodeLines(it, currentLines) }
                    }
                }
            }

            // 2. 如果窗口遍历没有得到足够文字，尝试当前活跃根节点
            if (currentLines.isEmpty()) {
                val root = try {
                    service.rootInActiveWindow
                } catch (_: Exception) {
                    null
                }
                if (root != null) {
                    collectNodeLines(root, currentLines)
                }
            }

            // 3. 将当前视口的行增量合并到 accumulatedLines
            val previousSize = accumulatedLines.size
            for (line in currentLines) {
                if (isValidJdLine(line)) {
                    accumulatedLines.add(line)
                }
            }

            if (accumulatedLines.size > previousSize && previousSize > 0) {
                isMergedLongJd = true
            }

            val fullAccumulated = accumulatedLines.joinToString("\n").trim()
            if (fullAccumulated.length >= 20) {
                lastExtractedJd = fullAccumulated
                cachedAppScreenText = fullAccumulated
                return fullAccumulated
            }

            val singleSnapshot = currentLines.joinToString("\n").trim()
            if (singleSnapshot.length >= 20) {
                lastExtractedJd = singleSnapshot
                cachedAppScreenText = singleSnapshot
                return singleSnapshot
            }

            // 4. 兜底返回最近一次自动监听缓存到的文字
            return if (cachedAppScreenText.isNotBlank()) cachedAppScreenText else fullAccumulated
        }

        private fun isValidJdLine(text: String): Boolean {
            if (text.length <= 1) return false
            if (text.matches(Regex("^[0-9:%/\\s-]{1,8}$"))) return false
            if (IGNORED_FIXED_TEXTS.contains(text)) return false
            return true
        }

        private fun collectNodeLines(node: AccessibilityNodeInfo?, list: MutableList<String>) {
            if (node == null) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && isValidJdLine(text)) {
                list.add(text)
            }
            for (i in 0 until node.childCount) {
                try {
                    collectNodeLines(node.getChild(i), list)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg.isBlank() || pkg == packageName) return

        // 1. 监测页面窗口切换（切换 Activity / 退出重进），重置累积缓冲区
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            if (pkg != lastActivePackage || (className.isNotBlank() && className != lastWindowClassName && !className.contains("Popup") && !className.contains("Dialog"))) {
                lastActivePackage = pkg
                lastWindowClassName = className
                clearAccumulation()
            }
        }

        // 2. 监测滑动事件与内容变化，增量累积新出现的屏幕文字
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val root = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }
            if (root != null) {
                val lines = mutableListOf<String>()
                collectNodeLines(root, lines)
                val initialCount = accumulatedLines.size
                for (line in lines) {
                    if (isValidJdLine(line)) {
                        accumulatedLines.add(line)
                    }
                }
                if (accumulatedLines.size > initialCount && initialCount > 0) {
                    isMergedLongJd = true
                }
                val merged = accumulatedLines.joinToString("\n").trim()
                if (merged.length >= 20) {
                    cachedAppScreenText = merged
                    lastExtractedJd = merged
                }
            }
        }
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
