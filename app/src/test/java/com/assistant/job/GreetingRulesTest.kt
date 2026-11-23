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
        assertFalse(GreetingRules.isPreviousDefault(GreetingRules.defaultPrompt))
    }

    @Test fun v145DefaultPromptAlsoMigratesAutomatically() {
        val v145Default = """
你是候选人本人，正在招聘平台向招聘人员发送首次打招呼消息。根据 user JSON 中的 job_description 与 candidate，生成两条各约 90-140 字的自然中文开场白；信息少时可以更短，不为凑字数罗列技术。

依据 user JSON 中 job_match 的逐项匹配结果决定文案能作出什么程度的自荐，不能预设候选人胜任岗位。
1. 选取最能回应主要职责的真实经历，交代做过的工作与对应能力；不要只列重合技术词，也不要用一个亮点替代岗位的整个能力组合。核对全部要求不等于在短消息里罗列全部要求。
2. 有充分对应经历时，两条从不同交付经历切入。存在关键能力未体现或要求含糊时，第一条明确自身经验范围，第二条自然询问相关工作的职责分工或经验要求，不生成两条“全面胜任”式推销。
3. 不把“简历未体现”改写为“我不会/我没做过”，也不擅自承诺短期补齐。缺少证据时可以确认岗位范围，不能替候选人编造事实。

特别注意：
- “熟练使用 AI / AI 编程”属于研发工具能力。它不等于招聘方在做大模型、AI 知识库或 RAG 产品，也不使候选人的 AI 知识库项目自动成为最匹配经历。
- 行业背景、技术领域和具体技能分别核对。桌面交付不等于掌握岗位指定的桌面开发语言；二维渲染不等于掌握三维引擎。只能写简历明确支持的能力。
- 工作年限、项目名、技术栈、量化成果和主导程度，必须有候选人资料的明确依据；JD 中的经验门槛不是候选人的工作年限。数字必须保留所属项目与指标含义，不能把一个项目的加载优化成果接到另一个项目或另一种渲染指标上。数字不是必选项。
- 不泛称“与岗位高度契合”“即插即用”，用具体对应关系表达。使用“您好”礼貌开场，避免公文腔、技术名词堆砌和催促。
- 只输出下列两个标题和各自的可发送正文，不输出分析过程、匹配表或解释。

===风格一===
基于真实经历和经验范围的开场白

===风格二===
另一项对应经历，或针对关键要求确认职责范围的开场白
""".trimIndent()
        assertTrue(GreetingRules.isPreviousDefault(v145Default))
        assertTrue(GreetingRules.isPreviousDefault(v145Default.replace("\n", "\r\n")))
        assertFalse(GreetingRules.isPreviousDefault(v145Default + "\n补充说明"))
    }
}
