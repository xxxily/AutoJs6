package org.autojs.autojs.ai.client

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.ai.copilot.AiPrivacyRedactor
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.autojs.autojs.ai.config.AiProviderConfig
import org.autojs.autojs.ai.config.StructuredOutputMode
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class OpenAiCompatibleClient {

    private val gson = Gson()

    @Throws(AiClientException::class)
    fun complete(
        provider: AiProviderConfig,
        apiKey: String,
        messages: List<AiChatMessage>,
        responseSchema: JsonObject?,
        callConsumer: ((Call) -> Unit)? = null,
        streamConsumer: ((AiStreamState, String) -> Unit)? = null,
    ): AiChatCompletion {
        val body = buildRequestBody(provider, messages, responseSchema)
        val request = Request.Builder()
            .url(buildChatCompletionsUrl(provider.baseUrl))
            .post(gson.toJson(body).toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .applyHeaders(provider)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(provider.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(provider.timeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(provider.timeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(provider.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()

        val call = client.newCall(request).also { callConsumer?.invoke(it) }
        return try {
            call.execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw classifyHttpError(response.code, responseBody)
                }
                if (provider.streamEnabled) {
                    parseStream(responseBody, streamConsumer)
                } else {
                    parseJsonResponse(responseBody)
                }
            }
        } catch (e: AiClientException) {
            throw e
        } catch (e: UnknownHostException) {
            throw AiClientException(AiErrorCode.NETWORK_UNAVAILABLE, e.message ?: "Network unavailable", e)
        } catch (e: SocketTimeoutException) {
            throw AiClientException(AiErrorCode.TIMEOUT, e.message ?: "Request timeout", e)
        } catch (e: SSLException) {
            throw AiClientException(AiErrorCode.TLS_ERROR, e.message ?: "TLS error", e)
        } catch (e: IOException) {
            if (call.isCanceled()) {
                throw AiClientException(AiErrorCode.CANCELLED, "Request cancelled", e)
            }
            throw AiClientException(AiErrorCode.NETWORK_ERROR, e.message ?: "Network error", e)
        } catch (e: Exception) {
            throw AiClientException(AiErrorCode.RESPONSE_PARSE_ERROR, e.message ?: "Response parse error", e)
        }
    }

    fun testConnection(
        provider: AiProviderConfig,
        apiKey: String,
        callConsumer: ((Call) -> Unit)? = null,
    ): AiChatCompletion {
        val testProvider = provider.copy(
            streamEnabled = false,
            structuredOutputMode = StructuredOutputMode.PROMPT_JSON,
            maxOutputTokens = 16,
            temperature = 0.0,
        )
        return complete(
            provider = testProvider,
            apiKey = apiKey,
            messages = listOf(
                AiChatMessage("system", "Reply with the exact JSON object {\"ok\":true}."),
                AiChatMessage("user", "Connection test."),
            ),
            responseSchema = null,
            callConsumer = callConsumer,
        )
    }

    private fun Request.Builder.applyHeaders(provider: AiProviderConfig): Request.Builder = apply {
        if (provider.organizationId.isNotBlank()) {
            header("OpenAI-Organization", provider.organizationId.trim())
        }
        if (provider.projectId.isNotBlank()) {
            header("OpenAI-Project", provider.projectId.trim())
        }
        provider.customHeaders
            .filter { it.normalizedKey().isNotBlank() && it.value.isNotBlank() }
            .forEach { header(it.normalizedKey(), it.value) }
    }

    private fun buildRequestBody(
        provider: AiProviderConfig,
        messages: List<AiChatMessage>,
        responseSchema: JsonObject?,
    ): JsonObject = JsonObject().apply {
        addProperty("model", provider.model)
        add("messages", JsonArray().apply {
            messages.forEach { message ->
                add(JsonObject().apply {
                    addProperty("role", message.role)
                    addProperty("content", message.content)
                })
            }
        })
        addProperty("temperature", provider.temperature)
        addProperty("stream", provider.streamEnabled)
        val maxTokensKey = if (provider.useCompatibilityFallback) "max_tokens" else "max_completion_tokens"
        if (provider.maxOutputTokens > 0) {
            addProperty(maxTokensKey, provider.maxOutputTokens)
        }
        when (provider.structuredOutputMode) {
            StructuredOutputMode.JSON_SCHEMA -> {
                if (responseSchema != null) {
                    add("response_format", JsonObject().apply {
                        addProperty("type", "json_schema")
                        add("json_schema", JsonObject().apply {
                            addProperty("name", "autojs6_ai_script_result")
                            addProperty("strict", true)
                            add("schema", responseSchema)
                        })
                    })
                }
            }
            StructuredOutputMode.JSON_OBJECT -> add("response_format", JsonObject().apply {
                addProperty("type", "json_object")
            })
            StructuredOutputMode.PROMPT_JSON -> Unit
        }
    }

    private fun parseJsonResponse(raw: String): AiChatCompletion {
        val root = runCatching { gson.fromJson(raw, JsonObject::class.java) }.getOrNull()
            ?: throw AiClientException(AiErrorCode.RESPONSE_PARSE_ERROR, "Response is not valid JSON")
        val choices = root.getAsJsonArray("choices")
            ?: throw AiClientException(AiErrorCode.MISSING_CHOICES, "Response JSON missing choices")
        if (choices.size() == 0) {
            throw AiClientException(AiErrorCode.MISSING_CHOICES, "Response choices is empty")
        }
        val message = choices[0].asJsonObject.getAsJsonObject("message")
            ?: throw AiClientException(AiErrorCode.MISSING_CHOICES, "Response choice missing message")
        val refusal = message.getStringOrNull("refusal")
        if (!refusal.isNullOrBlank()) {
            throw AiClientException(AiErrorCode.MODEL_REFUSAL, refusal)
        }
        val content = message.getStringOrNull("content")
            ?: throw AiClientException(AiErrorCode.EMPTY_CONTENT, "Response message content is empty")
        return AiChatCompletion(content = content, rawResponse = raw)
    }

    private fun parseStream(raw: String, streamConsumer: ((AiStreamState, String) -> Unit)?): AiChatCompletion {
        streamConsumer?.invoke(AiStreamState.GENERATING, "")
        val out = StringBuilder()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) return@forEach
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]") return@forEach
            val json = runCatching { gson.fromJson(data, JsonObject::class.java) }.getOrNull() ?: return@forEach
            val delta = json.getAsJsonArray("choices")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("delta")
            val content = delta?.getStringOrNull("content").orEmpty()
            if (content.isNotEmpty()) {
                out.append(content)
                streamConsumer?.invoke(AiStreamState.GENERATING, out.toString())
            }
        }
        val content = out.toString()
        if (content.isBlank()) {
            throw AiClientException(AiErrorCode.EMPTY_CONTENT, "Stream produced no content")
        }
        return AiChatCompletion(content = content, rawResponse = raw)
    }

    private fun classifyHttpError(code: Int, body: String): AiClientException {
        val message = AiPrivacyRedactor.redact(extractErrorMessage(body).ifBlank { "HTTP $code" }).take(800)
        val errorCode = when (code) {
            401, 403 -> AiErrorCode.AUTH_FAILED
            404 -> AiErrorCode.NOT_FOUND
            429 -> AiErrorCode.RATE_LIMITED
            in 500..599 -> AiErrorCode.SERVER_ERROR
            else -> AiErrorCode.HTTP_ERROR
        }
        return AiClientException(errorCode, message)
    }

    private fun extractErrorMessage(body: String): String {
        val root = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull() ?: return body.take(240)
        val error = root.getAsJsonObject("error") ?: return body.take(240)
        return error.getStringOrNull("message") ?: body.take(240)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

        fun buildChatCompletionsUrl(baseUrl: String): String {
            val trimmed = baseUrl.trim().trimEnd('/')
            require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "Invalid Base URL" }
            return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
        }
    }
}

data class AiChatMessage(
    val role: String,
    val content: String,
)

data class AiChatCompletion(
    val content: String,
    val rawResponse: String,
)

enum class AiStreamState {
    WAITING,
    GENERATING,
    VALIDATING,
    READY,
    FAILED,
    CANCELLED,
}

enum class AiErrorCode {
    NOT_CONFIGURED,
    INVALID_URL,
    NETWORK_UNAVAILABLE,
    NETWORK_ERROR,
    TLS_ERROR,
    TIMEOUT,
    AUTH_FAILED,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER_ERROR,
    HTTP_ERROR,
    RESPONSE_PARSE_ERROR,
    MISSING_CHOICES,
    MODEL_REFUSAL,
    EMPTY_CONTENT,
    VALIDATION_FAILED,
    CANCELLED,
}

class AiClientException(
    val code: AiErrorCode,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

private fun JsonObject.getStringOrNull(name: String): String? {
    val value: JsonElement = get(name) ?: return null
    if (value.isJsonNull) return null
    return value.asString
}
