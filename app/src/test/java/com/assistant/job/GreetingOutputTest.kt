package com.assistant.job

import org.junit.Assert.*
import org.junit.Test

class GreetingOutputTest {
    @Suppress("UNCHECKED_CAST")
    private fun parse(raw: String): List<GreetingOption> {
        val parser = DeepSeekClient::class.java.getDeclaredMethod("parseOptions", String::class.java, GenerationMode::class.java)
        parser.isAccessible = true
        return parser.invoke(DeepSeekClient, raw, GenerationMode.FIRST_ICEBREAK) as List<GreetingOption>
    }

    @Test fun chosenProjectAndTechnicalClaimsAreNotSilentlyRewritten() {
        val text = "您好，我曾负责企业级AI知识库，具备SSE流式交互经验，并完成相关页面性能优化。"
        assertEquals(text, parse(text).single().text)
    }

    @Test fun bothOptionsRetainTheirOwnEvidence() {
        val first = "您好，我有PC端和小程序开发经验，负责过多端功能交付与联调，希望交流岗位需求。"
        val second = "您好，我有Canvas渲染与性能优化实践，希望进一步交流认知评估产品的前端工作。"
        val options = parse("===风格一===\r\n$first\r\n\r\n===风格二===\r\n$second")
        assertEquals(listOf(first, second), options.map { it.text })
    }
}
