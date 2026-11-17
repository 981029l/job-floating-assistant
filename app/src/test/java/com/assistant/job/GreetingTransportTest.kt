package com.assistant.job

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class GreetingTransportTest {
    private fun completion(content: String, reason: String = "stop") = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("finish_reason", reason).put("message", JSONObject().put("content", content)))).toString()

    @Test fun actualHttpRequestsCarryTheValidatedMatchIntoTheSecondCall() = runBlocking {
        val case = MatchFixtures.cases[0]
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(completion(case.response)))
            server.enqueue(MockResponse().setBody(completion("===风格一===\n您好，做过订单页面开发。\n===风格二===\n您好，有长列表优化经验。")))
            server.start()
            val output = withTimeout(10000) {
                GreetingPipeline.generate(case.input, complete = {
                    DeepSeekClient.completeGreetingRequest(server.url("/chat/completions").toString(), "test-only-key", it)
                })
            }
            assertTrue(output.contains("订单页面"))
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val request = JSONObject(server.takeRequest().body.readUtf8())
            val second = JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"))
            assertEquals(case.input.jdText, second.getString("job_description"))
            assertTrue(second.getJSONObject("job_match").toString().contains(case.selectedEvidence))
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
