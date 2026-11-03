package com.assistant.job

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JobAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JobAccessibilityService? = null
            private set

        var lastExtractedJd: String = ""

        fun isServiceRunning(): Boolean = instance != null

        fun captureCurrentScreenText(): String {
            val service = instance ?: return ""
            val root = service.rootInActiveWindow ?: return ""
            val sb = StringBuilder()
            traverseNode(root, sb)
            val fullText = sb.toString().trim()
            lastExtractedJd = fullText
            return fullText
        }

        private fun traverseNode(node: AccessibilityNodeInfo?, sb: StringBuilder) {
            if (node == null) return
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length > 1) {
                // 忽略纯数字时间、电池电量等无意义状态栏信息
                if (!text.matches(Regex("^[0-9:%/\\s-]{1,8}$"))) {
                    sb.append(text).append("\n")
                }
            }
            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i), sb)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 当窗口变化时，保持监听
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
