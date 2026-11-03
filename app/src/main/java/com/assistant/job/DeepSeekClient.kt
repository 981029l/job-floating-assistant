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

    suspend fun generateAiSummary(context: Context): Result<String> = withContext(Dispatchers.IO) {
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
你是一位资深求职顾问与开场白提炼专家。
请严格依据以下候选人简历，提炼出一段适合在 BOSS直聘、猎聘等招聘平台向 HR/业务主管 打招呼时使用的【精炼优势】（100字左右）。
要求：
1. 语言干练、自信、有礼；
2. 突出工作年限、核心技术栈与主导项目成效；
3. 直接输出提炼好的语料正文，严禁包含任何前缀、问候废话或多余解释。

【候选人简历】：
$resume
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
        jdText: String
    ): Result<List<GreetingOption>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先填写并保存 API Key"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/chat/completions"
            val model = StorageManager.getModel(context)
            val systemPrompt = StorageManager.getCustomPrompt(context)
            val resume = StorageManager.getResume(context)

            val userContent = """
【目标岗位 JD 信息】：
$jdText

【候选人简历】：
$resume
""".trimIndent()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
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

            val options = parseOptions(content)
            Result.success(options)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOptions(raw: String): List<GreetingOption> {
        val list = mutableListOf<GreetingOption>()
        if (raw.contains("===方案一===") && raw.contains("===方案二===")) {
            val part1 = raw.substringAfter("===方案一===").substringBefore("===方案二===").trim()
            val part2 = raw.substringAfter("===方案二===").trim()
            list.add(GreetingOption("硬核技术型", part1))
            list.add(GreetingOption("全面契合型", part2))
        } else {
            list.add(GreetingOption("精选破冰语", raw))
        }
        return list
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
你是一位顶尖的资深技术招聘专家与简历结构化分析专家。
请仔细阅读以下用户的原始简历正文，将其核心关键信息提炼归纳为规范的 JSON 格式。

【输出规范】：
必须直接输出且仅输出一个合法的 JSON 对象。严禁包含任何 Markdown 代码块标记（如严禁包裹 ```json 或 ```），严禁添加任何前后缀或解释文字。

【JSON 字段结构与提取要求】：
{
  "targetRole": "提炼出的目标/期望求职岗位，如：资深前端工程师、全栈架构师",
  "targetSalaryCity": "期望薪资与目标城市，如：25k-35k · 北京。若简历中未明确写明，可综合经验写如：薪资面议 · 目标城市待定",
  "coreExperience": "精炼浓缩的2-3条核心主导项目与技术攻坚亮点，每条突出业务场景、技术难点与量化成果，总字数控制在150字以内",
  "aiSummary": "80-120字的打招呼黄金开场白语料，突出工作年限、核心优势与主导成果，语气自信专业诚恳，便于直接向招聘方发起首条打招呼"
}

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
}

data class StructuredResume(
    val targetRole: String = "",
    val targetSalaryCity: String = "",
    val coreExperience: String = "",
    val aiSummary: String = ""
)

data class GreetingOption(
    val title: String,
    val text: String
)
