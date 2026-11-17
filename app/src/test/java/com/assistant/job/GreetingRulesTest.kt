package com.assistant.job

import org.junit.Assert.*
import org.junit.Test

class GreetingRulesTest {
    @Test fun suppliedMedicalJobIsRecognizedAsRequiringAiToolUse() {
        val jd = javaClass.getResource("/medical_frontend_jd.txt")!!.readText(Charsets.UTF_8)
        assertTrue(GreetingRules.hasAiToolRequirement(jd))
    }

    @Test fun chineseTextDoesNotNeedSpacesAroundAi() {
        assertTrue(GreetingRules.hasAiToolRequirement("熟练使用AI编程，有PC端及小程序开发经验"))
        assertTrue(GreetingRules.hasAiToolRequirement("熟练使用AI，有PC端及小程序开发经验"))
        assertTrue(GreetingRules.hasAiToolRequirement("有AI辅助研发及性能优化经验"))
        assertTrue(GreetingRules.hasAiToolRequirement("熟悉 Cursor 和 Claude Code"))
    }

    @Test fun modelDevelopmentAndUnrelatedEnglishDoNotBecomeToolRequirements() {
        assertFalse(GreetingRules.hasAiToolRequirement("负责医疗AI模型的训练与推理服务开发"))
        assertFalse(GreetingRules.hasAiToolRequirement("熟练使用Tailwind，负责邮件mail页面和Canvas渲染"))
    }

    @Test fun onlyUnmodifiedPreviousDefaultMigrates() {
        val oldDefault = javaClass.getResource("/previous_default_prompt.txt")!!.readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertTrue(GreetingRules.isPreviousDefault(oldDefault))
        assertTrue(GreetingRules.isPreviousDefault(oldDefault.replace("\n", "\r\n")))
        assertFalse(GreetingRules.isPreviousDefault(oldDefault + "\n请突出我的小程序项目。"))
        assertFalse(GreetingRules.isPreviousDefault("自定义提示词：请根据真实经历生成，不要使用双微型分句法则。"))
    }
}
