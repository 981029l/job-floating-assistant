package com.assistant.job

import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

enum class GreetingStage(val description: String) {
    MATCHING("1/2 正在逐项对照岗位要求与完整简历"),
    WRITING("2/2 正在依据匹配结果生成话术")
}

internal object GreetingPipeline {
    suspend fun generate(
        input: GreetingInput,
        complete: suspend (JSONObject) -> String,
        onStage: suspend (GreetingStage) -> Unit = {},
        diagnostics: GenerationDiagnostics? = null
    ): String {
        coroutineContext.ensureActive()
        val matchRequest = GreetingRequestBuilder.match(input)
        diagnostics?.stage(GreetingStage.MATCHING)
        onStage(GreetingStage.MATCHING)
        val rawMatch = complete(matchRequest)
        coroutineContext.ensureActive()
        diagnostics?.operation("校验岗位匹配结果")
        val match = JobMatch.parse(rawMatch, input)
        diagnostics?.operation("构造文案生成请求")
        val writeRequest = GreetingRequestBuilder.build(input, match)
        diagnostics?.stage(GreetingStage.WRITING)
        onStage(GreetingStage.WRITING)
        coroutineContext.ensureActive()
        val greeting = complete(writeRequest)
        coroutineContext.ensureActive()
        diagnostics?.operation("校验文案响应")
        require(greeting.isNotBlank()) { "模型未返回招呼语，请重试" }
        return greeting
    }
}
