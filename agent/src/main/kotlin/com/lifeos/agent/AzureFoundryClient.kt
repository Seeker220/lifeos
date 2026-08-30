package com.lifeos.agent

import com.lifeos.core.LifeOsLog
import com.lifeos.core.LlmClient
import com.lifeos.core.model.LlmConfig
import com.lifeos.core.model.LlmRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AzureFoundryClient(
    private val config: LlmConfig,
    private val http: OkHttpClient = defaultClient(),
) : LlmClient {

    override suspend fun complete(req: LlmRequest): Result<String> {
        if (!config.usable) {
            return Result.failure(IllegalStateException("LLM unconfigured"))
        }
        return withContext(Dispatchers.IO) {
            runCatching { requestWithLadder(req) }.fold(
                onSuccess = { it },
                onFailure = { Result.failure(it) },
            )
        }
    }

    private suspend fun requestWithLadder(req: LlmRequest): Result<String> {
        val url = chatCompletionsUrl(config.endpoint, config.deployment, config.apiVersion)
        var useMaxCompletionTokens = false
        var includeTemperature = true
        var includeResponseFormat = true
        var rateLimitedOnce = false
        var remainingRetries = 3

        while (true) {
            val body = requestBody(
                req,
                config.deployment,
                useMaxCompletionTokens,
                includeTemperature,
                includeResponseFormat,
            )
            LifeOsLog.d(
                TAG,
                "llm POST attempt remainingRetries=$remainingRetries " +
                    "maxCompletion=$useMaxCompletionTokens temp=$includeTemperature format=$includeResponseFormat " +
                    "bodyLen=${body.length}",
            )
            val response = execute(url, body)
            val status = response.code
            val respBody = response.body
            LifeOsLog.d(TAG, "llm status=$status bodyLen=${respBody.length}")

            if (status == 401 || status == 403) {
                return Result.failure(IllegalStateException("LLM auth rejected"))
            }

            if (status in 200..299) {
                val content = extractContent(respBody)
                LifeOsLog.d(TAG, "llm content=${content?.take(700)}")
                return if (content.isNullOrBlank()) {
                    Result.failure(IllegalStateException("LLM empty content"))
                } else {
                    Result.success(content)
                }
            }

            if (status == 429 && !rateLimitedOnce && remainingRetries > 0) {
                LifeOsLog.d(TAG, "llm downgrade: 429 backoff")
                rateLimitedOnce = true
                remainingRetries--
                delay(1_500)
                continue
            }

            if (status == 400 && remainingRetries > 0) {
                val mention = respBody.lowercase()
                when {
                    !useMaxCompletionTokens && mention.contains("max_tokens") -> {
                        LifeOsLog.d(TAG, "llm downgrade: max_completion_tokens")
                        useMaxCompletionTokens = true
                        remainingRetries--
                        continue
                    }
                    includeTemperature && mention.contains("temperature") -> {
                        LifeOsLog.d(TAG, "llm downgrade: drop temperature")
                        includeTemperature = false
                        remainingRetries--
                        continue
                    }
                    includeResponseFormat && mention.contains("response_format") -> {
                        LifeOsLog.d(TAG, "llm downgrade: drop response_format")
                        includeResponseFormat = false
                        remainingRetries--
                        continue
                    }
                }
            }

            return Result.failure(IllegalStateException("LLM http $status"))
        }
    }

    private fun execute(url: String, json: String): HttpResult {
        val request = Request.Builder()
            .url(url)
            .addHeader("api-key", config.apiKey)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(json.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    private data class HttpResult(val code: Int, val body: String)

    companion object {
        private const val TAG = "LifeOS/Agent"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // Reasoning models bill hidden reasoning against the completion budget, so a
        // 1400-token cap can be spent before any JSON is emitted.
        private const val MIN_TOKEN_BUDGET = 4000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()

        internal fun chatCompletionsUrl(endpoint: String, deployment: String, apiVersion: String): String {
            val trimmed = endpoint.trim().trimEnd('/')
            val version = apiVersion.ifBlank { "2024-10-21" }
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.contains("/openai/v1") -> "$trimmed/chat/completions?api-version=$version"
                // OpenAI-compatible gateways (LiteLLM, vLLM, OpenAI itself) take the
                // model in the body and know nothing about api-version.
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/openai/deployments/$deployment/chat/completions?api-version=$version"
            }
        }

        internal fun requestBody(
            req: LlmRequest,
            model: String,
            useMaxCompletionTokens: Boolean,
            includeTemperature: Boolean,
            includeResponseFormat: Boolean,
        ): String {
            val messages = JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "role" to JsonPrimitive("system"),
                            "content" to JsonPrimitive(req.systemPrompt),
                        ),
                    ),
                    JsonObject(
                        mapOf(
                            "role" to JsonPrimitive("user"),
                            "content" to JsonPrimitive(req.userPrompt),
                        ),
                    ),
                ),
            )
            val fields = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
                "model" to JsonPrimitive(model),
                "messages" to messages,
            )
            if (includeResponseFormat) {
                fields["response_format"] = JsonObject(mapOf("type" to JsonPrimitive("json_object")))
            }
            if (includeTemperature) {
                fields["temperature"] = JsonPrimitive(req.temperature)
            }
            val budget = maxOf(req.maxTokens, MIN_TOKEN_BUDGET)
            if (useMaxCompletionTokens) {
                fields["max_completion_tokens"] = JsonPrimitive(budget)
            } else {
                fields["max_tokens"] = JsonPrimitive(budget)
            }
            return JsonObject(fields).toString()
        }

        internal fun extractContent(body: String): String? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            val first = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
            val content = first["message"]?.jsonObject?.get("content") ?: return null
            return when (content) {
                is JsonNull -> null
                is JsonPrimitive -> content.contentOrNull?.takeIf { it.isNotBlank() }
                else -> content.toString().takeIf { it.isNotBlank() }
            }
        }
    }
}
