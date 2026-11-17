package com.assistant.job

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GreetingPipelineTest {
    private fun inputs(request: JSONObject) = JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))

    @Test fun differentCompaniesAndRolesEachRunMatchingBeforeWriting() = runBlocking {
        for (case in MatchFixtures.cases) {
            val requests = mutableListOf<JSONObject>()
            val stages = mutableListOf<GreetingStage>()
            val output = GreetingPipeline.generate(case.input, complete = { request ->
                requests.add(request)
                if (requests.size == 1) case.response else "文案响应"
            }, onStage = { stages.add(it) })
            assertEquals("文案响应", output)
            assertEquals(listOf(GreetingStage.MATCHING, GreetingStage.WRITING), stages)
            assertEquals(2, requests.size)
            val first = inputs(requests[0])
            val second = inputs(requests[1])
            for (data in listOf(first, second)) {
                assertEquals(case.input.jdText, data.getString("job_description"))
                assertEquals(MatchFixtures.resume, data.getJSONObject("candidate").getString("resume"))
            }
            assertEquals(3, first.getJSONArray("jd_segments").length())
            assertFalse(first.has("job_match"))
            val matched = second.getJSONObject("job_match").getJSONArray("segments")
            assertEquals(3, matched.length())
            val item = matched.getJSONObject(1).getJSONArray("items").getJSONObject(0)
            if (case.selectedEvidence.isBlank()) {
                assertEquals("unverified", item.getString("status"))
                assertEquals(0, item.getJSONArray("resume_quotes").length())
            } else {
                assertEquals(case.selectedEvidence, item.getJSONArray("resume_quotes").getString(0))
                assertFalse(second.getJSONObject("job_match").toString().contains("知识库项目"))
            }
        }
    }

    @Test fun invalidMatchingNeverCallsTheWritingStage() = runBlocking {
        for (raw in listOf("不是JSON", "{\"segments\":[]}")) {
            var calls = 0
            val stages = mutableListOf<GreetingStage>()
            try {
                GreetingPipeline.generate(MatchFixtures.cases[0].input, complete = { calls++; raw }, onStage = { stages.add(it) })
                fail("无效匹配不应进入文案生成")
            } catch (e: IllegalArgumentException) {
                assertEquals(1, calls)
                assertEquals(listOf(GreetingStage.MATCHING), stages)
            }
        }
    }

    @Test fun everyConversationModeAndCustomPromptStillPassesTheMatchingGate() = runBlocking {
        val case = MatchFixtures.cases[0]
        for (mode in GenerationMode.values()) {
            val input = case.input.copy(mode = mode, prompt = "我的自定义文风{{resume}}", chatHistory = "已沟通过", latestMessage = "介绍一下项目")
            var calls = 0
            GreetingPipeline.generate(input, complete = { request ->
                calls++
                val system = request.getJSONArray("messages").getJSONObject(0).getString("content")
                if (calls == 1) {
                    assertFalse(system.contains("我的自定义文风"))
                    case.response
                } else {
                    assertTrue(system.contains("我的自定义文风"))
                    val data = inputs(request)
                    assertTrue(data.has("job_match"))
                    assertEquals(mode.tabTitle, data.getJSONObject("conversation").getString("mode"))
                    assertEquals("介绍一下项目", data.getJSONObject("conversation").getString("latest_message"))
                    "文案响应"
                }
            })
            assertEquals(2, calls)
        }
    }

    @Test fun cancellationDuringMatchingPreventsSecondCallAndStaleResult() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        var calls = 0
        var delivered = false
        val job = launch {
            GreetingPipeline.generate(MatchFixtures.cases[0].input, complete = {
                calls++
                started.complete(Unit)
                response.await()
            })
            delivered = true
        }
        started.await()
        job.cancelAndJoin()
        response.complete(MatchFixtures.cases[0].response)
        assertEquals(1, calls)
        assertFalse(delivered)
    }

    @Test fun networkFailureDoesNotFallBackToUncheckedGeneration() = runBlocking {
        for (failedCall in 1..2) {
            var calls = 0
            try {
                GreetingPipeline.generate(MatchFixtures.cases[0].input, complete = {
                    calls++
                    if (calls == failedCall) throw java.io.IOException("network failed")
                    MatchFixtures.cases[0].response
                })
                fail("请求失败应返回错误")
            } catch (e: java.io.IOException) {
                assertEquals(failedCall, calls)
            }
        }
    }

    @Test fun missingInputsFailBeforeAnyApiCall() = runBlocking {
        for (input in listOf(MatchFixtures.cases[0].input.copy(jdText = ""), MatchFixtures.cases[0].input.copy(resume = ""))) {
            var calls = 0
            try {
                GreetingPipeline.generate(input, complete = { calls++; "" })
                fail("空输入不应请求API")
            } catch (e: IllegalArgumentException) {
                assertEquals(0, calls)
            }
        }
    }
}
