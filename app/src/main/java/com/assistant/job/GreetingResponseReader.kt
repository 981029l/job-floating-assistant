package com.assistant.job

import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.json.JSONObject

internal object GreetingResponseReader {
    fun read(response: Response, diagnostics: GenerationDiagnostics?): String {
        val body = checkNotNull(response.body) { "接口未返回响应正文" }
        val source = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, byteCount)
                if (count > 0) diagnostics?.received(count)
                return count
            }
        }.buffer()
        if (response.isSuccessful && body.contentType()?.let { it.type == "text" && it.subtype == "event-stream" } == true) {
            diagnostics?.streamStarted()
            return readStream(source, diagnostics)
        }
        val raw = source.readUtf8()
        diagnostics?.response(raw)
        check(response.isSuccessful) { "API 响应失败 (${response.code})，请检查接口配置或稍后重试" }
        val json = JSONObject(raw)
        check(!json.has("error")) { "接口返回生成错误，可复制错误详情排查" }
        val choice = json.getJSONArray("choices").getJSONObject(0)
        requireComplete(choice.optString("finish_reason"))
        return choice.getJSONObject("message").getString("content").trim().also {
            check(it.isNotBlank()) { "模型未返回有效回复内容" }
        }
    }

    private fun readStream(source: BufferedSource, diagnostics: GenerationDiagnostics?): String {
        val content = StringBuilder()
        val data = StringBuilder()
        var finishReason = ""
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (data.isEmpty()) continue
                val event = data.toString()
                data.setLength(0)
                if (event.trim() == "[DONE]") {
                    diagnostics?.streamFinished()
                    requireComplete(finishReason)
                    return content.toString().trim().also {
                        check(it.isNotBlank()) { "模型未返回有效回复内容" }
                    }
                }
                val json = JSONObject(event)
                diagnostics?.streamEvent(json)
                check(!json.has("error")) { "接口返回流式生成错误，可复制错误详情排查" }
                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                if (choice.optInt("index", 0) != 0) continue
                val delta = choice.optJSONObject("delta")
                (delta?.opt("content") as? String)?.let { content.append(it) }
                (choice.opt("finish_reason") as? String)?.let {
                    finishReason = it
                    requireComplete(it)
                }
            } else if (line == "data" || line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data").removePrefix(":").removePrefix(" "))
            }
        }
        error("流式响应中断，未收到完整结束标记，请重试")
    }

    private fun requireComplete(reason: String) {
        check(reason != "length") { "模型输出被截断，请重试或更换支持更长输出的模型" }
        check(reason == "stop") { "模型未正常完成回复，请重试或检查模型配置" }
    }
}
