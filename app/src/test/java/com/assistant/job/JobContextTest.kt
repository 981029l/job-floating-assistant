package com.assistant.job

import org.junit.Assert.*
import org.junit.Test

class JobContextTest {
    private val packageName = "com.example.jobs"

    private fun lines(company: String, title: String = "前端工程师") =
        listOf(title, company, "工作职责：", "负责 PC 和小程序的开发、前端架构设计与性能优化。")

    @Test fun sameTitleAtDifferentCompaniesKeepsBothJds() {
        val store = JobMemoryStore()
        val first = lines("甲科技公司")
        val second = lines("乙网络公司")
        store.record(packageName, first, first.joinToString("\n"))
        store.record(packageName, second, second.joinToString("\n"))
        assertEquals(first.joinToString("\n"), store.find(packageName, first.take(2))?.fullJd)
        assertEquals(second.joinToString("\n"), store.find(packageName, second.take(2))?.fullJd)
    }

    @Test fun sameCompanyDifferentRolesDoNotMatchEachOther() {
        val store = JobMemoryStore()
        val jd = lines("甲科技公司")
        store.record(packageName, jd, jd.joinToString("\n"))
        assertNull(store.find(packageName, listOf("后端工程师", "甲科技公司")))
    }

    @Test fun missingOrUnrelatedChatIdentityNeverUsesLatestJob() {
        val store = JobMemoryStore()
        val jd = lines("甲科技公司")
        store.record(packageName, jd, jd.joinToString("\n"))
        assertNull(store.find(packageName, listOf("前端工程师", "乙网络公司")))
        assertNull(store.find(packageName, listOf("前端工程师")))
        assertNull(store.find(packageName, listOf("", "前端", "甲科技")))
        assertNull(store.find("com.other.jobs", jd.take(2)))
    }

    @Test fun chatCannotOverwriteStoredRequirements() {
        val store = JobMemoryStore()
        val jd = lines("甲科技公司")
        store.record(packageName, jd, jd.joinToString("\n"))
        val chat = listOf("前端工程师", "甲科技公司", "您好，这是我的简历，希望进一步沟通岗位相关情况。")
        store.record(packageName, chat, chat.joinToString("\n"))
        assertEquals(jd.joinToString("\n"), store.find(packageName, chat)?.fullJd)
    }

    @Test fun repeatedReadingUpdatesOnlyTheSameJob() {
        val store = JobMemoryStore()
        val jd = lines("甲科技公司")
        val longer = jd + "任职要求：有 Canvas 渲染与性能优化经验。"
        store.record(packageName, jd, jd.joinToString("\n"))
        store.record(packageName, longer, longer.joinToString("\n"))
        assertEquals(longer.joinToString("\n"), store.find(packageName, jd.take(2))?.fullJd)
    }

    @Test fun jobSwitchDetectedEvenWhenActivityAndTitleAreUnchanged() {
        assertTrue(JobContext.changed(JobContext.identity(lines("甲科技公司")), JobContext.identity(lines("乙网络公司"))))
        assertFalse(JobContext.changed(JobContext.identity(lines("甲科技公司")), JobContext.identity(listOf("任职要求：", "掌握 Vue 和 React"))))
    }

    @Test fun requirementsAndRecommendedJobsAreSeparated() {
        val jd = lines("甲科技公司")
        assertEquals(jd, JobContext.jdLines(jd + listOf("推荐职位", "后端工程师", "乙网络公司")))
        assertTrue(JobContext.hasJdSection(listOf("前端工程师\n工作职责：负责多端开发")))
        assertFalse(JobContext.hasJdSection(listOf("您好，我想了解一下岗位职责和工作内容。")))
    }

    @Test fun responsibilitiesCannotBecomeCompanyIdentity() {
        assertEquals("", JobContext.identity(listOf("前端工程师", "工作职责：", "负责企业信息系统设计开发")).company)
    }

    @Test fun bossChatMatchesFullCompanyAgainstHrBadgeAndSalaryTitle() {
        val store = JobMemoryStore()
        val jdLines = listOf(
            "全栈工程师（独立站电商方向）",
            "15-20K",
            "英智创新（深圳）科技有限公司",
            "岗位职责：",
            "1. 参与 AI 电商建站平台架构设计与核心功能开发。"
        )
        val fullJd = jdLines.joinToString("\n")
        assertTrue(store.record(packageName, jdLines, fullJd))

        val bossChat = listOf(
            "张先生",
            "英智创新·技术总监",
            "打电话", "换微信", "发简历", "换电话", "不感兴趣",
            "全栈工程师（独立站电商方向） 15-20K",
            "深圳", "5-10年", "本科",
            "已读",
            "您好，看到您在招全栈工程师。"
        )
        assertEquals(fullJd, store.find(packageName, bossChat)?.fullJd)
    }

    @Test fun bossJdExtractsShortCompanyFromHrBadgeWhenNoLegalSuffix() {
        val store = JobMemoryStore()
        val jdLines = listOf(
            "前端开发工程师",
            "18-25K",
            "李女士",
            "小红书·招聘经理",
            "职位描述：",
            "负责社区核心业务前端研发与性能优化。"
        )
        val fullJd = jdLines.joinToString("\n")
        assertTrue(store.record(packageName, jdLines, fullJd))

        val bossChat = listOf(
            "李女士",
            "小红书 · 招聘经理",
            "沟通职位：前端开发工程师",
            "已读"
        )
        assertEquals(fullJd, store.find(packageName, bossChat)?.fullJd)
    }

    @Test fun bossBottomCompanyCardAndScrollUpgradeDoNotDuplicateOrReset() {
        val store = JobMemoryStore()
        val topScreen = listOf(
            "前端开发工程师",
            "15-20K",
            "王先生",
            "深讯数据·研发总监",
            "职位详情",
            "工作职责：",
            "负责大型企业级系统前端架构设计与性能优化。"
        )
        val topId = JobContext.identity(topScreen)
        assertEquals("前端开发工程师", topId.title)
        assertEquals("深讯数据", topId.company)
        assertTrue(store.record(packageName, JobContext.jdLines(topScreen), JobContext.jdLines(topScreen).joinToString("\n"), topId))

        val bottomScreen = listOf(
            "工作职责：",
            "负责大型企业级系统前端架构设计与性能优化。",
            "公司信息",
            "深讯数据科技有限公司",
            "已上市 · 100-499人"
        )
        val bottomId = JobContext.identity(bottomScreen)
        assertEquals("深讯数据科技有限公司", bottomId.company)
        assertFalse(JobContext.changed(topId, bottomId))

        val mergedId = JobContext.Identity(topId.title, bottomId.company)
        val cleanJd = JobContext.jdLines(topScreen).joinToString("\n")
        assertTrue(store.record(packageName, JobContext.jdLines(topScreen), cleanJd, mergedId))

        // Must resolve uniquely without duplicate collision
        val bossChat = listOf("王先生", "深讯数据·研发总监", "前端开发工程师", "已读")
        assertEquals(cleanJd, store.find(packageName, bossChat)?.fullJd)
    }

    @Test fun cityTagInChatNeverMatchesCompanyNamePrefix() {
        val store = JobMemoryStore()
        val jdLines = listOf(
            "前端开发工程师",
            "深圳市英智科技有限公司",
            "岗位职责：",
            "负责多端前端架构设计与核心功能开发。"
        )
        assertTrue(store.record(packageName, jdLines, jdLines.joinToString("\n")))

        val unrelatedChat = listOf("陈先生", "深圳", "前端开发工程师", "已读")
        assertNull(store.find(packageName, unrelatedChat))
    }

    @Test fun memoryStorePersistsAndRestoresAcrossRestarts() {
        val store1 = JobMemoryStore()
        val jdLines = listOf(
            "全栈工程师（独立站电商方向）",
            "英智创新（深圳）科技有限公司",
            "岗位职责：",
            "参与 AI 电商建站平台架构设计与核心功能开发。"
        )
        val fullJd = jdLines.joinToString("\n")
        assertTrue(store1.record(packageName, jdLines, fullJd))

        val serialized = store1.toJson()
        val store2 = JobMemoryStore()
        store2.loadJson(serialized)
        val bossChat = listOf("张先生", "英智创新·技术总监", "全栈工程师（独立站电商方向）", "已读")
        assertEquals(fullJd, store2.find(packageName, bossChat)?.fullJd)
    }

    @Test fun bossJobDetailHeaderWithoutDutyKeywordIsRecognized() {
        val bossDetail = listOf(
            "全栈工程师（独立站电商方向）",
            "15-20K",
            "张先生",
            "英智创新·技术总监",
            "职位详情",
            "1. 参与 AI 电商建站平台架构设计与核心功能开发。"
        )
        assertTrue(JobContext.hasJdSection(bossDetail))
        assertEquals("全栈工程师（独立站电商方向）", JobContext.identity(bossDetail).title)
        assertEquals("英智创新", JobContext.identity(bossDetail).company)
    }

    @Test fun cityPrefixedFullCompanyMatchesShortCompanyInBossChat() {
        val store = JobMemoryStore()
        val jdLines = listOf(
            "全栈工程师（独立站电商方向）",
            "15-20K",
            "深圳市英智创新科技有限公司",
            "岗位职责：",
            "参与 AI 电商建站平台架构设计与核心功能开发。"
        )
        val fullJd = jdLines.joinToString("\n")
        assertTrue(store.record(packageName, jdLines, fullJd))

        // Chat header with HR badge
        assertEquals(fullJd, store.find(packageName, listOf("张先生", "英智创新·技术总监", "全栈工程师（独立站电商方向）", "已读"))?.fullJd)
        // Chat header with active status badge
        assertEquals(fullJd, store.find(packageName, listOf("张先生", "英智创新 · 刚刚活跃", "沟通职位：全栈工程师（独立站电商方向） 15-20K", "已读"))?.fullJd)
        // Chat header with bare short company name
        assertEquals(fullJd, store.find(packageName, listOf("张先生", "英智创新", "全栈工程师（独立站电商方向） | 15-20K", "已读"))?.fullJd)
    }

    @Test fun extractChatMessagesPreservesShortHrQuestionsAndFiltersUiNoise() {
        val screenLines = listOf(
            "张先生",
            "英智创新·技术总监",
            "打电话", "换微信", "发简历", "换电话", "不感兴趣",
            "全栈工程师（独立站电商方向）",
            "15-20K",
            "深圳", "5-10年", "本科",
            "昨天 14:20",
            "您好，看到您在招全栈工程师，我有7年React与Node.js经验。",
            "已读",
            "10:35",
            "发份简历",
            "期望薪资多少",
            "温馨提示：请勿在平台外转账",
            "常用语", "发送"
        )
        val messages = JobContext.extractChatMessages(screenLines, "全栈工程师（独立站电商方向）", "深圳市英智创新科技有限公司")
        assertEquals(
            listOf(
                "您好，看到您在招全栈工程师，我有7年React与Node.js经验。",
                "发份简历",
                "期望薪资多少"
            ),
            messages
        )
    }

    @Test fun titleParenthesesWidthAndTruncationAreMatchedInChat() {
        val store = JobMemoryStore()
        val jdLines = listOf(
            "全栈工程师（独立站电商方向）",
            "工作职责：",
            "参与 AI 电商建站平台架构设计与核心功能开发。",
            "深圳市英智创新科技有限公司"
        )
        val fullJd = jdLines.joinToString("\n")
        assertTrue(store.record(packageName, jdLines, fullJd))

        // Half-width parentheses
        assertEquals(fullJd, store.find(packageName, listOf("张先生", "英智创新·技术总监", "全栈工程师(独立站电商方向)", "已读"))?.fullJd)
        // Truncated title with ellipsis
        assertEquals(fullJd, store.find(packageName, listOf("张先生", "英智创新·技术总监", "沟通职位：全栈工程师（独立站...", "已读"))?.fullJd)
    }

    @Test fun hrMessageCanBeExactSendResumeWithoutBeingFilteredByTopChips() {
        val screenLines = listOf(
            "张先生",
            "英智创新·技术总监",
            "打电话", "换微信", "发简历", "换电话", "不感兴趣",
            "全栈工程师（独立站电商方向）",
            "15-20K",
            "昨天 14:20",
            "您好，看到您在招全栈工程师。",
            "已读",
            "发简历",
            "常用语", "发送"
        )
        val messages = JobContext.extractChatMessages(screenLines, "全栈工程师（独立站电商方向）", "深圳市英智创新科技有限公司")
        assertEquals(
            listOf(
                "您好，看到您在招全栈工程师。",
                "发简历"
            ),
            messages
        )
    }
}
