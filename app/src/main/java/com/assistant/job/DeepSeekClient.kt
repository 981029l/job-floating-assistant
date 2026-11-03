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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun generateGreetings(
        context: Context,
        jdText: String
    ): Result<List<GreetingOption>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = StorageManager.getApiKey(context)
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("请先在主页设置中填写 DeepSeek API Key！"))
            }

            val baseUrl = StorageManager.getBaseUrl(context).trimEnd('/')
            val endpoint = "$baseUrl/chat/completions"
            val model = StorageManager.getModel(context)
            val systemPrompt = StorageManager.getCustomPrompt(context)
            val resume = StorageManager.getResume(context)

            val userContent = """
【目标岗位 JD 信息】：
$jdText

【候选人核心简历】：
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
                return@withContext Result.failure(Exception("API 请求失败 (${response.code}): $responseBody"))
            }

            val responseJson = JSONObject(responseBody)
            val choices = responseJson.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("大模型未返回有效回复：$responseBody"))
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
        if (raw.contains("===风格一===") && raw.contains("===风格二===")) {
            val part1 = raw.substringAfter("===风格一===").substringBefore("===风格二===").trim()
            val part2 = raw.substringAfter("===风格二===").trim()
            list.add(GreetingOption("技术硬核攻坚型", part1))
            list.add(GreetingOption("全面契合交流型", part2))
        } else {
            list.add(GreetingOption("精选定制招呼语", raw))
        }
        return list
    }
}

data class GreetingOption(
    val title: String,
    val text: String
)
