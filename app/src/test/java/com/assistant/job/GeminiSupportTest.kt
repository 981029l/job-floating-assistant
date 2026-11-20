package com.assistant.job

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiSupportTest {
    private fun stream(text: String): MockResponse {
        val delta = JSONObject().put("choices", JSONArray().put(JSONObject().put("index", 0)
            .put("delta", JSONObject().put("role", "assistant").put("content", text)).put("finish_reason", JSONObject.NULL)))
        val end = JSONObject().put("choices", JSONArray().put(JSONObject().put("index", 0)
            .put("delta", JSONObject()).put("finish_reason", "stop")))
        return MockResponse().setHeader("Content-Type", "text/event-stream; charset=utf-8")
            .setBody("data: $delta\n\ndata: $end\n\ndata: [DONE]\n\n")
    }

    @Test fun officialRootAndNativeBaseBecomeCompatibleEndpoints() {
        for (suffix in listOf("", "/", "/v1beta", "/v1beta/", "/v1beta/openai/", "/v1beta/openai/models", "/v1beta/openai/chat/completions")) {
            val base = AiEndpointConfig.baseUrl("  https://generativelanguage.googleapis.com$suffix  ")
            assertEquals(AiEndpointConfig.GEMINI_BASE_URL, base)
            assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "$base/chat/completions")
        }
        assertEquals("gemini-custom", AiEndpointConfig.model(" models/gemini-custom ", AiEndpointConfig.GEMINI_BASE_URL))
        assertEquals(AiEndpointConfig.GEMINI_BASE_URL, AiEndpointConfig.presets.single { it.first == "Gemini 官方" }.second)
    }

    @Test fun customGatewaysKeepTheirOwnPathsAndModelNames() {
        for (base in listOf("https://api.deepseek.com/v1", "https://proxy.example.com/gemini/v1", "https://generativelanguage.googleapis.com.example.org/v1beta")) {
            assertEquals(base, AiEndpointConfig.baseUrl(" $base/ "))
            assertFalse(AiEndpointConfig.isGeminiOfficial(base))
            assertEquals("models/custom", AiEndpointConfig.model("models/custom", base))
        }
    }

    @Test fun modelListUsesOnlyActualIdsAndNormalizesOfficialModelPrefixes() {
        val data = JSONObject("""{"data":[{"id":"models/gemini-example"},{"id":"gemini-example"},{"id":"gemini-other"},{"id":null},{"id":" "},{}]}""")
        assertEquals(listOf("gemini-example", "gemini-other"), AiEndpointConfig.models(data, AiEndpointConfig.GEMINI_BASE_URL))
        for (raw in listOf("{}", "{\"data\":[]}", "{\"models\":[{\"name\":\"gemini-native-api\"}]}")) {
            try {
                AiEndpointConfig.models(JSONObject(raw), AiEndpointConfig.GEMINI_BASE_URL)
                fail("不能伪造可用模型")
            } catch (e: IllegalStateException) {
                assertTrue(e.message.orEmpty().contains("未返回可用模型"))
            }
        }
    }

    @Test fun officialGeminiUsesDefaultSamplingAndKeepsSourceMessages() {
        val input = MatchFixtures.cases[0].input.copy(model = "models/" + AiEndpointConfig.GEMINI_DEFAULT_MODEL)
        val original = GreetingRequestBuilder.match(input)
        val adapted = AiEndpointConfig.completionBody(original, AiEndpointConfig.GEMINI_BASE_URL + "/chat/completions")
        assertEquals(AiEndpointConfig.GEMINI_DEFAULT_MODEL, adapted.getString("model"))
        assertFalse(adapted.has("temperature"))
        assertTrue(adapted.getBoolean("stream"))
        assertEquals(original.getJSONArray("messages").toString(), adapted.getJSONArray("messages").toString())
        assertTrue(original.has("temperature"))
        assertEquals(0.3, AiEndpointConfig.completionBody(original, "https://proxy.example.com/v1/chat/completions").getDouble("temperature"), 0.001)
    }

    @Test fun compatibleModelListUsesBearerKeyAndCorrectRoute() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"gemini-from-server","object":"model","owned_by":"google"}]}"""))
            server.start()
            assertEquals(listOf("gemini-from-server"), DeepSeekClient.fetchModels(server.url("/v1beta/openai/models").toString(), " test-gemini-key "))
            val request = server.takeRequest()
            assertEquals("/v1beta/openai/models", request.path)
            assertEquals("Bearer test-gemini-key", request.getHeader("Authorization"))
            assertEquals("GET", request.method)
        }
    }

    @Test fun geminiRunsBothStagesWithTheFullSourcesAndValidatedMatch() = runBlocking {
        val fixture = MatchFixtures.cases[0]
        val input = fixture.input.copy(model = AiEndpointConfig.GEMINI_DEFAULT_MODEL)
        MockWebServer().use { server ->
            server.enqueue(stream(fixture.response))
            server.enqueue(stream("===风格一===\n您好，有订单页面开发经验。\n===风格二===\n您好，做过长列表优化。"))
            server.start()
            val result = withTimeout(5000) {
                GreetingPipeline.generate(input, complete = { body ->
                    DeepSeekClient.completeGreetingRequest(server.url("/v1beta/openai/chat/completions").toString(), "test-gemini-key", body)
                })
            }
            assertTrue(result.contains("订单页面"))
            repeat(2) { stage ->
                val request = server.takeRequest()
                assertEquals("/v1beta/openai/chat/completions", request.path)
                assertEquals("Bearer test-gemini-key", request.getHeader("Authorization"))
                val json = JSONObject(request.body.readUtf8())
                assertEquals(input.model, json.getString("model"))
                assertTrue(json.getBoolean("stream"))
                val messages = json.getJSONArray("messages")
                assertEquals("system", messages.getJSONObject(0).getString("role"))
                val sources = JSONObject(messages.getJSONObject(1).getString("content"))
                assertEquals(input.jdText, sources.getString("job_description"))
                assertEquals(input.resume, sources.getJSONObject("candidate").getString("resume"))
                if (stage == 1) assertTrue(sources.getJSONObject("job_match").toString().contains(fixture.selectedEvidence))
            }
        }
    }

    @Test fun nonGreetingRequestsAlsoUseStreamingWithoutChangingTheirMessages() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(stream("{\"targetRole\":\"前端开发\"}"))
            server.start()
            val body = JSONObject().put("model", AiEndpointConfig.GEMINI_DEFAULT_MODEL)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "完整简历输入")))
            val output = withTimeout(5000) {
                DeepSeekClient.completeGreetingRequest(server.url("/v1beta/openai/chat/completions").toString(), "test-gemini-key", body)
            }
            assertEquals("前端开发", JSONObject(output).getString("targetRole"))
            val sent = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(sent.getBoolean("stream"))
            assertEquals(body.getJSONArray("messages").toString(), sent.getJSONArray("messages").toString())
            assertFalse(body.has("stream"))
        }
    }

    @Test fun googleQuotaErrorRetainsStatusAndHidesKey() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded for test-gemini-key"}}"""))
            server.start()
            val trace = GenerationDiagnostics(MatchFixtures.cases[0].input.copy(model = AiEndpointConfig.GEMINI_DEFAULT_MODEL),
                AiEndpointConfig.GEMINI_BASE_URL + "/chat/completions", "test", "test", "test", "test-gemini-key")
            try {
                DeepSeekClient.completeGreetingRequest(server.url("/").toString(), "test-gemini-key", JSONObject(), trace)
                fail("配额不足必须报错")
            } catch (e: IllegalStateException) {
                val report = trace.failure(e).report
                assertTrue(report.contains("HTTP状态：429"))
                assertTrue(report.contains("status=RESOURCE_EXHAUSTED"))
                assertFalse(report.contains("test-gemini-key"))
                assertEquals(1, server.requestCount)
            }
        }
    }
}
