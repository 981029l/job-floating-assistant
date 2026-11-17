package com.assistant.job

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.OkHttpClient
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.net.SocketTimeoutException

class GreetingTransportTest {
    private fun completion(content: String, reason: String = "stop") = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("finish_reason", reason).put("message", JSONObject().put("content", content)))).toString()

    private fun event(content: String? = null, reasoning: String? = null, reason: String? = null): String {
        val delta = JSONObject().put("content", content).put("reasoning_content", reasoning)
        return "data: " + JSONObject().put("choices", JSONArray().put(JSONObject()
            .put("index", 0).put("delta", delta).put("finish_reason", reason))) + "\n\n"
    }

    private fun stream(content: String) = MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8")
        .setBody(event(content) + event(reason = "stop") + "data: [DONE]\n\n")

    private fun timedClient(idleMs: Long, totalMs: Long) = OkHttpClient.Builder()
        .readTimeout(idleMs, TimeUnit.MILLISECONDS).callTimeout(totalMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false).build()

    private fun trace() = GenerationDiagnostics(MatchFixtures.cases[0].input, "https://example.com/v1/chat/completions",
        "test", "test", "test", "test-only-key")

    @Test fun actualHttpRequestsCarryTheValidatedMatchIntoTheSecondCall() = runBlocking {
        val case = MatchFixtures.cases[0]
        MockWebServer().use { server ->
            server.enqueue(stream(case.response))
            server.enqueue(stream("===风格一===\n您好，做过订单页面开发。\n===风格二===\n您好，有长列表优化经验。"))
            server.start()
            val output = withTimeout(10000) {
                GreetingPipeline.generate(case.input, complete = {
                    DeepSeekClient.completeGreetingRequest(server.url("/chat/completions").toString(), "test-only-key", it)
                })
            }
            assertTrue(output.contains("订单页面"))
            assertEquals(2, server.requestCount)
            assertTrue(JSONObject(server.takeRequest().body.readUtf8()).getBoolean("stream"))
            val request = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(request.getBoolean("stream"))
            val second = JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertEquals(case.input.jdText, second.getString("job_description"))
            assertTrue(second.getJSONObject("job_match").toString().contains(case.selectedEvidence))
        }
    }

    @Test fun streamHandlesFragmentedChineseCommentsUsageAndDiscardsReasoning() = runBlocking {
        MockWebServer().use { server ->
            val wire = ": keep-alive\r\n\r\n" + event(reasoning = "PRIVATE-REASONING") +
                event(content = "您好，").replace("{\"choices\"", "{\ndata: \"choices\"") +
                event(content = "有前端经验。") + event(reason = "stop") +
                "data: {\"choices\":[],\"usage\":{\"total_tokens\":30}}\n\n" + "data: [DONE]\n\n"
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setChunkedBody(wire, 7))
            server.start()
            val diagnostics = trace()
            val output = withTimeout(5000) {
                DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject(), diagnostics)
            }
            assertEquals("您好，有前端经验。", output)
            val report = diagnostics.failure(IllegalStateException("test")).report
            assertTrue(report.contains("回答=9 字符；思考=17 字符"))
            assertTrue(report.contains("收到[DONE]=true"))
            assertTrue(report.contains("finish_reason：stop"))
            assertFalse(report.contains("PRIVATE-REASONING"))
            assertFalse(report.contains(output))
        }
    }

    @Test fun incompleteOrRejectedStreamsCannotProduceGreetings() = runBlocking {
        MockWebServer().use { server ->
            val cases = listOf(
                event("半截正文"),
                event("看似完整") + event(reason = "stop"),
                event("没有结束原因") + "data: [DONE]\n\n",
                event("截断正文", reason = "length") + "data: [DONE]\n\n",
                event("被过滤的正文", reason = "content_filter") + "data: [DONE]\n\n"
            )
            cases.forEach { server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(it)) }
            server.start()
            for (ignored in cases) {
                try {
                    withTimeout(5000) {
                        DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject())
                    }
                    fail("不完整响应不能成为招呼语")
                } catch (e: IllegalStateException) {
                    assertTrue(e.message.orEmpty().contains("中断") || e.message.orEmpty().contains("截断") || e.message.orEmpty().contains("未正常完成"))
                }
            }
            assertEquals(cases.size, server.requestCount)
        }
    }

    @Test fun streamedProviderErrorKeepsDiagnosticsWithoutReturningPartialContent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(event("不能使用的片段") + "data: {\"error\":{\"code\":\"overloaded\",\"message\":\"Busy test-only-key\"}}\n\n"))
            server.start()
            val diagnostics = trace()
            try {
                DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject(), diagnostics)
                fail("流中错误必须中止")
            } catch (e: IllegalStateException) {
                val report = diagnostics.failure(e).report
                assertTrue(report.contains("overloaded"))
                assertTrue(report.contains("HTTP状态：200"))
                assertFalse(report.contains("test-only-key"))
                assertFalse(report.contains("不能使用的片段"))
            }
        }
    }

    @Test fun stalledStreamTimesOutAndRetainsPartialProgress() = runBlocking {
        MockWebServer().use { server ->
            val prefix = event(reasoning = "PRIVATE-THOUGHT") + event("您好")
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(prefix + event(reason = "stop") + "data: [DONE]\n\n")
                .throttleBody(prefix.toByteArray(Charsets.UTF_8).size.toLong(), 1, TimeUnit.SECONDS))
            server.start()
            val diagnostics = trace()
            try {
                withTimeout(5000) {
                    DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject(), diagnostics,
                        timedClient(200, 3000))
                }
                fail("预期连续无数据超时")
            } catch (e: SocketTimeoutException) {
                val report = diagnostics.failure(e).report
                assertTrue(report.contains("回答=2 字符；思考=15 字符"))
                assertTrue(report.contains("收到[DONE]=false"))
                assertTrue(report.contains("读取完成：false"))
                assertFalse(report.contains("首批正文等待：未收到"))
                assertFalse(report.contains("PRIVATE-THOUGHT"))
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun keepAliveCannotExtendTheTotalDeadlineForever() = runBlocking {
        MockWebServer().use { server ->
            val heartbeat = ": keep-alive\n\n"
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(heartbeat.repeat(200)).throttleBody(heartbeat.length.toLong(), 50, TimeUnit.MILLISECONDS))
            server.start()
            val diagnostics = trace()
            val started = System.nanoTime()
            try {
                withTimeout(5000) {
                    DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject(), diagnostics,
                        timedClient(2000, 500))
                }
                fail("预期请求总时限")
            } catch (e: IOException) {
                assertTrue((System.nanoTime() - started) / 1_000_000 < 3000)
                assertTrue(diagnostics.failure(e).report.contains("读取完成：false"))
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun delayedGzipBodyReproducesOld45SecondTimeoutAndSucceedsWithGenerationClient() = runBlocking {
        MockWebServer().use { server ->
            val compressed = Buffer()
            GzipSink(compressed).buffer().use { it.writeUtf8(completion("延迟返回也能完整生成")) }
            repeat(2) {
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setHeader("Content-Encoding", "gzip")
                    .setBody(compressed.clone()).setBodyDelay(46, TimeUnit.SECONDS))
            }
            server.start()
            withTimeout(55000) {
                val legacy = async {
                    runCatching { DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject(),
                        httpClient = timedClient(45000, 0)) }
                }
                val fixed = async { DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject()) }
                assertTrue(legacy.await().exceptionOrNull() is SocketTimeoutException)
                assertEquals("延迟返回也能完整生成", fixed.await())
            }
        }
    }

    @Test fun truncatedModelOutputStopsBeforeWriting() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(completion(MatchFixtures.cases[0].response, "length")))
            server.start()
            try {
                withTimeout(10000) {
                    GreetingPipeline.generate(MatchFixtures.cases[0].input, complete = {
                        DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", it)
                    })
                }
                fail("被截断的响应不能生成文案")
            } catch (e: IllegalStateException) {
                assertTrue(e.message.orEmpty().contains("截断"))
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test fun cancellingAnInFlightHttpRequestDoesNotWaitForTheServer() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            var delivered = false
            val job = launch {
                DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-only-key", JSONObject())
                delivered = true
            }
            try {
                withTimeout(5000) {
                    val request = withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) }
                    assertNotNull(request)
                    job.cancelAndJoin()
                }
                assertFalse(delivered)
                assertEquals(1, server.requestCount)
            } finally {
                job.cancelAndJoin()
            }
        }
    }
}
