package com.assistant.job

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DeepSeekClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchModelList(context: Context): Result<List<String>> = withContext(Dispatchers.IO) {
        val result = fetchModelListWithLatency(context)
        result.map { it.first }
    }

    suspend fun fetchModelListWithLatency(context: Context): Result<Pair<List<String>, Long>> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先填写 API Key 再测试连通性"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/models"

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("接口响应错误 (${response.code}): $responseBody"))
            }

            val json = JSONObject(responseBody)
            val dataArray = json.optJSONArray("data")
            val models = mutableListOf<String>()

            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val id = item.optString("id")
                    if (id.isNotBlank()) {
                        models.add(id)
                    }
                }
            }

            if (models.isEmpty()) {
                models.addAll(listOf("deepseek-chat", "deepseek-reasoner", "qwen-turbo", "gpt-4o-mini"))
            }

            Result.success(Pair(models.sorted(), latency))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateAiSummary(context: Context, jdText: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在【AI设置】中配置并保存 API Key"))
            }

            val resume = StorageManager.getResume(context)
            if (resume.isBlank()) {
                return@withContext Result.failure(Exception("请先在上方录入或上传您的简历内容"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/chat/completions"
            val model = StorageManager.getModel(context)

            val prompt = """
你是一位资深技术招聘专家、职业竞争力分析顾问和招聘沟通文案专家。

请仔细阅读候选人的完整简历，提炼一段适合在 BOSS直聘、猎聘等招聘平台主动联系 HR 或技术负责人的【个人精炼优势 / 打招呼语料】。

你的核心目标是：让招聘方在短时间内识别候选人的技术实力、AI 开发能力、项目经验和岗位匹配度，从而产生进一步查看简历和沟通的兴趣。

【一、核心提炼方向】

1. 工作背景与职业定位：
突出候选人的实际工作年限、职业定位及核心工作方向，例如资深前端工程师、前端全栈工程师、AI 全栈工程师等。

2. 核心技术能力：
提炼与目标岗位最匹配的技术栈，例如 Vue3、React、TypeScript、Node.js、Java、Python、MySQL 等。避免无差别堆砌技术名词。

3. AI 编程能力（重点突出）：
如果候选人具有相关实际经验，应重点突出熟练使用 AI 编程工具的能力，例如 Cursor、Claude Code、Codex、Antigravity、GitHub Copilot 等。

不能仅仅罗列工具名称，而应突出实际应用能力，包括：
- 利用 AI 进行需求分析、技术方案设计和任务拆解。
- 利用 AI 完成前端、后端及数据库相关开发。
- 利用 AI 进行代码生成、重构、调试和问题排查。
- 通过上下文管理、Prompt 工程和任务规划提高研发效率。
- 对 AI 生成的代码进行审查、验证、优化，保障代码质量。
- 利用 AI 辅助完成从需求分析到开发、测试、部署上线的完整交付。

根据简历中的实际经历，优先突出候选人最有优势的 AI 开发实践，不要将仅仅使用 AI 工具描述为 AI 算法或大模型研发经验。

4. 全栈开发与项目交付：
如果候选人具备全栈开发经历，应重点体现其独立承担业务需求、前后端协同开发、数据库设计、接口开发、系统部署及项目交付的能力。

5. 项目成果与差异化优势：
优先提炼具有代表性的主导项目、技术攻坚、业务落地和研发效率提升成果。如果有真实量化数据，应适当体现；没有数据时不得虚构。

【二、表达策略】

- 使用第一人称，以候选人的口吻表达。
- 开头直接展示最有价值的竞争优势。
- AI 编程能力与传统工程能力相结合，体现候选人不仅有扎实的技术基础，也具备 AI 时代的研发效率优势。
- 不要写成传统简历中的个人优势，也不要写成正式求职信。
- 不使用您好、你好、HR您好等问候语。
- 不使用学习能力强、工作认真负责、抗压能力强等空洞描述。
- 不要过度包装，不要刻意堆砌 AI 热门技术名词。

【三、字数要求】

- 总字数控制在120-200个汉字以内，绝对不能超过200字。
- 前30-40字必须突出候选人最有竞争力的特征。
- 优先让招聘方看到工作年限、全栈定位、AI 编程能力及项目交付能力。
- 不必为了凑满200字而重复描述，信息不足时可以更短。
- 确保语句自然流畅，适合招聘平台直接发送。

【四、岗位匹配策略】

如果提供了招聘岗位 JD：
优先分析岗位的技术要求和核心职责，动态调整打招呼内容，突出候选人与该岗位最匹配的技术能力和项目经验。

如果没有提供招聘岗位 JD：
根据候选人的整体简历，选择最适合其职业定位的核心优势，不要假设招聘方的具体技术要求。

【五、真实性要求】

1. 所有内容必须来自候选人真实简历。
2. 不得虚构工作年限、项目成果、技术栈和量化数据。
3. 不得将 AI 辅助开发包装成模型训练或算法研发能力。
4. 不得夸大候选人在项目中的主导程度。
5. 优先体现实际开发经验和完整交付能力，而不是简单罗列工具。

【六、输出规范】

只输出一段完整的打招呼语料正文。

严禁输出标题、前缀、问候语、分析过程、解释文字、Markdown 标记或其他内容。

【候选人简历】
$resume

【目标岗位 JD（可选）】
${if (jdText.isNotBlank()) jdText else "无（请基于候选人整体简历提炼通用核心优势）"}
""".trimIndent()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.6)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("AI 提炼失败 (${response.code}): $responseBody"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("AI 模型未返回有效回复内容"))
            }

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateGreetings(
        context: Context,
        jdText: String,
        mode: GenerationMode = GenerationMode.FIRST_ICEBREAK,
        chatHistory: String = "",
        latestMessage: String = "",
        screenText: String = "",
        resumeStatus: String = "",
        elapsedTime: String = ""
    ): Result<List<GreetingOption>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先填写并保存 API Key"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/chat/completions"
            val model = StorageManager.getModel(context)
            val basePrompt = StorageManager.getPromptForMode(context, mode)
            val resume = StorageManager.getResume(context)

            val messagesArray = JSONArray().apply {
                if (basePrompt.contains("{{jdText}}")) {
                    val promptContent = basePrompt
                        .replace("{{jdText}}", jdText.ifBlank { "（暂未识别到详细岗位JD，请基于通用岗位匹配度生成）" })
                        .replace("{{resume}}", resume.ifBlank { "（未录入完整简历）" })
                        .replace("{{chatHistory}}", chatHistory.ifBlank { "（此前双方已有初步沟通记录）" })
                        .replace("{{latestMessage}}", latestMessage.ifBlank { "（对方已作出回复，请结合上下文推进面试转化）" })
                        .replace("{{screenText}}", screenText.ifBlank { "（当前屏幕聊天窗口内容）" })
                        .replace("{{resumeStatus}}", resumeStatus.ifBlank { "候选人已发送在线/附件简历" })
                        .replace("{{elapsedTime}}", elapsedTime.ifBlank { "数小时前或未标注时间" })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptContent)
                    })
                } else {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", basePrompt)
                    })
                    val userPromptContent = """
【核心准则】：你正在充当求职者全自动开聊的话筒。请严格执行【平视三段式推导流水线：先审判目标岗位真实业务 -> 再从简历按需提取对口经验】。
- 严禁字面摘抄应试要求！第一句平实接住核心业务形态（如：“您好，关注到咱们岗位主要负责[业务形态]……”），绝不使用“有很高标准”等虚伪奉承套话，绝不扣大帽子！
- 严禁反客为主！严禁向对方反问技术瓶颈、卡点或挑战（严禁出现“想听听你们现在的性能卡点/遇到了什么瓶颈”等盘问），第三句只平视表达业务契合与沟通意向！
- 严禁无中生有编造性能百分比！若无明确数据只陈述真实业务落地。目标 JD 未提及的领域（如未明确提及 AI/大模型），候选人即使有对应经历也必须绝对静默！

【目标岗位 JD（唯一事实源，以它为准）】：
${jdText.ifBlank { "（通用技术岗位要求）" }}

【候选人简历储备池（按需取药，禁止盲目强推与虚构）】：
${resume.ifBlank { "（未录入完整简历）" }}
""".trimIndent()
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPromptContent)
                    })
                }
            }

            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API 响应失败 (${response.code}): $responseBody"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("AI 模型未返回有效内容: $responseBody"))
            }

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            val options = parseOptions(content, mode, jdText)
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOptions(raw: String, mode: GenerationMode = GenerationMode.FIRST_ICEBREAK, jdText: String = ""): List<GreetingOption> {
        val list = mutableListOf<GreetingOption>()
        var cleaned = raw.trim().replace("\r\n", "\n")
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.replace(Regex("^```[a-zA-Z]*\\n?"), "").replace(Regex("\\n?```$"), "").trim()
        }

        if (mode == GenerationMode.RESUME_FOLLOW_UP) {
            var scene = ""
            var speech = ""
            var tip = ""

            val sceneMatch = Regex("【当前场景】[\\s:：]*([^\\n]+)").find(cleaned)
            if (sceneMatch != null) {
                scene = sceneMatch.groupValues[1].trim()
            }

            val speechMatch = Regex("【推荐话术】[\\s:：]*([\\s\\S]*?)(?=【沟通建议】|$)").find(cleaned)
            if (speechMatch != null) {
                speech = speechMatch.groupValues[1].trim()
            }

            val tipMatch = Regex("【沟通建议】[\\s:：]*([\\s\\S]*?)$").find(cleaned)
            if (tipMatch != null) {
                tip = tipMatch.groupValues[1].trim()
            }

            if (speech.isNotBlank()) {
                val cardTitle = if (scene.isNotBlank()) "投后跟进 · $scene" else "投后精准跟进"
                list.add(GreetingOption(cardTitle, speech, tip))
                return list
            }
        }
        if (mode == GenerationMode.TECH_EVALUATION) {
            val pNatural = Regex("【自然得体型】[\\s:：]*([\\s\\S]*?)(?=【岗位优势强化型】|$)")
            val pAdvantage = Regex("【岗位优势强化型】[\\s:：]*([\\s\\S]*?)(?=【推荐选择】|$)")
            val pRec = Regex("【推荐选择】[\\s:：]*([\\s\\S]*?)$")

            val mNatural = pNatural.find(cleaned)
            val mAdvantage = pAdvantage.find(cleaned)
            val mRec = pRec.find(cleaned)

            val naturalText = mNatural?.groupValues?.get(1)?.trim() ?: ""
            val advantageText = mAdvantage?.groupValues?.get(1)?.trim() ?: ""
            val recText = mRec?.groupValues?.get(1)?.trim() ?: ""

            if (naturalText.isNotBlank() || advantageText.isNotBlank()) {
                val recForNatural = if (recText.contains("自然得体") || !recText.contains("岗位优势")) recText else ""
                val recForAdvantage = if (recText.contains("岗位优势") || recText.contains("强化型")) recText else ""

                if (naturalText.isNotBlank()) {
                    list.add(GreetingOption("自然得体型", naturalText, if (recForNatural.isNotBlank()) "推荐理由：$recForNatural" else ""))
                }
                if (advantageText.isNotBlank()) {
                    list.add(GreetingOption("岗位优势强化型", advantageText, if (recForAdvantage.isNotBlank()) "推荐理由：$recForAdvantage" else ""))
                }
                return list
            }
        }
        val (title1, title2) = when (mode) {
            GenerationMode.FIRST_ICEBREAK -> Pair("业务契合型", "技术即战力型")
            GenerationMode.READ_UNREPLIED -> Pair("技术攻坚深化型", "业务成效赋能型")
            GenerationMode.UNREAD_NUDGE -> Pair("岗位价值补充型", "自然轻量触达型")
            GenerationMode.IN_DEPTH_CHAT -> Pair("专业答复型", "面试推进型")
            GenerationMode.RESUME_FOLLOW_UP -> Pair("投后跟进型", "面试促成型")
            GenerationMode.TECH_EVALUATION -> Pair("自然得体型", "岗位优势强化型")
        }

        // 1. 优先按完整多行标题进行正则切分（容错各种标记如 ===风格一===、### 风格一、[风格一] 等）
        val p1 = Regex("^[#*\\-=\\s【\\d.、]*?(?:风格一|方案一|风格 1|方案 1|第一种风格|第一种方案)[^\\n]*$", RegexOption.MULTILINE)
        val p2 = Regex("^[#*\\-=\\s【\\d.、]*?(?:风格二|方案二|风格 2|方案 2|第二种风格|第二种方案)[^\\n]*$", RegexOption.MULTILINE)

        val m1 = p1.find(cleaned)
        val m2 = p2.find(cleaned)

        if (m1 != null && m2 != null && m1.range.first < m2.range.first) {
            val part1 = cleaned.substring(m1.range.last + 1, m2.range.first).trim()
            val part2 = cleaned.substring(m2.range.last + 1).trim()
            if (part1.isNotBlank()) list.add(GreetingOption(title1, part1))
            if (part2.isNotBlank()) list.add(GreetingOption(title2, part2))
            return list
        }

        // 2. 次级单行行内切分
        val p1Inline = Regex("(?:===|【|###)?\\s*(?:风格一|方案一)[^=\\n】]*?(?:===|】|\\n)", RegexOption.IGNORE_CASE)
        val p2Inline = Regex("(?:===|【|###)?\\s*(?:风格二|方案二)[^=\\n】]*?(?:===|】|\\n)", RegexOption.IGNORE_CASE)
        val im1 = p1Inline.find(cleaned)
        val im2 = p2Inline.find(cleaned)

        if (im1 != null && im2 != null && im1.range.first < im2.range.first) {
            val part1 = cleaned.substring(im1.range.last + 1, im2.range.first).trim()
            val part2 = cleaned.substring(im2.range.last + 1).trim()
            if (part1.isNotBlank()) list.add(GreetingOption(title1, part1))
            if (part2.isNotBlank()) list.add(GreetingOption(title2, part2))
            return list
        }

        // 3. 兜底策略：若大模型以双换行分段输出了两段具有一定长度的候选内容
        val paragraphs = cleaned.split("\\n\\n").map { it.trim() }.filter { it.length > 25 }
        if (paragraphs.size >= 2) {
            list.add(GreetingOption(title1, paragraphs[0]))
            list.add(GreetingOption(title2, paragraphs[1]))
            return list
        }

        val fallbackTitle = if (mode == GenerationMode.IN_DEPTH_CHAT) "面试转化回复" else "精选破冰语"
        list.add(GreetingOption(fallbackTitle, cleaned))
        return list.map { it.copy(text = sanitizeGreetingText(it.text, mode, jdText)) }
    }

    private fun sanitizeGreetingText(text: String, mode: GenerationMode, jdText: String = ""): String {
        var result = text.trim()
        if (result.isBlank()) return result

        if (result.startsWith("**") && result.endsWith("**") && result.length > 4) {
            result = result.substring(2, result.length - 2).trim()
        }

        // 彻底根除公文腔、生硬书面化字眼
        result = result
            .replace("贵司高级", "看到高级")
            .replace("贵司", "看到")
            .replace("贵公司", "看到")
            .replace("尊敬的招聘负责人", "您好")
            .replace("尊敬的HR", "您好")
            .replace("尊敬的面试官", "您好")
            .replace("看到看到", "看到")

        // 物理防护线：若目标 JD 完全没有提及 AI/大模型相关内容，而生成话术第一句发生幻觉
        val jdHasAi = jdText.contains("AI", ignoreCase = true) || 
                      jdText.contains("大模型") || 
                      jdText.contains("人工智能") || 
                      jdText.contains("智能体") || 
                      jdText.contains("Agent", ignoreCase = true)

        if (!jdHasAi && mode == GenerationMode.FIRST_ICEBREAK) {
            result = result
                .replace("多端产品与 AI 能力的前端落地", "多端产品与交互架构的前端落地")
                .replace("多端产品与 AI 能力的落地", "多端产品与交互架构的落地")
                .replace("多端产品与 AI 能力", "多端产品与交互架构")
                .replace("AI 能力的前端落地", "核心业务的前端落地")
                .replace("AI 能力的落地", "核心业务的落地")
                .replace("AI 业务的前端落地", "核心业务的前端落地")
                .replace("做 AI 相关", "做核心业务")
        }

        // 物理防护线 2：拦截咨询顾问式反问卡点、盘问瓶颈与虚伪奉承套话
        if (mode == GenerationMode.FIRST_ICEBREAK) {
            result = result
                .replace(Regex("(?:方便的话)?想听听你们现在的(?:性能)?卡点[。！？?]*"), "期待能和您具体交流。")
                .replace(Regex("(?:不知)?(?:目前|现在)?遇到(?:了)?什么(?:技术)?(?:卡点|瓶颈|挑战)[。！？?]*"), "希望能和您进一步沟通。")
                .replace(Regex("想了解(?:下)?你们(?:目前)?的(?:性能)?卡点[。！？?]*"), "期待和您深入沟通。")
                .replace("在数据密集的视图渲染，并且在交互流畅、易维护易扩展上有很高的标准", "主要负责后台系统与多端交互落地")
                .replace("并且在交互流畅、易维护易扩展上有很高的标准", "同时注重交互体验与规范交付")
                .replace("有很高的标准", "有清晰明确的要求")
        }

        // 首次破冰若首句缺失礼貌尊称，自动补全「您好，」
        if (mode == GenerationMode.FIRST_ICEBREAK) {
            val hasGreeting = result.startsWith("您好") || result.startsWith("你好")
            if (!hasGreeting) {
                result = "您好，$result"
            }
        }

        return result
    }
    suspend fun extractStructuredResume(context: Context, rawText: String): Result<StructuredResume> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在【AI设置】中配置并保存 API Key"))
            }

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("简历正文内容为空，无法提取"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/chat/completions"
            val model = StorageManager.getModel(context)

            val prompt = """
你是一位顶尖的资深技术招聘专家、简历优化顾问和简历结构化分析专家，擅长识别技术人才的核心竞争力，并将普通简历内容提炼成具有招聘吸引力的专业简历信息。

请仔细阅读用户提供的原始简历正文，全面分析候选人的工作背景、项目经历、技术能力、职业定位及个人优势，并将核心信息提炼为规范的 JSON 格式，供后续 AI 简历生成系统使用。

## 一、核心分析原则

1. 真实性原则：所有提取和优化的内容必须以原始简历为依据，不得虚构公司名称、任职时间、项目经历、技术栈、业务成果、工作年限及量化数据。
2. 专业性原则：使用资深技术招聘专家认可的专业表达，突出技术深度、业务价值、架构能力和实际贡献。
3. 价值导向原则：不要简单复制原始简历，而是识别候选人真正有竞争力的内容，进行专业化重组和优化。
4. 结构清晰原则：严格区分求职意向、项目经历和个人优势，不得混淆不同字段的内容。
5. 优先级原则：优先提取最有价值、最能体现候选人能力的经历，弱化重复、琐碎和缺乏技术含量的内容。
6. 技术定位原则：如果候选人具备前端、后端、数据库、部署运维及 AI 辅助研发等多方面能力，应综合判断其是否具备全栈工程师定位，不要仅根据简历中的职位名称判断。
7. AI 开发经历识别：如果候选人使用 Cursor、Claude、Codex、Antigravity 等 AI 编程工具参与研发，应准确识别其 AI 辅助开发能力，同时突出候选人自身的技术设计、架构决策、问题排查和工程交付能力，不得将 AI 生成代码等同于独立技术能力。

## 二、JSON 输出规范

必须直接输出且仅输出一个合法的 JSON 对象。

严禁：
- 输出 Markdown 代码块标记。
- 输出任何解释性文字或前后缀。
- 输出 JSON 以外的内容。
- 添加 JSON 结构之外的字段。
- 使用注释、尾随逗号或其他不合法 JSON 语法。

所有字段均使用字符串类型。
没有明确的信息时，根据字段要求使用合理的默认值，不得自行编造。

## 三、JSON 字段结构与提取要求

{
  "targetRole": "目标求职岗位。根据候选人的实际工作经历、核心项目、技术栈和职业能力，综合判断最合适的求职方向。例如：资深前端工程师、前端全栈工程师、AI 全栈工程师、全栈开发工程师等。优先选择与候选人实际能力匹配、具有市场竞争力的岗位定位，避免过于宽泛或脱离实际。",

  "targetSalaryCity": "期望薪资与目标城市。优先从原始简历中提取明确的期望薪资和求职城市，格式为：25k-35k · 北京。如果原始简历没有提供，则填写：薪资面议 · 目标城市待定。不得根据工作年限或技术水平自行推测薪资和城市。",

  "coreExperience": "核心项目经历。提取候选人最具代表性的2-3个项目，按照项目重要性及时间倒序排列。每个项目必须重点提取以下内容：1. 项目名称及项目背景：说明项目解决什么业务问题、面向什么用户或业务场景。2. 项目职责：明确候选人在项目中担任的角色、负责的业务模块及实际工作范围，区分独立负责、核心参与和协助开发。3. 技术架构：提取项目实际使用的核心技术栈、框架、基础设施及架构方案。4. 核心技术攻坚：重点提取候选人解决的技术难点，例如复杂业务流程设计、前后端架构设计、性能优化、系统稳定性、数据处理、接口设计、工程化建设、多端适配、权限体系及 AI 能力集成等。5. 解决方案与个人贡献：说明候选人针对技术问题采取了什么方案，以及具体负责了哪些工作。6. 项目成果：提取有依据的业务成果、性能提升、效率提升、交付成果或系统建设成果，优先使用原始简历中的真实量化数据。输出时采用项目名称、项目介绍、技术栈、个人职责与核心贡献等清晰结构。不要只罗列项目名称和技术栈，也不要将整个项目团队的成果全部归功于候选人。优先保留体现候选人技术深度、独立负责能力、架构能力及业务价值的内容。总字数建议控制在300-500字以内。如果原始简历只有1-2个有价值的项目，则按实际数量输出，不得为了满足数量要求而编造项目。",

  "aiSummary": "个人优势 / 个人简介。这是用于简历首页的个人核心竞争力介绍，不是招聘平台的打招呼话术。请结合候选人的工作年限、工作经历、项目经历和技术能力，重新组织并优化为一段具有招聘吸引力的专业介绍。字数控制在100-150字。重点体现：1. 工作年限及职业定位；2. 核心技术能力和技术栈；3. 独立负责复杂业务、技术架构设计和解决技术难题的能力；4. 全栈开发、跨端开发及工程交付能力；5. AI 辅助研发经验及 AI 工具在实际项目中的应用能力（仅在原始简历有依据时体现）；6. 相比普通候选人的差异化竞争优势。要求语言简洁、自信、专业，突出实际能力和业务价值。不要简单罗列技术名词，不要重复项目经历，不要使用学习能力强、工作认真负责、性格开朗等空洞的自我评价，不要使用招聘打招呼式的开场白。"
}

## 四、特别质量控制要求

1. 项目经历必须来自原始简历，不能把工作职责直接包装成一个不存在的项目。
2. 对于候选人主导的项目，重点突出架构设计、技术选型、核心模块开发和项目交付。
3. 对于候选人参与的项目，准确描述其实际负责的部分，不得夸大主导程度。
4. 对于 AI 辅助开发项目，重点体现候选人如何利用 AI 提高研发效率，以及如何完成技术方案设计、代码实现、架构落地与工程交付。

【候选人原始简历正文】：
$rawText
""".trimIndent()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", 0.3)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("AI 解析接口响应失败 (${response.code}): $responseBody"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("AI 模型未返回有效内容"))
            }

            var content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            if (content.startsWith("```json")) {
                content = content.removePrefix("```json").trim()
            }
            if (content.startsWith("```")) {
                content = content.removePrefix("```").trim()
            }
            if (content.endsWith("```")) {
                content = content.removeSuffix("```").trim()
            }

            val jsonStart = content.indexOf("{")
            val jsonEnd = content.lastIndexOf("}")
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                content = content.substring(jsonStart, jsonEnd + 1)
            }

            val parsed = JSONObject(content)
            val targetRole = parsed.optString("targetRole", "")
            val targetSalaryCity = parsed.optString("targetSalaryCity", "")
            val coreExperience = parsed.optString("coreExperience", "")
            val aiSummary = parsed.optString("aiSummary", "")

            Result.success(
                StructuredResume(
                    targetRole = targetRole,
                    targetSalaryCity = targetSalaryCity,
                    coreExperience = coreExperience,
                    aiSummary = aiSummary
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private suspend fun callDeepSeek(
        context: Context,
        systemPrompt: String,
        userContent: String,
        temperature: Double = 0.7
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在设置中配置 API Key"))
            }

            val endpoint = StorageManager.getBaseUrl(context).trimEnd('/') + "/chat/completions"
            val model = StorageManager.getModel(context)

            val messagesArray = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("temperature", temperature)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API 响应失败 (${response.code}): $responseBody"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("AI 响应未包含有效内容: $responseBody"))
            }

            val content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateInterviewQuestions(
        context: Context,
        jdText: String,
        resumeText: String = ""
    ): Result<List<InterviewQuestion>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在设置中配置 API Key"))
            }

            val effectiveResume = if (resumeText.isNotBlank()) {
                resumeText
            } else {
                val fullResume = StorageManager.getResume(context)
                val summary = StorageManager.getAiSummary(context)
                if (fullResume.isNotBlank()) fullResume else summary
            }

            val systemPrompt = """你是一位顶尖的大厂技术专家、技术合伙人与资深技术面试官。
请严格遵循【先从目标岗位 JD 提炼业务团队眼下真实的痛点与难点 -> 再结合候选人经历预测连环追问】。若 JD 明确未涉及 AI、大模型等领域，绝对严禁押不相干的偏门考题！
请站在面试官的角度，生成 4 到 6 道真实面试中最容易被连环追问的高价值考题。

【考核维度必须涵盖】：
1. 技术深水区追问：针对 JD 最硬核的技术要求，考察技术底层原理、性能优化或技术攻坚细节；
2. 项目真实性深挖：结合候选人项目经历中可能提到的成果，深挖指标衡量依据、技术方案选型与踩坑解决；
3. 系统架构与场景设计：结合该岗位日常业务场景（如高并发、复杂渲染、工程化治理）的现场设计题；
4. 综合素质与避坑沟通：针对离职动机、业务协作冲突或压力应对的考察。

【输出规范】：
请严格按以下标准结构输出每道题目，每道题之间使用 ===题目分割=== 隔开，便于程序精确解析：

===题目分割===
【考题分类】：技术深水区追问
【面试提问】：[具体的面试官原话问题]
【考察意图】：[面试官问这个问题的真实意图、考察的能力与暗藏的考核陷阱]
【回答思路】：[基于候选人真实经历给出的 STAR 法则高分回答逻辑与核心技术亮点]
【避坑指南】：[回答该问题绝对不能说的禁忌或踩坑点]"""

            val userContent = """【目标岗位 JD】：
${if (jdText.isNotBlank()) jdText else "通用资深技术开发岗位要求"}

【候选人真实简历/经历】：
${if (effectiveResume.isNotBlank()) effectiveResume else "暂无详细简历，请基于岗位最核心的技术要求提出深水区问题"}"""

            val rawResult = callDeepSeek(context, systemPrompt, userContent, 0.6)
            if (rawResult.isFailure) {
                return@withContext Result.failure(rawResult.exceptionOrNull() ?: Exception("调用失败"))
            }

            val rawText = rawResult.getOrNull() ?: ""
            val questions = parseInterviewQuestions(rawText)
            Result.success(questions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseInterviewQuestions(raw: String): List<InterviewQuestion> {
        val list = mutableListOf<InterviewQuestion>()
        var cleaned = raw.trim().replace("\r\n", "\n")
        if (cleaned.startsWith("```") && cleaned.endsWith("```")) {
            cleaned = cleaned.replace(Regex("^```[a-zA-Z]*\\n?"), "").replace(Regex("\\n?```$"), "").trim()
        }

        val blocks = cleaned.split(Regex("===+(?:题目分割|题目 \\d+|第\\d+题)===+")).map { it.trim() }.filter { it.isNotBlank() }

        for (block in blocks) {
            val catMatch = Regex("【考题分类】[：:]*([^\\n]+)").find(block)
            val qMatch = Regex("【面试提问】[：:]*([\\s\\S]*?)(?=【考察意图】|$)").find(block)
            val intentMatch = Regex("【考察意图】[：:]*([\\s\\S]*?)(?=【回答思路】|$)").find(block)
            val ansMatch = Regex("【回答思路】[：:]*([\\s\\S]*?)(?=【避坑指南】|$)").find(block)
            val pitMatch = Regex("【避坑指南】[：:]*([\\s\\S]*?)$").find(block)

            val category = catMatch?.groupValues?.get(1)?.trim() ?: "核心技术考核"
            val question = qMatch?.groupValues?.get(1)?.trim() ?: ""
            val intention = intentMatch?.groupValues?.get(1)?.trim() ?: ""
            val answer = ansMatch?.groupValues?.get(1)?.trim() ?: ""
            val pitfall = pitMatch?.groupValues?.get(1)?.trim() ?: ""

            if (question.isNotBlank()) {
                list.add(
                    InterviewQuestion(
                        category = category,
                        question = question,
                        intention = intention,
                        answerStrategy = answer,
                        pitfall = pitfall
                    )
                )
            }
        }

        // 兜底逻辑：若未能按标准分割提取出多道题，尝试基于标题段落切分
        if (list.isEmpty()) {
            val sections = cleaned.split(Regex("\\n(?=(?:[#*\\-=\\s]*\\d+[.、]|【[0-9一二三四五]】))")).filter { it.trim().length > 30 }
            for (sec in sections) {
                val lines = sec.trim().split("\n")
                val title = lines.firstOrNull()?.replace(Regex("^[#*\\-=\\s\\d.、【】]+"), "")?.trim() ?: "核心考题"
                val body = if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else sec.trim()
                list.add(
                    InterviewQuestion(
                        category = "综合深度考点",
                        question = title,
                        intention = "考察针对该技术方案的深度认知与实际解决问题的工程化落地能力。",
                        answerStrategy = body,
                        pitfall = "切忌脱离实际项目空谈理论，重点围绕自身主导的真实攻坚展开。"
                    )
                )
            }
        }

        return list
    }

}


data class StructuredResume(
    val targetRole: String = "",
    val targetSalaryCity: String = "",
    val coreExperience: String = "",
    val aiSummary: String = ""
)

data class GreetingOption(
    val title: String,
    val text: String,
    val tip: String = ""
)

data class InterviewQuestion(
    val category: String,
    val question: String,
    val intention: String,
    val answerStrategy: String,
    val pitfall: String = ""
)
