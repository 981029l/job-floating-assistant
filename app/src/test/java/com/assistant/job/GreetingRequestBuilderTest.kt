package com.assistant.job

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GreetingRequestBuilderTest {
    private val jd = javaClass.getResource("/medical_frontend_jd.txt")!!.readText(Charsets.UTF_8)
    private val resume = "七年研发经验。项目甲：Windows桌面交付。项目乙：Canvas动画调度。项目丙：小程序架构。"

    private fun inputs(request: JSONObject): JSONObject =
        JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))

    private fun build(input: GreetingInput) = GreetingRequestBuilder.build(input, MatchFixtures.unverified(input))

    @Test fun defaultRequestPreservesCompleteInputsAndSeparatesRoles() {
        val request = build(GreetingInput("selected-model", GreetingRules.defaultPrompt, GenerationMode.FIRST_ICEBREAK, jd, resume))
        val messages = request.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertFalse(messages.getJSONObject(0).getString("content").contains(resume))
        assertEquals(jd, inputs(request).getString("job_description"))
        assertEquals(resume, inputs(request).getJSONObject("candidate").getString("resume"))
        assertEquals("selected-model", request.getString("model"))
    }

    @Test fun lateProjectEvidenceInLongResumeIsPreserved() {
        val longResume = "项目甲：AI应用交互与知识管理。\n".repeat(300) + "最后的项目：Canvas动画调度和Windows桌面交付。"
        val request = build(GreetingInput("model", GreetingRules.defaultPrompt, GenerationMode.FIRST_ICEBREAK, jd, longResume))
        assertEquals(longResume, inputs(request).getJSONObject("candidate").getString("resume"))
    }

    @Test fun templateSubstitutionNeverChangesContentInsideTheResumeOrJd() {
        val template = "按{{jdText}}匹配{{resume}}，参考{{chatHistory}}与{{latestMessage}}。"
        val jdWithBraces = jd + "原始文本包含{{resume}}和\"引号\"。"
        val resumeWithBraces = resume + "模板开发：{{latestMessage}}\n下一行保留。"
        val request = build(GreetingInput("model", template, GenerationMode.IN_DEPTH_CHAT, jdWithBraces, resumeWithBraces,
            chatHistory = "历史聊天", latestMessage = "能介绍小程序项目吗？"))
        val data = inputs(request)
        assertEquals(jdWithBraces, data.getString("job_description"))
        assertEquals(resumeWithBraces, data.getJSONObject("candidate").getString("resume"))
        assertEquals("能介绍小程序项目吗？", data.getJSONObject("conversation").getString("latest_message"))
        val system = request.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system.contains("candidate.resume"))
        assertFalse(system.contains("{{resume}}"))
    }

    @Test fun customPromptWithoutJdPlaceholderStillReceivesAllContext() {
        val request = build(GreetingInput("model", "按{{resume}}写回复", GenerationMode.RESUME_FOLLOW_UP, jd, resume,
            screenText = "对方询问作品集", resumeStatus = "未知", elapsedTime = "未知"))
        assertEquals(jd, inputs(request).getString("job_description"))
        assertEquals("对方询问作品集", inputs(request).getJSONObject("conversation").getString("screen_text"))
    }

    @Test fun unknownChatStateStaysUnknownAndDuplicateScreenJdIsNotRepeated() {
        val request = build(GreetingInput("model", "规则", GenerationMode.FIRST_ICEBREAK, jd, resume, screenText = jd))
        val conversation = inputs(request).getJSONObject("conversation")
        for (field in listOf("history", "latest_message", "screen_text", "resume_status", "elapsed_time")) {
            assertEquals("", conversation.getString(field))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingJdCannotProduceAGenericRequest() {
        GreetingRequestBuilder.match(GreetingInput("model", "规则", GenerationMode.FIRST_ICEBREAK, " ", resume))
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingResumeCannotProduceUnsupportedClaims() {
        GreetingRequestBuilder.match(GreetingInput("model", "规则", GenerationMode.FIRST_ICEBREAK, jd, ""))
    }
}
