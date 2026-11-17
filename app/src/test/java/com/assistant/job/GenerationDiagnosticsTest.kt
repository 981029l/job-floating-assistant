package com.assistant.job

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class GenerationDiagnosticsTest {
    private val input = MatchFixtures.cases[0].input
    private fun diagnostics(value: GreetingInput = input, endpoint: String = "https://api.example.com/v1/chat/completions", clock: () -> Long = System::nanoTime) =
        GenerationDiagnostics(value, endpoint, "1.4.3 (43)", "Android 14 / API 34", "连接=20000 ms；读取=45000 ms；写入=20000 ms", "test-private-key", clock)

    @Test fun timeoutReportIdentifiesStageAndActualElapsedTimeWithoutSourceText() = runBlocking {
        var now = 0L
        val trace = diagnostics(clock = { now })
        try {
            GreetingPipeline.generate(input, complete = {
                trace.request(it.toString().toByteArray().size)
                now = 45_000_000_000L
                throw SocketTimeoutException("timeout")
            }, diagnostics = trace)
            fail("预期超时")
        } catch (e: SocketTimeoutException) {
            val failure = trace.failure(e)
            assertTrue(failure.message.orEmpty().contains("超时"))
            assertTrue(failure.report.contains("1/2"))
            assertTrue(failure.report.contains("45000 ms"))
            assertTrue(failure.report.contains("HTTP状态：未收到响应头"))
            assertTrue(failure.report.contains("SocketTimeoutException"))
            assertFalse(failure.report.contains(input.resume))
            assertFalse(failure.report.contains(input.jdText))
            assertFalse(failure.report.contains(input.prompt))
        }
    }

    @Test fun secondRequestTimeoutDoesNotReuseTheFirstResponseMetadata() = runBlocking {
        val trace = diagnostics()
        var calls = 0
        try {
            GreetingPipeline.generate(input, complete = {
                trace.request(256)
                calls++
                if (calls == 1) {
                    trace.headers(200, "first-request", "application/json")
                    trace.response("{}")
                    MatchFixtures.cases[0].response
                } else throw SocketTimeoutException("timeout")
            }, diagnostics = trace)
            fail("预期超时")
        } catch (e: SocketTimeoutException) {
            val report = trace.failure(e).report
            assertTrue(report.contains("2/2"))
            assertTrue(report.contains("接口调用次数：2"))
            assertTrue(report.contains("HTTP状态：未收到响应头"))
            assertFalse(report.contains("first-request"))
        }
    }

    @Test fun missingMatchCoverageIsReportedAsValidationFailure() = runBlocking {
        val trace = diagnostics()
        try {
            GreetingPipeline.generate(input, complete = { "{\"segments\":[]}" }, diagnostics = trace)
            fail("预期校验失败")
        } catch (e: IllegalArgumentException) {
            val report = trace.failure(e).report
            assertTrue(report.contains("操作：校验岗位匹配结果"))
            assertTrue(report.contains("遗漏"))
        }
    }

    @Test fun actualHttpErrorsIncludeStatusRequestIdAndRedactedProviderMessage() = runBlocking {
        MockWebServer().use { server ->
            val message = "Quota exceeded test-private-key Bearer other-secret contact user@example.com 13812345678"
            server.enqueue(MockResponse().setResponseCode(429).setHeader("x-request-id", "req-trace-123")
                .setHeader("Content-Type", "application/json")
                .setBody(JSONObject().put("error", JSONObject().put("code", "quota_exceeded").put("message", message)).toString()))
            server.start()
            val trace = diagnostics()
            try {
                DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-private-key", JSONObject(), trace)
                fail("预期HTTP错误")
            } catch (e: IllegalStateException) {
                val report = trace.failure(e).report
                for (value in listOf("429", "req-trace-123", "quota_exceeded", "Quota exceeded")) assertTrue(report.contains(value))
                for (value in listOf("test-private-key", "other-secret", "user@example.com", "13812345678")) assertFalse(report.contains(value))
            }
        }
    }

    @Test fun urlCredentialsAndQuerySecretsAreRemovedFromEveryReportedField() {
        val url = "https://alice:password@api.example.com/v1/chat/completions?api_key=query-secret#fragment-secret"
        val trace = diagnostics(endpoint = url)
        val report = trace.failure(java.io.IOException("Failed to connect $url Authorization: hidden-secret")).report
        assertTrue(report.contains("https://api.example.com/v1/chat/completions"))
        for (secret in listOf("alice", "password@", "query-secret", "fragment-secret", "hidden-secret")) assertFalse(report.contains(secret))
    }

    @Test fun jsonParseErrorsNeverEchoRawModelResponses() {
        val report = diagnostics().failure(JSONException("Value RAW-MODEL-PRIVATE-RESPONSE cannot be converted")).report
        assertTrue(report.contains("JSONException"))
        assertFalse(report.contains("RAW-MODEL-PRIVATE-RESPONSE"))
    }

    @Test fun providerErrorsCannotEchoCompleteResumeOrJd() {
        val trace = diagnostics()
        trace.headers(400, null, "application/json")
        trace.response(JSONObject().put("error", JSONObject().put("message", input.resume + input.jdText)).toString())
        val report = trace.failure(IllegalStateException("HTTP 400")).report
        assertFalse(report.contains(input.resume))
        assertFalse(report.contains(input.jdText))
        assertTrue(report.contains("资料正文已隐藏"))
    }
}
