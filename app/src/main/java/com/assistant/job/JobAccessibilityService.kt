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

        data class JobMemoryItem(
            val title: String,
            val company: String,
            val fullJd: String,
            val timestamp: Long = System.currentTimeMillis()
        )

        val jobMemoryPool = mutableListOf<JobMemoryItem>()
        var lastMatchedJobTitle: String = ""

        fun recordJobToMemoryPool(lines: List<String>, fullText: String) {
            if (fullText.length < 20) return
            var title = ""
            var company = ""

            for (line in lines.take(15)) {
                val trimmed = line.trim()
                if (title.isBlank() && trimmed.length in 3..25) {
                    if (trimmed.contains("工程师") || trimmed.contains("开发") || trimmed.contains("前端") ||
                        trimmed.contains("后端") || trimmed.contains("架构") || trimmed.contains("设计") ||
                        trimmed.contains("产品") || trimmed.contains("算法") || trimmed.contains("测试") ||
                        trimmed.contains("Java") || trimmed.contains("Android") || trimmed.contains("iOS") ||
                        trimmed.contains("专家") || trimmed.contains("总监") || trimmed.contains("经理") ||
                        trimmed.contains("主管") || trimmed.contains("专员") || trimmed.contains("实习")
                    ) {
                        title = trimmed
                        continue
                    }
                }
                if (company.isBlank() && trimmed.length in 3..30) {
                    if (trimmed.contains("公司") || trimmed.contains("科技") || trimmed.contains("网络") ||
                        trimmed.contains("信息") || trimmed.contains("集团") || trimmed.contains("工作室") ||
                        trimmed.contains("互娱") || trimmed.contains("企业") || trimmed.contains("银行")
                    ) {
                        company = trimmed
                    }
                }
            }

            if (title.isBlank() && lines.isNotEmpty()) {
                title = lines.firstOrNull { it.length in 3..25 && !it.contains("立即沟通") && !it.contains("发简历") } ?: ""
            }

            if (title.isNotBlank()) {
                synchronized(jobMemoryPool) {
                    jobMemoryPool.removeAll { it.title == title || (it.company.isNotBlank() && it.company == company && it.title == title) }
                    jobMemoryPool.add(0, JobMemoryItem(title, company, fullText))
                    if (jobMemoryPool.size > 50) {
                        jobMemoryPool.removeAt(jobMemoryPool.size - 1)
                    }
                }
            }
        }

        fun findMatchingJdInChat(chatLines: List<String>): JobMemoryItem? {
            synchronized(jobMemoryPool) {
                if (jobMemoryPool.isEmpty()) return null
                for (item in jobMemoryPool) {
                    val titleMatch = item.title.isNotBlank() && chatLines.any { line ->
                        line.contains(item.title) || item.title.contains(line)
                    }
                    val companyMatch = item.company.isNotBlank() && chatLines.any { line ->
                        line.contains(item.company) || item.company.contains(line)
                    }
                    if (titleMatch || (companyMatch && chatLines.any { it.length in 3..25 && (it.contains("工程师") || it.contains("开发")) })) {
                        lastMatchedJobTitle = item.title
                        return item
                    }
                }
                val latest = jobMemoryPool.firstOrNull()
                if (latest != null && System.currentTimeMillis() - latest.timestamp < 10 * 60 * 1000) {
                    lastMatchedJobTitle = latest.title
                    return latest
                }
                lastMatchedJobTitle = ""
                return null
            }
        }


        data class SceneInspection(
            val mode: GenerationMode,
            val jdText: String,
            val chatHistory: String,
            val latestMessage: String = "",
            val screenText: String = "",
            val resumeStatus: String = "",
            val elapsedTime: String = ""
        )

        fun inspectCurrentScene(): SceneInspection {
            val service = instance
            if (service == null) {
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, cachedAppScreenText, "", "", cachedAppScreenText, "未发送", "")
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
                    } catch (e: Exception) {}
                }
            }

            val windows = try { service.windows } catch (e: Exception) { null }
            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        w.root?.let { scan(it) }
                    }
                }
            }
            if (allTextLines.isEmpty()) {
                val root = try { service.rootInActiveWindow } catch (e: Exception) { null }
                if (root != null) scan(root)
            }

            if (hasChatInput || hasReadStatus || hasUnreadOrDelivered) {
                val historyCandidates = allTextLines.filter { it.length >= 8 && !it.startsWith("预览") }
                val lastLine = historyCandidates.lastOrNull() ?: ""
                val isHrReplied = historyCandidates.size >= 2 && !lastLine.contains("已读") && !lastLine.contains("送达")

                val isTechEvaluation = lastLine.contains("转给技术") || lastLine.contains("技术人员") ||
                        lastLine.contains("部门负责人") || lastLine.contains("评估") ||
                        lastLine.contains("看过之后") || lastLine.contains("再通知") ||
                        lastLine.contains("转给部门") || lastLine.contains("给主管看")

                val mode = when {
                    isTechEvaluation -> GenerationMode.TECH_EVALUATION
                    isHrReplied -> GenerationMode.IN_DEPTH_CHAT
                    hasReadStatus -> GenerationMode.READ_UNREPLIED
                    hasUnreadOrDelivered -> GenerationMode.UNREAD_NUDGE
                    else -> GenerationMode.READ_UNREPLIED
                }

                val chatHistory = historyCandidates.takeLast(4).joinToString("\n")
                val latestMessage = if (isHrReplied) lastLine else (historyCandidates.lastOrNull() ?: "")

                val matchedJob = findMatchingJdInChat(allTextLines)
                val jdText = if (matchedJob != null && matchedJob.fullJd.length >= 20) {
                    matchedJob.fullJd
                } else if (lastExtractedJd.length >= 20) {
                    lastExtractedJd
                } else {
                    val candidateTitle = allTextLines.firstOrNull { line ->
                        line.length in 3..25 && (line.contains("前端") || line.contains("后端") || line.contains("工程师") ||
                                line.contains("开发") || line.contains("架构") || line.contains("设计") ||
                                line.contains("产品") || line.contains("算法") || line.contains("测试") ||
                                line.contains("Java") || line.contains("Android") || line.contains("iOS"))
                    } ?: allTextLines.firstOrNull { it.length in 3..20 && !it.contains("已读") && !it.contains("送达") } ?: "目标岗位"

                    val candidateSalary = allTextLines.firstOrNull { it.matches(Regex(".*\\d+[-~至]\\d+[kK万千].*")) } ?: ""

                    "【目标岗位】：$candidateTitle ${if (candidateSalary.isNotBlank()) "($candidateSalary)" else ""}\n（注：当前处于聊天沟通界面，未在屏幕发现长任职要求，已提取顶栏职位特征并针对对方最新消息与候选人简历智能应答）"
                }
                val fullScreen = allTextLines.joinToString("\n")
                val hasResumeMention = fullScreen.contains("简历") || fullScreen.contains("附件")
                val resumeStatus = if (hasResumeMention) "聊天中已出现简历发送或索要记录" else "暂未发送简历"
                return SceneInspection(mode, jdText, chatHistory, latestMessage, screenText = fullScreen, resumeStatus = resumeStatus, elapsedTime = "当天")
            } else {
                val jd = captureCurrentScreenText()
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, jd, "", "", screenText = jd, resumeStatus = "未发送", elapsedTime = "")
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
            } catch (e: Exception) {
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
                } catch (e: Exception) {
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
                recordJobToMemoryPool(accumulatedLines.toList(), fullAccumulated)
                return fullAccumulated
            }

            val singleSnapshot = currentLines.joinToString("\n").trim()
            if (singleSnapshot.length >= 20) {
                lastExtractedJd = singleSnapshot
                cachedAppScreenText = singleSnapshot
                recordJobToMemoryPool(currentLines, singleSnapshot)
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
                } catch (e: Exception) {}
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
            } catch (e: Exception) {
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
