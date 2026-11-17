package com.assistant.job

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GreetingTransportTest {
    private fun completion(content: String, reason: String = "stop") = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("finish_reason", reason).put("message", JSONObject().put("content", content)))).toString()

    @Test fun actualHttpRequestsCarryTheValidatedMatchIntoTheSecondCall() = runBlocking {
        val case = MatchFixtures.cases[0]
        val requests = mutableListOf<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            exchange.use {
                requests.add(JSONObject(it.requestBody.bufferedReader(Charsets.UTF_8).readText()))
                val content = if (requests.size == 1) case.response else "===风格一===\n您好，做过订单页面开发。\n===风格二===\n您好，有长列表优化经验。"
                val bytes = completion(content).toByteArray(Charsets.UTF_8)
                it.sendResponseHeaders(200, bytes.size.toLong())
                it.responseBody.write(bytes)
            }
        }
        server.start()
        try {
            val output = withTimeout(10000) {
                GreetingPipeline.generate(case.input, complete = {
                    DeepSeekClient.completeGreetingRequest("http://127.0.0.1:${server.address.port}/chat/completions", "test-only-key", it)
                })
            }
            assertTrue(output.contains("订单页面"))
            assertEquals(2, requests.size)
            val second = JSONObject(requests[1].getJSONArray("messages").getJSONObject(1).getString("content"))
            assertEquals(case.input.jdText, second.getString("job_description"))
            assertTrue(second.getJSONObject("job_match").toString().contains(case.selectedEvidence))
        } finally {
            server.stop(0)
        }
    }

    @Test fun truncatedModelOutputStopsBeforeWriting() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var calls = 0
        server.createContext("/") { exchange ->
            exchange.use {
                calls++
                it.requestBody.close()
                val bytes = completion(MatchFixtures.cases[0].response, "length").toByteArray(Charsets.UTF_8)
                it.sendResponseHeaders(200, bytes.size.toLong())
                it.responseBody.write(bytes)
            }
        }
        server.start()
        try {
            try {
                withTimeout(10000) {
                    GreetingPipeline.generate(MatchFixtures.cases[0].input, complete = {
                        DeepSeekClient.completeGreetingRequest("http://127.0.0.1:${server.address.port}/", "test-only-key", it)
                    })
                }
                fail("被截断的响应不能生成文案")
            } catch (e: IllegalStateException) {
                assertTrue(e.message.orEmpty().contains("截断"))
                assertEquals(1, calls)
            }
        } finally {
            server.stop(0)
        }
    }

    @Test fun cancellingAnInFlightHttpRequestDoesNotWaitForTheServer() = runBlocking {
        val arrived = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.use {
                arrived.complete(Unit)
                release.await(10, TimeUnit.SECONDS)
            }
        }
        server.start()
        var delivered = false
        val job = launch {
            DeepSeekClient.completeGreetingRequest("http://127.0.0.1:${server.address.port}/", "test-only-key", JSONObject())
            delivered = true
        }
        try {
            withTimeout(5000) {
                arrived.await()
                job.cancelAndJoin()
            }
            assertFalse(delivered)
            assertEquals(1L, release.count)
        } finally {
            job.cancelAndJoin()
            release.countDown()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
