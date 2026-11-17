package com.assistant.job

import org.json.JSONArray
import org.json.JSONObject

internal data class GreetingInput(
    val model: String,
    val prompt: String,
    val mode: GenerationMode,
    val jdText: String,
    val resume: String,
    val chatHistory: String = "",
    val latestMessage: String = "",
    val screenText: String = "",
    val resumeStatus: String = "",
    val elapsedTime: String = ""
)

internal object GreetingRequestBuilder {
    private val templateFields = mapOf(
        "jdText" to "job_description",
        "resume" to "candidate.resume",
        "chatHistory" to "conversation.history",
        "latestMessage" to "conversation.latest_message",
        "screenText" to "conversation.screen_text",
        "resumeStatus" to "conversation.resume_status",
        "elapsedTime" to "conversation.elapsed_time"
    )

    fun match(input: GreetingInput): JSONObject = request(
        input.model,
        GreetingRules.matchPrompt + "\n\n" + GreetingRules.grounding(input.jdText),
        JobMatch.source(input)
    )

    fun build(input: GreetingInput, match: JobMatch): JSONObject {
        val instructions = templateFields.entries.fold(input.prompt) { text, (placeholder, field) ->
            text.replace("{{$placeholder}}", "（读取 user JSON 的 $field 字段）")
        } + "\n\n" + GreetingRules.grounding(input.jdText) + "\n\n" + GreetingRules.writeFromMatch

        val inputs = JSONObject().apply {
            put("job_description", input.jdText)
            put("candidate", JSONObject().apply {
                put("resume", input.resume)
            })
            put("job_match", match.forInput(input))
            put("conversation", JSONObject().apply {
                put("mode", input.mode.tabTitle)
                put("history", input.chatHistory)
                put("latest_message", input.latestMessage)
                put("screen_text", if (input.screenText.trim() == input.jdText.trim()) "" else input.screenText)
                put("resume_status", input.resumeStatus)
                put("elapsed_time", input.elapsedTime)
            })
        }

        return request(input.model, instructions, inputs)
    }

    private fun request(model: String, instructions: String, inputs: JSONObject): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("stream", true)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", instructions))
                put(JSONObject().put("role", "user").put("content", inputs.toString()))
            })
        }
}
