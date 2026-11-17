package com.assistant.job

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class JobMatch private constructor(
    private val jdText: String,
    private val resume: String,
    private val serialized: String
) {
    fun forInput(input: GreetingInput): JSONObject {
        require(input.jdText == jdText && input.resume == resume) { "岗位或简历已变更，请重新匹配后生成" }
        return JSONObject(serialized)
    }

    companion object {
        private val separators = Regex("(?<=[；;。])|[\\r\\n]+")
        private val whitespace = Regex("[\\s\\u00a0\\u3000]+")
        private val kinds = setOf("duty", "required", "preferred", "context")
        private val statuses = setOf("direct", "related", "unverified", "context")

        private fun compact(text: String): String = text.replace(whitespace, "")

        fun segments(jdText: String): List<String> = jdText.split(separators).map(String::trim).filter(String::isNotEmpty)

        fun source(input: GreetingInput): JSONObject {
            require(input.jdText.isNotBlank()) { "未确认当前岗位 JD，请打开岗位详情页读取，或粘贴完整 JD 后再生成" }
            require(input.resume.isNotBlank()) { "请先录入完整简历，才能根据真实经历匹配岗位" }
            return JSONObject().apply {
                put("job_description", input.jdText)
                put("candidate", JSONObject().put("resume", input.resume))
                put("jd_segments", JSONArray().apply {
                    segments(input.jdText).forEachIndexed { index, text ->
                        put(JSONObject().put("id", index + 1).put("text", text))
                    }
                })
            }
        }

        fun parse(raw: String, input: GreetingInput): JobMatch {
            try {
                val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val rows = JSONObject(cleaned).getJSONArray("segments")
                val source = segments(input.jdText)
                require(rows.length() == source.size) { "岗位匹配遗漏了 JD 段落，请重试" }
                val seen = mutableSetOf<Int>()
                val normalizedResume = compact(input.resume)
                val validated = mutableMapOf<Int, JSONObject>()
                var requirementCount = 0
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val id = row.getInt("id")
                    require(id in 1..source.size && seen.add(id)) { "岗位匹配段落重复或不属于当前 JD，请重试" }
                    val items = row.getJSONArray("items")
                    require(items.length() > 0) { "岗位匹配未核对第 $id 段，请重试" }
                    val quotes = StringBuilder()
                    val checkedItems = JSONArray()
                    for (itemIndex in 0 until items.length()) {
                        val item = items.getJSONObject(itemIndex)
                        val quote = item.getString("jd_quote")
                        val kind = item.getString("kind")
                        val status = item.getString("status")
                        val evidence = item.getJSONArray("resume_quotes")
                        val note = item.getString("note")
                        require(quote.isNotBlank() && note.isNotBlank() && kind in kinds && status in statuses) {
                            "岗位匹配缺少有效的要求或匹配状态，请重试"
                        }
                        require((kind == "context") == (status == "context")) { "岗位背景与任职要求分类冲突，请重试" }
                        require((status == "direct" || status == "related") == (evidence.length() > 0)) {
                            "岗位匹配结论缺少对应简历依据，请重试"
                        }
                        val checkedEvidence = JSONArray()
                        for (evidenceIndex in 0 until evidence.length()) {
                            val text = evidence.getString(evidenceIndex)
                            require(text.isNotBlank() && normalizedResume.contains(compact(text))) {
                                "岗位匹配引用了简历中不存在的内容，请重试"
                            }
                            checkedEvidence.put(text)
                        }
                        quotes.append(compact(quote))
                        if (kind != "context") requirementCount++
                        checkedItems.put(JSONObject().put("jd_quote", quote).put("kind", kind)
                            .put("status", status).put("resume_quotes", checkedEvidence).put("note", note))
                    }
                    require(quotes.toString() == compact(source[id - 1])) { "岗位匹配遗漏或改写了第 $id 段要求，请重试" }
                    validated[id] = JSONObject().put("id", id).put("source", source[id - 1]).put("items", checkedItems)
                }
                require(requirementCount > 0) { "未提取到岗位职责或任职要求，请补充完整 JD 后重试" }
                val report = JSONObject().put("segments", JSONArray().apply {
                    for (id in 1..source.size) put(validated.getValue(id))
                })
                return JobMatch(input.jdText, input.resume, report.toString())
            } catch (e: JSONException) {
                throw IllegalArgumentException("岗位匹配结果格式不完整，请重试", e)
            }
        }
    }
}
