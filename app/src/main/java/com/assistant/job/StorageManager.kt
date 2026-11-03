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
    private const val KEY_BUBBLE_SIZE = "bubble_size_dp"
    private const val KEY_TARGET_ROLE = "target_role"
    private const val KEY_TARGET_SALARY_CITY = "target_salary_city"
    private const val KEY_EXP1 = "experience_1"
    private const val KEY_EXP2 = "experience_2"
    private const val KEY_AI_SUMMARY = "ai_summary"
    private const val KEY_CUSTOM_TAGS = "custom_tech_tags"

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-flash"
    const val DEFAULT_RESUME = ""
    const val DEFAULT_BUBBLE_SIZE = 56

    const val DEFAULT_TARGET_ROLE = ""
    const val DEFAULT_TARGET_SALARY_CITY = ""
    const val DEFAULT_EXP1 = ""
    const val DEFAULT_EXP2 = ""
    const val DEFAULT_AI_SUMMARY = ""
    const val DEFAULT_CUSTOM_TAGS = ""

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

    fun saveBubbleSize(context: Context, sizeDp: Int) {
        getPrefs(context).edit().putInt(KEY_BUBBLE_SIZE, sizeDp).apply()
    }

    fun getBubbleSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_BUBBLE_SIZE, DEFAULT_BUBBLE_SIZE)
    }

    fun saveTargetRole(context: Context, role: String) {
        getPrefs(context).edit().putString(KEY_TARGET_ROLE, role).apply()
    }

    fun getTargetRole(context: Context): String {
        return getPrefs(context).getString(KEY_TARGET_ROLE, DEFAULT_TARGET_ROLE) ?: DEFAULT_TARGET_ROLE
    }

    fun saveTargetSalaryCity(context: Context, salaryCity: String) {
        getPrefs(context).edit().putString(KEY_TARGET_SALARY_CITY, salaryCity).apply()
    }

    fun getTargetSalaryCity(context: Context): String {
        return getPrefs(context).getString(KEY_TARGET_SALARY_CITY, DEFAULT_TARGET_SALARY_CITY) ?: DEFAULT_TARGET_SALARY_CITY
    }

    fun saveExp1(context: Context, exp: String) {
        getPrefs(context).edit().putString(KEY_EXP1, exp).apply()
    }

    fun getExp1(context: Context): String {
        return getPrefs(context).getString(KEY_EXP1, DEFAULT_EXP1) ?: DEFAULT_EXP1
    }

    fun saveAiSummary(context: Context, summary: String) {
        getPrefs(context).edit().putString(KEY_AI_SUMMARY, summary).apply()
    }

    fun getAiSummary(context: Context): String {
        return getPrefs(context).getString(KEY_AI_SUMMARY, DEFAULT_AI_SUMMARY) ?: DEFAULT_AI_SUMMARY
    }

    fun saveCustomTags(context: Context, tags: List<String>) {
        getPrefs(context).edit().putString(KEY_CUSTOM_TAGS, tags.joinToString(",")).apply()
    }

    fun getCustomTags(context: Context): List<String> {
        val s = getPrefs(context).getString(KEY_CUSTOM_TAGS, DEFAULT_CUSTOM_TAGS) ?: DEFAULT_CUSTOM_TAGS
        return s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
