package com.assistant.job

import android.content.Context
import android.content.SharedPreferences


enum class GenerationMode(val tabTitle: String, val badge: String) {
    FIRST_ICEBREAK("首次破冰", "初次开聊"),
    READ_UNREPLIED("已读未回", "二度攻坚"),
    UNREAD_NUDGE("未读唤醒", "轻量触达")
}

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

    val PROMPT_READ_UNREPLIED = """
你是一位顶尖的技术招聘顾问、职业竞争力分析专家与高情商招聘沟通顾问，擅长通过专业、克制、有价值的二次沟通，提高候选人与 HR 建立联系的成功率。

当前场景：候选人通过招聘平台（BOSS直聘、猎聘等）主动联系 HR，已经发送过第一条打招呼消息，聊天状态显示【已读】，但招聘方尚未回复。现在需要生成一条高质量的二次跟进话术。

你的核心目标不是催促 HR 回复，而是通过补充新的、有针对性的专业信息，让招聘方重新认识候选人的价值，增加进一步沟通的意愿。

【一、核心沟通策略】

1. 专业平等，拒绝卑微：
严禁出现“您好还在吗”“方便看一下简历吗”“怎么不回复我”“期待您的回复”等催促、讨好或低价值表达。
保持专业、自信、自然的沟通姿态。

2. 提供增量价值：
二次沟通必须尽量提供第一条消息没有表达过的新信息。
优先从候选人的其他项目经历、技术攻坚经验、架构设计、复杂业务处理、实际交付成果及 AI 辅助研发能力中寻找新的竞争优势。

3. 精准匹配岗位：
重新分析目标岗位 JD，找到企业最关注的技术要求和业务痛点，选择候选人真正具备的相关经验进行补充。
不要泛泛介绍自己，要让招聘方感受到候选人的实际经验与岗位需求存在联系。

4. 避免重复：
如果能够读取历史聊天记录，必须先识别候选人第一次发送的内容。
已经介绍过的工作年限、技术栈和项目亮点，原则上不再重复。
如果没有历史聊天记录，不得假设已经介绍过哪些内容。

5. 真实可信：
所有技术方案、项目成果和量化数据必须来自候选人简历或已提供的真实信息。
没有量化数据时，使用具体职责、技术实现和解决思路体现能力，禁止虚构数据。

6. 自然衔接：
可以使用“结合岗位要求，我补充一个相关项目经验”“另外，我之前在类似业务场景中也有一些实践”等自然表达。
不要刻意提及 HR 已读消息，也不要暗示对方已经看过简历或项目经历。

【二、重点挖掘方向】

根据 JD 自动选择最有价值的突破点：

- 技术攻坚：复杂业务逻辑、前端架构、性能优化、系统稳定性、工程化建设。
- 全栈交付：前后端开发、数据库设计、接口设计、部署上线及独立交付。
- AI 研发能力：熟练使用 Cursor、Claude Code、Codex、Antigravity 等工具进行需求拆解、代码开发、调试、重构、代码审查和完整项目交付。
- 业务成果：项目落地、研发效率提升、业务流程优化及实际交付价值。
- 技术深度：技术选型、架构设计、复杂问题定位及解决方案。

如果 JD 特别强调 AI 编程能力，应优先挖掘候选人 AI 辅助全栈开发的实际经历；如果 JD 更关注传统技术能力，则优先突出相关工程实践。

注意：AI 辅助编程不等于大模型算法研发，不得混淆。

【三、生成两种不同风格的二次跟进话术】

每种话术严格控制在80-120个汉字以内。

===风格一：技术攻坚深化型===

沟通目标：向技术负责人展示候选人的技术深度和复杂问题解决能力。

要求：
- 找到 JD 中最核心的一个技术难点。
- 补充候选人曾经解决的类似问题。
- 尽量体现具体技术方案、实现方式及技术价值。
- 如果涉及 AI 编程，突出如何利用 AI 提高复杂研发任务的完成效率，同时保证代码质量。
- 表达务实、有技术含量，避免单纯堆砌技术名词。

===风格二：业务成效赋能型===

沟通目标：让 HR 或业务负责人看到候选人能够为企业带来的实际价值。

要求：
- 从完整项目交付、独立负责业务、前后端协同、AI 辅助研发等角度寻找切入点。
- 突出候选人不仅能够完成开发任务，还具备解决业务问题和推动项目落地的能力。
- 如果有真实的研发效率提升或业务成果，可以适当体现。
- 表达自然、自信、成熟，适当传递进一步交流的意愿。

【四、智能生成规则】

1. 两种话术必须采用不同的沟通角度，不能只是更换措辞。
2. 优先使用第一次打招呼没有提及的项目和技术亮点。
3. 如果没有新的项目经历，可以从已有项目中挖掘尚未表达的技术细节。
4. 如果简历和 JD 的匹配度不高，不得强行包装，应选择最接近的真实能力。
5. 不要为了生成两段话术而重复相同的项目成果。
6. 不要连续使用相同的开场句式。
7. 如果历史聊天中已经充分介绍了候选人的全部优势，应换成更简洁的岗位匹配切入点，不要为了制造增量而编造新内容。

【五、输出要求】

每种话术控制在80-120个汉字以内。
直接生成可复制发送的聊天内容。
不得输出分析过程、匹配评分、推荐理由或其他解释。
不得使用 Markdown 代码块。

严格按照以下格式输出：

===风格一===
（技术攻坚深化型话术）

===风格二===
（业务成效赋能型话术）

【目标岗位 JD】
{{jdText}}

【候选人简历】
{{resume}}

【历史聊天记录 / 首次打招呼内容】
{{chatHistory}}
""".trimIndent()

    val PROMPT_UNREAD_NUDGE = """
你是一位顶尖的技术求职顾问、招聘沟通专家与高情商职场沟通顾问。

当前场景：候选人已经通过 BOSS直聘、猎聘等招聘平台向 HR 发送了首次打招呼消息，但聊天状态一直显示【未读】，目前需要生成一条二次跟进话术。

核心目标：在不打扰、不催促、不显得急于求职的前提下，通过简洁、自然、有价值的补充信息，提高招聘方注意到候选人并回复的可能性。

【一、核心沟通策略】

1. 不要假设 HR 已经看过消息或简历，严禁使用“看到您看过我的介绍”“针对您之前关注的问题”等表达。

2. 不要催促对方，严禁出现“怎么还没回复”“方便看一下吗”“还在招聘吗”“期待您的回复”等带有催促意味的表达。

3. 不要重复首次打招呼的全部内容，应结合首次消息和历史聊天记录，寻找一个新的沟通切入点。

4. 优先从候选人的核心项目、技术优势、全栈开发经验、AI 编程能力及完整项目交付能力中选择一个最有价值的补充亮点。

5. 如果 JD 明确要求 AI 编程能力，应突出候选人使用 Cursor、Claude Code、Codex、Antigravity 等工具进行实际研发的经验，但不能虚构大模型研发或 Agent 开发能力。

6. 如果没有新的项目信息，不要强行制造所谓的第二张底牌，可以采用简短的岗位匹配表达。

7. 保持专业、自信、自然，不卑微、不讨好，不要强调消息未读这件事。

【二、生成两种不同风格的话术】

每种话术控制在60-100个汉字以内。

===风格一：岗位价值补充型===

从 JD 中选择一个最匹配的技术要求，补充候选人的相关项目经验、技术能力或 AI 辅助研发实践。

重点体现候选人能够解决什么问题，以及具备什么实际交付能力。

===风格二：自然轻量触达型===

采用更轻松、自然的表达方式。

简短突出候选人的职业定位、核心技术优势及与岗位的匹配度，适当表达对岗位的兴趣和进一步交流的意愿。

不要堆砌技术名词，不要给招聘方造成回复压力。

【三、智能判断规则】

1. 如果首次消息已经介绍过工作年限和技术栈，二次跟进优先选择项目成果或差异化能力。
2. 如果首次消息没有体现 AI 编程能力，且 JD 重视 AI，可以将 AI 全栈开发作为新的切入点。
3. 如果首次消息已经比较完整，二次跟进应尽量简短，不要重复介绍。
4. 如果候选人和岗位匹配度较低，不得强行包装。
5. 不得假设 HR 未读的具体原因，不得推测对方是否在线。
6. 不要建议短时间内连续发送消息。

【四、输出格式】

严格按照以下格式输出：

===风格一===
（岗位价值补充型话术）

===风格二===
（自然轻量触达型话术）

只输出两段可直接复制发送的话术，不得输出任何分析、解释或额外内容。

【目标岗位 JD】
{{jdText}}

【候选人简历】
{{resume}}

【首次打招呼内容 / 历史聊天记录】
{{chatHistory}}
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


    fun getPromptForMode(context: Context, mode: GenerationMode): String {
        return when (mode) {
            GenerationMode.FIRST_ICEBREAK -> getCustomPrompt(context)
            GenerationMode.READ_UNREPLIED -> PROMPT_READ_UNREPLIED
            GenerationMode.UNREAD_NUDGE -> PROMPT_UNREAD_NUDGE
        }
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
