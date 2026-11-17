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

        private val jobMemory = JobMemoryStore()
        private var lastJobIdentity = JobContext.Identity()
        var lastMatchedJobTitle: String = ""
            private set

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
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, "", "")
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
                    if (txt == "已读") {
                        hasReadStatus = true
                    }
                    if (txt == "送达" || txt == "已送达" || txt == "未读") {
                        hasUnreadOrDelivered = true
                    }
                    if (txt.length >= 2 && !IGNORED_FIXED_TEXTS.contains(txt)) {
                        allTextLines.add(txt)
                    }
                }

                for (i in 0 until node.childCount) {
                    try {
                        scan(node.getChild(i))
                    } catch (e: Exception) {}
                }
            }

            lastMatchedJobTitle = ""
            val root = applicationRoot(service)
            if (root == null) {
                clearAccumulation()
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, "", "")
            }
            scan(root)

            if (hasReadStatus || hasUnreadOrDelivered || hasChatInput) {
                clearAccumulation()
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

                val matchedJob = jobMemory.find(root.packageName?.toString() ?: "", allTextLines)
                lastMatchedJobTitle = matchedJob?.identity?.title ?: ""
                val jdText = matchedJob?.fullJd ?: ""
                val fullScreen = allTextLines.joinToString("\n")
                val hasResumeMention = fullScreen.contains("简历") || fullScreen.contains("附件")
                val resumeStatus = if (hasResumeMention) "聊天中已出现简历发送或索要记录" else "暂未发送简历"
                return SceneInspection(mode, jdText, chatHistory, latestMessage, screenText = fullScreen, resumeStatus = resumeStatus, elapsedTime = "")
            } else {
                val jd = captureJd(root)
                return SceneInspection(GenerationMode.FIRST_ICEBREAK, jd, "", "", screenText = jd, resumeStatus = "未发送", elapsedTime = "")
            }
        }

        fun isLongJdMerged(): Boolean = isMergedLongJd

        fun clearAccumulation() {
            accumulatedLines.clear()
            isMergedLongJd = false
            cachedAppScreenText = ""
            lastExtractedJd = ""
            lastMatchedJobTitle = ""
            lastJobIdentity = JobContext.Identity()
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

        fun captureCurrentScreenText(): String = inspectCurrentScene().jdText

        private fun applicationRoot(service: JobAccessibilityService): AccessibilityNodeInfo? {
            val windows = try { service.windows } catch (e: Exception) { null }
            val appWindows = windows.orEmpty().filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.isActive || it.isFocused }
            for (window in appWindows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: continue
                if (pkg != service.packageName && pkg != "com.android.systemui") return root
            }
            val root = try { service.rootInActiveWindow } catch (e: Exception) { null }
            return root?.takeIf { it.packageName != service.packageName && it.packageName != "com.android.systemui" }
        }

        private fun hasChatInput(node: AccessibilityNodeInfo): Boolean {
            if (node.className?.contains("EditText") == true) return true
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (hasChatInput(child)) return true
            }
            return false
        }

        private fun captureJd(root: AccessibilityNodeInfo, allowScrollContinuation: Boolean = false): String {
            val currentLines = mutableListOf<String>()
            collectNodeLines(root, currentLines)
            val pkg = root.packageName?.toString() ?: ""
            val identity = JobContext.identity(currentLines)
            if (pkg != lastActivePackage || JobContext.changed(lastJobIdentity, identity)) clearAccumulation()
            lastActivePackage = pkg

            val hasSection = JobContext.hasJdSection(currentLines)
            val isChat = currentLines.any { it in setOf("已读", "未读", "送达", "已送达") } || hasChatInput(root)
            val isContinuation = accumulatedLines.isNotEmpty() &&
                (allowScrollContinuation || currentLines.any { it.length >= 20 && it in accumulatedLines })
            if (isChat || (!hasSection && !isContinuation)) {
                clearAccumulation()
                return ""
            }

            lastJobIdentity = JobContext.Identity(
                identity.title.ifBlank { lastJobIdentity.title },
                identity.company.ifBlank { lastJobIdentity.company }
            )
            val previousSize = accumulatedLines.size
            accumulatedLines.addAll(JobContext.jdLines(currentLines))
            if (previousSize > 0 && accumulatedLines.size > previousSize) isMergedLongJd = true
            val fullJd = accumulatedLines.joinToString("\n").trim()
            if (fullJd.length < 20) return ""
            cachedAppScreenText = fullJd
            lastExtractedJd = fullJd
            jobMemory.record(pkg, accumulatedLines.toList(), fullJd)
            return fullJd
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

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val root = applicationRoot(this) ?: return
            if (root.packageName?.toString() != pkg) return
            captureJd(root, event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED)
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
