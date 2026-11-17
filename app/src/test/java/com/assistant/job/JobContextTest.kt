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
}
