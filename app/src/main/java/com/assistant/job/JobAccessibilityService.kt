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

        fun captureCurrentScreenText(): String {
            val service = instance ?: return cachedAppScreenText

            val sb = StringBuilder()

            // 1. 优先尝试从所有非系统应用窗口提取 (避免悬浮窗遮挡导致的焦点丢失)
            val windows = try {
                service.windows
            } catch (_: Exception) {
                null
            }

            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        w.root?.let { traverseNode(it, sb) }
                    }
                }
            }

            // 2. 如果窗口遍历没有得到足够文字，尝试当前活跃根节点
            if (sb.length < 20) {
                val root = try {
                    service.rootInActiveWindow
                } catch (_: Exception) {
                    null
                }
                if (root != null) {
                    traverseNode(root, sb)
                }
            }

            val extracted = sb.toString().trim()
            if (extracted.length >= 20) {
                lastExtractedJd = extracted
                cachedAppScreenText = extracted
                return extracted
            }

            // 3. 兜底返回最近一次自动监听缓存到的文字
            return if (cachedAppScreenText.isNotBlank()) cachedAppScreenText else extracted
        }

        private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder) {
            if (node == null) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length > 1) {
                // 忽略纯时间/电池电量
                if (!text.matches(Regex("^[0-9:%/\\s-]{1,8}$"))) {
                    sb.append(text).append("\n")
                }
            }
            for (i in 0 until node.childCount) {
                try {
                    traverseNode(node.getChild(i), sb)
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

        // 当用户在任何第三方应用中滑动、切换窗口时，主动捕获并缓存屏幕文本
        val pkg = event.packageName?.toString() ?: ""
        if (pkg != packageName) {
            val root = try {
                rootInActiveWindow
            } catch (_: Exception) {
                null
            }
            if (root != null) {
                val sb = StringBuilder()
                traverseNode(root, sb)
                val text = sb.toString().trim()
                if (text.length >= 20) {
                    cachedAppScreenText = text
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
