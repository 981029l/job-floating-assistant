package com.assistant.job

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JobMatchTest {
    private val case = MatchFixtures.cases.first()

    private fun reject(raw: String, message: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { JobMatch.parse(raw, case.input) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message))
    }

    @Test fun incompleteDuplicateAndForeignSegmentsAreRejected() {
        val missing = JSONObject(case.response)
        missing.getJSONArray("segments").remove(2)
        reject(missing.toString(), "遗漏")
        for (id in listOf(1, 99)) {
            val invalid = JSONObject(case.response)
            invalid.getJSONArray("segments").getJSONObject(1).put("id", id)
            reject(invalid.toString(), "段落")
        }
    }

    @Test fun inventedResumeQuotesAndEvidenceFreeClaimsAreRejected() {
        for (evidence in listOf(JSONArray(listOf("熟悉Unity3D开发")), JSONArray())) {
            val invalid = JSONObject(case.response)
            invalid.getJSONArray("segments").getJSONObject(1).getJSONArray("items").getJSONObject(0).put("resume_quotes", evidence)
            reject(invalid.toString(), "简历")
        }
    }

    @Test fun partialQuoteCannotHideTheRestOfACompoundRequirement() {
        val input = case.input.copy(jdText = "要求Canvas、WebGL和Unity3D")
        val incomplete = MatchFixtures.report(MatchFixtures.row(1,
            MatchFixtures.item("要求Canvas", "direct", listOf("直播项目：使用Canvas实现弹幕动画和渲染优化。"))))
        assertThrows(IllegalArgumentException::class.java) { JobMatch.parse(incomplete, input) }
        val complete = MatchFixtures.report(MatchFixtures.row(1,
            MatchFixtures.item("要求Canvas、", "direct", listOf("直播项目：使用Canvas实现弹幕动画和渲染优化。")),
            MatchFixtures.item("WebGL和Unity3D")))
        val report = JobMatch.parse(complete, input).forInput(input)
        assertEquals("unverified", report.getJSONArray("segments").getJSONObject(0).getJSONArray("items").getJSONObject(1).getString("status"))
    }

    @Test fun oldMatchesCannotBeUsedWithAnotherCompanyOrUpdatedResume() {
        val match = JobMatch.parse(case.response, case.input)
        assertThrows(IllegalArgumentException::class.java) { match.forInput(MatchFixtures.cases[1].input) }
        assertThrows(IllegalArgumentException::class.java) { match.forInput(case.input.copy(resume = "已更新的简历")) }
    }

    @Test fun malformedAndEntirelyContextResponsesCannotProduceGreetings() {
        reject("分析好了，直接打招呼即可", "格式")
        reject("{\"segments\":[", "格式")
        val context = JSONObject(case.response)
        val rows = context.getJSONArray("segments")
        for (i in 0 until rows.length()) {
            rows.getJSONObject(i).getJSONArray("items").getJSONObject(0)
                .put("kind", "context").put("status", "context").put("resume_quotes", JSONArray())
        }
        reject(context.toString(), "未提取到")
    }

    @Test fun relatedExperienceAndUnverifiedSkillsStaySeparate() {
        val input = case.input.copy(jdText = "负责WebGL动画开发；熟悉Unity3D。")
        val response = MatchFixtures.report(
            MatchFixtures.row(1, MatchFixtures.item("负责WebGL动画开发；", "related", listOf("直播项目：使用Canvas实现弹幕动画和渲染优化。"), "duty")),
            MatchFixtures.row(2, MatchFixtures.item("熟悉Unity3D。")))
        val report = JobMatch.parse(response, input).forInput(input).getJSONArray("segments")
        assertEquals("related", report.getJSONObject(0).getJSONArray("items").getJSONObject(0).getString("status"))
        assertEquals(0, report.getJSONObject(1).getJSONArray("items").getJSONObject(0).getJSONArray("resume_quotes").length())
    }

    @Test fun layoutWhitespaceAndFencesAreToleratedWithoutChangingClaims() {
        val response = case.response.replace("使用React开发订单页面", "使用React 开发订单页面")
        val match = JobMatch.parse("```json\n$response\n```", case.input)
        val copy = match.forInput(case.input)
        copy.remove("segments")
        assertEquals(3, match.forInput(case.input).getJSONArray("segments").length())
    }
}
