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
    const val DEFAULT_RESUME = ""

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
