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
        onStage: suspend (GreetingStage) -> Unit = {}
    ): String {
        coroutineContext.ensureActive()
        val matchRequest = GreetingRequestBuilder.match(input)
        onStage(GreetingStage.MATCHING)
        val rawMatch = complete(matchRequest)
        coroutineContext.ensureActive()
        val match = JobMatch.parse(rawMatch, input)
        val writeRequest = GreetingRequestBuilder.build(input, match)
        onStage(GreetingStage.WRITING)
        coroutineContext.ensureActive()
        val greeting = complete(writeRequest)
        coroutineContext.ensureActive()
        require(greeting.isNotBlank()) { "模型未返回招呼语，请重试" }
        return greeting
    }
}
