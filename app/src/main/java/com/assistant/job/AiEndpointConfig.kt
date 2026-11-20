package com.assistant.job

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal object AiEndpointConfig {
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai"
    const val GEMINI_DEFAULT_MODEL = "gemini-4-hydrogen"

    val presets = listOf(
        Triple("DeepSeek 官方", StorageManager.DEFAULT_BASE_URL, StorageManager.DEFAULT_MODEL),
        Triple("Gemini 官方", GEMINI_BASE_URL, GEMINI_DEFAULT_MODEL),
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        Triple("OneAPI 聚合转接", "https://api.oneapi.pro/v1", "claude-3-5-sonnet")
    )

    fun isGeminiOfficial(url: String): Boolean =
        url.trim().toHttpUrlOrNull()?.host == "generativelanguage.googleapis.com"

    fun baseUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        val url = trimmed.toHttpUrlOrNull() ?: return trimmed
        if (isGeminiOfficial(trimmed) && url.encodedPath.trimEnd('/') in
            listOf("", "/v1beta", "/v1beta/openai", "/v1beta/openai/chat/completions", "/v1beta/openai/models")) {
            return GEMINI_BASE_URL
        }
        return trimmed
    }

    fun model(value: String, baseUrl: String): String =
        if (isGeminiOfficial(baseUrl)) value.trim().removePrefix("models/") else value.trim()

    fun completionBody(body: JSONObject, endpoint: String): JSONObject = JSONObject(body.toString()).apply {
        put("stream", true)
        if (isGeminiOfficial(endpoint)) {
            put("model", model(getString("model"), endpoint))
            remove("temperature")
        }
    }

    fun models(json: JSONObject, baseUrl: String): List<String> {
        val data = json.optJSONArray("data")
        val models = (0 until (data?.length() ?: 0)).mapNotNull { index ->
            (data?.optJSONObject(index)?.opt("id") as? String)?.let { model(it, baseUrl) }?.takeIf { it.isNotBlank() }
        }.distinct().sorted()
        check(models.isNotEmpty()) { "接口未返回可用模型，请检查接口地址，或手动填写服务商提供的文本模型名称" }
        return models
    }
}
