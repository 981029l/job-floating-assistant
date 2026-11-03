package com.assistant.job

import android.content.Context
import android.content.SharedPreferences

object StorageManager {
    private const val PREF_NAME = "job_assistant_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model"
    private const val KEY_RESUME = "resume_content"
    private const val KEY_PROMPT = "custom_prompt"

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-chat"

    val DEFAULT_RESUME = """
吕宽友 | 7年前端研发经验 | 期望城市：深圳 | 期望薪资：15-20K
【核心优势】
1. 精通 Vue3 + TypeScript + Vite，熟练掌握 React、微信小程序原生开发与 Node.js 全栈。
2. 深度主导 AI 落地项目：精通 OpenAI API、SSE 流式打字机交互、语音合成（EdgeTTS/讯飞）、知识库检索与文档切片双向高亮溯源。
3. 架构与性能攻坚：具备 Web/APP/H5/小程序多端统一架构能力；主导过直播平台万级弹幕 Canvas/Web Workers 调度（峰值3000+条/秒稳定）、弱网保活重连、虚拟列表及首屏优化。
4. 全日制软件工程本科科班出身，工程化与业务闭环能力扎实。
""".trimIndent()

    val DEFAULT_SYSTEM_PROMPT = """
你是一位顶尖的技术求职顾问与技术专家。
请仔细分析【目标岗位JD】与【候选人简历】，找出双方最契合的 2-3 个核心亮点。

请生成 2 种不同风格的打招呼语（每种风格控制在 100-130 字以内）：
【风格一：技术硬核攻坚型】
强调针对该 JD 痛点的具体攻坚方案与量化指标，专业务实、直奔主题。
【风格二：全面匹配交流型】
突出年限、技术栈契合度与核心业务经验，诚恳礼貌，表达深入沟通意向。

输出格式必须严格如下：
===风格一===
(此处输出第一段话术)
===风格二===
(此处输出第二段话术)
""".trimIndent()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY, "") ?: ""
    }

    fun saveBaseUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBaseUrl(context: Context): String {
        return getPrefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveModel(context: Context, model: String) {
        getPrefs(context).edit().putString(KEY_MODEL, model).apply()
    }

    fun getModel(context: Context): String {
        return getPrefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun saveResume(context: Context, resume: String) {
        getPrefs(context).edit().putString(KEY_RESUME, resume).apply()
    }

    fun getResume(context: Context): String {
        val content = getPrefs(context).getString(KEY_RESUME, "")
        return if (content.isNullOrBlank()) DEFAULT_RESUME else content
    }

    fun saveCustomPrompt(context: Context, prompt: String) {
        getPrefs(context).edit().putString(KEY_PROMPT, prompt).apply()
    }

    fun getCustomPrompt(context: Context): String {
        val prompt = getPrefs(context).getString(KEY_PROMPT, "")
        return if (prompt.isNullOrBlank()) DEFAULT_SYSTEM_PROMPT else prompt
    }
}
