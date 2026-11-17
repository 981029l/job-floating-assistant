package com.assistant.job

import org.json.JSONException
import org.json.JSONObject
import java.io.InterruptedIOException
import java.net.URI
import java.net.SocketTimeoutException
import java.time.OffsetDateTime
import java.util.UUID

internal class GenerationFailure(message: String, val report: String, cause: Throwable) : Exception(message, cause)

internal class GenerationDiagnostics(
    private val input: GreetingInput,
    private val endpoint: String,
    private val appVersion: String,
    private val device: String,
    private val timeouts: String,
    private val apiKey: String,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val started = nanoTime()
    private var stageStarted = started
    private var requestStarted: Long? = null
    private var requestFinished: Long? = null
    private var stage = "准备输入"
    private var operation = "校验输入与配置"
    private var requests = 0
    private var requestBytes = 0
    private var httpStatus: Int? = null
    private var requestId = "未返回"
    private var contentType = "未返回"
    private var responseChars: Int? = null
    private var responseBytes = 0L
    private var firstDataAt: Long? = null
    private var lastDataAt: Long? = null
    private var streaming = false
    private var streamEvents = 0
    private var contentChars = 0L
    private var reasoningChars = 0L
    private var streamDone = false
    private var finishReason = "未返回"
    private var providerError = "无结构化错误信息"

    fun stage(value: GreetingStage) {
        stage = value.description
        stageStarted = nanoTime()
    }

    fun operation(value: String) { operation = value }

    fun request(bytes: Int) {
        operation = "请求接口 / 等待响应"
        requests++
        requestBytes = bytes
        requestStarted = nanoTime()
        requestFinished = null
        httpStatus = null
        requestId = "未返回"
        contentType = "未返回"
        responseChars = null
        responseBytes = 0
        firstDataAt = null
        lastDataAt = null
        streaming = false
        streamEvents = 0
        contentChars = 0
        reasoningChars = 0
        streamDone = false
        finishReason = "未返回"
        providerError = "无结构化错误信息"
    }

    fun headers(status: Int, id: String?, type: String?) {
        httpStatus = status
        requestId = id ?: "未返回"
        contentType = type ?: "未返回"
        operation = "读取响应正文"
    }

    fun response(body: String) {
        requestFinished = nanoTime()
        responseChars = body.length
        operation = "解析接口响应"
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return
        metadata(json)
    }

    fun received(bytes: Long) {
        val now = nanoTime()
        if (firstDataAt == null) firstDataAt = now
        lastDataAt = now
        responseBytes += bytes
    }

    fun streamStarted() {
        streaming = true
        operation = "读取流式响应"
    }

    fun streamEvent(json: JSONObject) {
        streamEvents++
        metadata(json)
        val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
        contentChars += (delta?.opt("content") as? String)?.length ?: 0
        reasoningChars += (delta?.opt("reasoning_content") as? String)?.length ?: 0
    }

    fun streamFinished() {
        streamDone = true
        requestFinished = nanoTime()
        operation = "校验流式响应"
    }

    private fun metadata(json: JSONObject) {
        (json.optJSONArray("choices")?.optJSONObject(0)?.opt("finish_reason") as? String)?.let { finishReason = it }
        val error = json.optJSONObject("error") ?: return
        providerError = listOf("code", "type", "message").mapNotNull { key ->
            val value = error.opt(key)
            if (value is String || value is Number) "$key=$value" else null
        }.joinToString("\n").ifBlank { "无结构化错误信息" }
    }

    private fun safe(text: String): String {
        var result = text
        if (apiKey.isNotBlank()) result = result.replace(apiKey, "[API_KEY已隐藏]")
        for (source in listOf(input.jdText, input.resume, input.prompt, input.chatHistory, input.latestMessage, input.screenText)) {
            if (source.isNotBlank()) result = result.replace(source, "[资料正文已隐藏]")
            source.lineSequence().map(String::trim).filter { it.length >= 8 }.forEach {
                result = result.replace(it, "[资料片段已隐藏]")
            }
        }
        result = result.replace(Regex("https?://[^\\s<>\"']+")) { match -> safeUrl(match.value) }
            .replace(Regex("(?i)Bearer\\s+[^\\s,;\"]+"), "Bearer [已隐藏]")
            .replace(Regex("(?i)(api[_-]?key|token|password|authorization)([\"'\\s:=]+)[^\\s,;\"'}]+"), "$1$2[已隐藏]")
            .replace(Regex("sk-[A-Za-z0-9_-]+"), "[密钥已隐藏]")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[邮箱已隐藏]")
            .replace(Regex("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)"), "[手机号已隐藏]")
        return result
    }

    private fun safeUrl(url: String): String = runCatching {
        val uri = URI(url)
        if (uri.host.isNullOrBlank()) "[接口地址无法解析]"
        else "${uri.scheme}://${uri.host}${if (uri.port >= 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}"
    }.getOrDefault("[接口地址无法解析]")

    fun failure(error: Throwable): GenerationFailure {
        val now = nanoTime()
        val causes = generateSequence(error) { it.cause }.take(6).toList()
        val timeout = causes.any { it is SocketTimeoutException || (it is InterruptedIOException && it.message.orEmpty().contains("timeout", true)) }
        val message = when {
            timeout -> "生成超时，可复制错误详情查看失败阶段与耗时"
            httpStatus != null && httpStatus !in 200..299 -> "接口返回 HTTP $httpStatus，可复制错误详情排查"
            error is JSONException -> "接口响应格式异常，可复制错误详情排查"
            else -> safe(error.message ?: error.javaClass.simpleName).take(240)
        }
        val report = buildString {
            appendLine("求职助手 · 生成错误报告")
            appendLine("报告ID：${UUID.randomUUID()}")
            appendLine("时间：${OffsetDateTime.now()}")
            appendLine("应用：$appVersion")
            appendLine("系统：$device")
            appendLine("场景：${input.mode.tabTitle}")
            appendLine("阶段：$stage")
            appendLine("操作：$operation")
            appendLine("总耗时：${(now - started) / 1_000_000} ms；阶段耗时：${(now - stageStarted) / 1_000_000} ms")
            appendLine("接口：${safeUrl(endpoint)}")
            appendLine("模型：${input.model}")
            appendLine("超时配置：$timeouts")
            appendLine("输入长度：JD=${input.jdText.length}；简历=${input.resume.length}；提示词=${input.prompt.length}；聊天=${input.chatHistory.length}")
            appendLine("接口调用次数：$requests；本次请求体：$requestBytes bytes")
            appendLine("本次请求耗时：${requestStarted?.let { ((requestFinished ?: now) - it) / 1_000_000 } ?: "未发起"} ms")
            appendLine("HTTP状态：${httpStatus ?: "未收到响应头"}")
            appendLine("请求ID：$requestId")
            appendLine("Content-Type：$contentType")
            appendLine("响应字符数：${responseChars ?: if (streaming) "见流式统计" else "未完整读取"}；finish_reason：$finishReason")
            appendLine("已收响应正文：$responseBytes bytes（解压后）；读取完成：${requestFinished != null}")
            appendLine("首批正文等待：${firstDataAt?.let { (it - (requestStarted ?: started)) / 1_000_000 } ?: "未收到"} ms；距最后收包：${lastDataAt?.let { ((requestFinished ?: now) - it) / 1_000_000 } ?: "未收到"} ms")
            if (streaming) appendLine("流式统计：事件=$streamEvents；回答=$contentChars 字符；思考=$reasoningChars 字符；收到[DONE]=$streamDone")
            appendLine("接口错误信息：\n${safe(providerError).take(2000)}")
            appendLine("异常链与调用栈：")
            causes.forEachIndexed { index, cause ->
                val detail = if (cause is JSONException) "JSON解析失败（原始内容不写入报告）" else safe(cause.message.orEmpty())
                appendLine("[$index] ${cause.javaClass.name}: ${detail.take(1000)}")
                cause.stackTrace.take(18).forEach { appendLine("  at $it") }
            }
            appendLine("报告不记录请求正文、完整JD/简历/聊天或模型原文；仅保留定位信息及脱敏后的接口错误摘要。")
        }
        return GenerationFailure(message, safe(report).take(24000), error)
    }
}
