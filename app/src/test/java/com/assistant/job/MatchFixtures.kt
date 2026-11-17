package com.assistant.job

import org.json.JSONArray
import org.json.JSONObject

internal object MatchFixtures {
    const val resume = """有5年前端开发经验。本科，软件工程。
知识库项目：集成智能问答与SSE展示。
零售后台项目：使用React开发订单页面并优化长列表。
直播项目：使用Canvas实现弹幕动画和渲染优化。
移动项目：使用Vue和微信小程序完成商城页面及接口联调。
工作中使用AI工具辅助代码开发和调试。"""

    fun item(quote: String, status: String = "unverified", evidence: List<String> = emptyList(), kind: String = "required") =
        JSONObject().put("jd_quote", quote).put("kind", kind).put("status", status)
            .put("resume_quotes", JSONArray(evidence)).put("note", "仅依据所引原文判断，不外推其他能力")

    fun row(id: Int, vararg items: JSONObject) = JSONObject().put("id", id).put("items", JSONArray(items.toList()))

    fun report(vararg rows: JSONObject) = JSONObject().put("segments", JSONArray(rows.toList())).toString()

    fun unverified(input: GreetingInput): JobMatch {
        val raw = report(*JobMatch.segments(input.jdText).mapIndexed { index, text -> row(index + 1, item(text)) }.toTypedArray())
        return JobMatch.parse(raw, input)
    }

    data class Case(val input: GreetingInput, val response: String, val selectedEvidence: String)

    val cases: List<Case> = listOf(
        Triple("甲商贸公司·前端工程师\n负责React订单页面开发\n要求长列表性能优化", "零售后台项目：使用React开发订单页面并优化长列表。", "direct"),
        Triple("乙文娱公司·前端工程师\n负责Canvas动画开发\n要求渲染性能优化", "直播项目：使用Canvas实现弹幕动画和渲染优化。", "direct"),
        Triple("丙零售公司·小程序工程师\n负责Vue及微信小程序商城开发\n要求接口联调经验", "移动项目：使用Vue和微信小程序完成商城页面及接口联调。", "direct"),
        Triple("丁数据公司·Java工程师\n负责Java交易服务开发\n要求Spring与MySQL性能优化", "", "unverified")
    ).map { (jd, evidence, status) ->
        val lines = jd.lines()
        val quotes = if (evidence.isBlank()) emptyList() else listOf(evidence)
        Case(GreetingInput("test-model", GreetingRules.defaultPrompt, GenerationMode.FIRST_ICEBREAK, jd, resume),
            report(row(1, item(lines[0], "context", kind = "context")),
                row(2, item(lines[1], status, quotes, "duty")),
                row(3, item(lines[2], status, quotes))), evidence)
    }
}
