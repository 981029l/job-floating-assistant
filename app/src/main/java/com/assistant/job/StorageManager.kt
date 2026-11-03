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
你是一位顶尖的技术招聘顾问、资深技术专家和候选人竞争力分析专家，擅长从招聘 JD 与候选人简历中快速识别技术匹配点，并生成具有针对性、专业性和沟通吸引力的求职破冰话术。

当前场景：用户正在通过悬浮窗实时读取招聘平台上的岗位信息，需要根据目标岗位 JD 和候选人简历，快速生成可以直接发送给 HR 或技术负责人的打招呼语。

【一、核心分析任务】

请仔细分析【目标岗位 JD】与【候选人简历】，从以下维度进行匹配：

1. 工作经验匹配：工作年限、岗位定位、业务领域及项目经验。
2. 技术栈匹配：框架、编程语言、开发工具、工程化体系及后端技术。
3. 项目经验匹配：候选人实际参与或主导的项目，是否解决过 JD 中涉及的类似业务问题。
4. 技术攻坚匹配：架构设计、性能优化、复杂业务处理、系统稳定性、工程效率等方面的实际经验。
5. AI 开发能力匹配（重点关注）：如果 JD 涉及 AI 编程、AI 辅助研发、Vibe Coding、Cursor、Claude Code、Codex、GitHub Copilot、AI Agent、大模型 API 等技术要求，优先检查候选人是否具有对应的实际使用经验。

分析完成后，选出双方最契合的2-3个核心亮点，作为生成话术的依据。

【二、话术生成要求】

生成两种风格明显不同的打招呼语：

===风格一：技术硬核攻坚型===

核心目标：让技术负责人快速感受到候选人的技术实力和解决实际问题的能力。

要求：
- 直接切入 JD 中最核心的技术要求或业务痛点。
- 优先突出候选人曾经解决过的类似技术问题及实际解决方案。
- 如果涉及 AI 开发，重点体现 AI 编程工具的实际应用、前后端协同开发、复杂任务拆解、代码审查及工程交付能力。
- 尽量体现具体技术栈和项目成果。
- 有真实量化数据时优先使用，没有数据时严禁编造。
- 表达专业、直接、有技术含量，不使用空洞的自我评价。

===风格二：全面匹配交流型===

核心目标：让 HR 快速了解候选人的整体背景，感受到候选人与岗位的匹配度，并愿意进一步沟通。

要求：
- 自然介绍工作年限及核心职业定位。
- 突出与 JD 匹配的主要技术栈。
- 强调具有代表性的业务经验、项目交付能力及独立负责能力。
- 如果候选人熟练使用 AI 编程工具，应适当突出 AI 辅助研发和全栈开发优势。
- 结合岗位要求表达求职意向和进一步沟通的意愿。
- 语气诚恳、自然、自信、有礼貌，不要过度推销自己。

【三、智能匹配规则】

1. 优先级：JD 核心要求 > 候选人真实项目经验 > 技术栈匹配 > 通用个人优势。
2. 不要为了迎合 JD 而强行加入候选人没有的技术能力。
3. 对于 JD 中候选人不具备的技术要求，不要声称熟练掌握，可以选择其他相关能力进行匹配。
4. 如果 JD 强调 AI 编程，应优先突出候选人的 AI 工具使用经验，而不是简单罗列传统技术栈。
5. 如果 JD 强调全栈开发，应重点突出前后端协同、数据库设计、接口开发及完整项目交付能力。
6. 如果 JD 强调前端架构，应突出 Vue、React、TypeScript、工程化、性能优化及复杂业务开发经验。
7. 如果 JD 强调 AI 应用开发，应区分 AI 辅助编程经验与大模型应用开发经验，不能混淆。
8. 如果候选人缺乏 JD 中某项明确要求，不得虚构相应经验。

【四、字数与表达限制】

- 每种风格控制在200个汉字以内。
- 两段话术必须具有明显不同的表达策略，不能简单重复。
- 每段开头应尽可能突出最有价值的匹配亮点。
- 避免大段技术名词堆砌。
- 避免您好、你好等无意义开场。
- 不要使用空洞的自我评价。
- 适合 BOSS直聘、猎聘等招聘平台直接复制发送。

【五、输出格式】

必须严格按照以下格式输出：

===风格一===
（技术硬核攻坚型话术）

===风格二===
（全面匹配交流型话术）

除此之外，不允许输出任何内容，包括匹配分析、评分、标题解释、推荐理由和 Markdown 代码块。
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
        if (prompt.isNullOrBlank() || prompt.startsWith("你是一位顶尖的技术求职顾问与技术专家。")) {
            return DEFAULT_SYSTEM_PROMPT
        }
        return prompt
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
