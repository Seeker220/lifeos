package com.lifeos.agent

import com.lifeos.core.model.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzureFoundryClientTest {
    @Test
    fun v1EndpointUsesChatCompletionsPath() {
        val url = AzureFoundryClient.chatCompletionsUrl(
            "https://example.openai.azure.com/openai/v1/",
            "gpt-demo",
            "2024-10-21",
        )
        assertEquals(
            "https://example.openai.azure.com/openai/v1/chat/completions?api-version=2024-10-21",
            url,
        )
    }

    @Test
    fun classicEndpointPutsDeploymentInPath() {
        val url = AzureFoundryClient.chatCompletionsUrl(
            "https://example.openai.azure.com/",
            "gpt-demo",
            "",
        )
        assertEquals(
            "https://example.openai.azure.com/openai/deployments/gpt-demo/chat/completions?api-version=2024-10-21",
            url,
        )
    }

    @Test
    fun openAiCompatibleEndpointDropsApiVersion() {
        val url = AzureFoundryClient.chatCompletionsUrl(
            "http://20.122.222.95:4000/v1",
            "azure/gpt-5.6-sol",
            "",
        )
        assertEquals("http://20.122.222.95:4000/v1/chat/completions", url)
    }

    @Test
    fun requestBodyCarriesModelName() {
        val body = AzureFoundryClient.requestBody(
            LlmRequest("sys", "user"),
            model = "azure/gpt-5.6-sol",
            useMaxCompletionTokens = false,
            includeTemperature = true,
            includeResponseFormat = true,
        )
        assertTrue(body.contains("\"model\":\"azure/gpt-5.6-sol\""))
    }

    @Test
    fun requestBodyUsesJsonObjectAndCanDegrade() {
        val full = AzureFoundryClient.requestBody(
            LlmRequest("sys", "user"),
            model = "gpt-demo",
            useMaxCompletionTokens = false,
            includeTemperature = true,
            includeResponseFormat = true,
        )
        assertTrue(full.contains("\"type\":\"json_object\""))
        assertTrue(full.contains("max_tokens"))
        assertTrue(full.contains("temperature"))
        val degraded = AzureFoundryClient.requestBody(
            LlmRequest("sys", "user"),
            model = "gpt-demo",
            useMaxCompletionTokens = true,
            includeTemperature = false,
            includeResponseFormat = false,
        )
        assertTrue(degraded.contains("\"max_completion_tokens\""))
        assertFalse(degraded.contains("\"max_tokens\""))
        assertFalse(degraded.contains("temperature"))
        assertFalse(degraded.contains("response_format"))
    }

    @Test
    fun extractContentReadsChoicesMessage() {
        val body = """{"choices":[{"message":{"content":"{\"reply\":\"hi\",\"actions\":[]}"}}]}"""
        assertEquals("{\"reply\":\"hi\",\"actions\":[]}", AzureFoundryClient.extractContent(body))
    }
}
