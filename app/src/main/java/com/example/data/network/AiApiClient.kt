package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Float = 0.7f,
    @Json(name = "max_tokens") val maxTokens: Int = 220
)

@JsonClass(generateAdapter = true)
data class ChatChoiceMessage(
    val role: String?,
    val content: String?
)

@JsonClass(generateAdapter = true)
data class ChatChoice(
    val index: Int?,
    val message: ChatChoiceMessage?
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<ChatChoice>?
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    val message: String?
)

@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    val error: ApiErrorDetail?
)

@JsonClass(generateAdapter = true)
data class ModelItemDto(
    val id: String
)

@JsonClass(generateAdapter = true)
data class ModelsListResponse(
    val data: List<ModelItemDto>?
)

sealed class AiApiResult<out T> {
    data class Success<T>(val data: T, val latencyMs: Long) : AiApiResult<T>()
    data class Error(val message: String, val httpCode: Int? = null) : AiApiResult<Nothing>()
}

class AiApiClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_RETRIES = 2

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun isValidApiKeyHeader(apiKey: String): Boolean {
            val trimmed = apiKey.trim()
            return trimmed.isNotEmpty() && trimmed.all { it in '\u0021'..'\u007E' }
        }

        fun normalizeEndpoint(baseUrl: String, path: String): String? {
            val trimmed = baseUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val parsed = trimmed.toHttpUrlOrNull() ?: return null
            if (parsed.scheme != "https" && parsed.scheme != "http") return null
            val cleanPath = path.trimStart('/')
            return "$trimmed/$cleanPath"
        }
    }

    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)
    private val errorAdapter = moshi.adapter(ApiErrorEnvelope::class.java)
    private val modelsAdapter = moshi.adapter(ModelsListResponse::class.java)

    suspend fun generateCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessageDto>,
        temperature: Float,
        maxTokens: Int
    ): AiApiResult<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AiApiResult.Error("API key is missing. Configure it in Settings.")
        }
        if (!isValidApiKeyHeader(cleanKey)) {
            return@withContext AiApiResult.Error("API key contains invalid spaces, line breaks, or non-ASCII characters.")
        }
        if (model.isBlank()) {
            return@withContext AiApiResult.Error("Model name is missing.")
        }
        val url = normalizeEndpoint(baseUrl, "chat/completions")
            ?: return@withContext AiApiResult.Error("Invalid API Base URL. Use a valid http:// or https:// URL.")

        val payload = ChatCompletionRequest(
            model = model.trim(),
            messages = messages,
            temperature = temperature.coerceIn(0f, 1.5f),
            maxTokens = maxTokens.coerceIn(16, 4096)
        )
        val jsonBody = requestAdapter.toJson(payload)

        var lastError: AiApiResult.Error = AiApiResult.Error("Request failed")
        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                delay(600L * attempt)
            }
            val startMs = System.currentTimeMillis()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $cleanKey")
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startMs
                    val bodyText = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val parsed = runCatching { responseAdapter.fromJson(bodyText) }.getOrNull()
                        val content = parsed?.choices?.firstOrNull()?.message?.content?.trim()
                        if (!content.isNullOrBlank()) {
                            return@withContext AiApiResult.Success(content, latency)
                        }
                        return@withContext AiApiResult.Error(
                            "Provider returned an empty completion response.",
                            response.code
                        )
                    } else {
                        val apiMessage = runCatching {
                            errorAdapter.fromJson(bodyText)?.error?.message
                        }.getOrNull()
                        val msg = apiMessage?.takeIf { it.isNotBlank() }
                            ?: "HTTP ${response.code}: ${response.message.ifBlank { "Request rejected" }}"
                        lastError = AiApiResult.Error(msg, response.code)
                        if (response.code != 429 && response.code < 500) {
                            return@withContext lastError
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (iae: IllegalArgumentException) {
                return@withContext AiApiResult.Error(iae.localizedMessage ?: "Invalid request configuration.")
            } catch (io: IOException) {
                lastError = AiApiResult.Error(io.localizedMessage ?: "Network communication error")
            }
        }
        lastError
    }

    suspend fun testConnection(
        baseUrl: String,
        apiKey: String,
        model: String
    ): AiApiResult<String> {
        return generateCompletion(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            messages = listOf(
                ChatMessageDto(role = "system", content = "Respond with only: OK"),
                ChatMessageDto(role = "user", content = "Connection check")
            ),
            temperature = 0f,
            maxTokens = 12
        )
    }

    suspend fun fetchAvailableModels(
        baseUrl: String,
        apiKey: String
    ): AiApiResult<List<String>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AiApiResult.Error("Enter an API key before fetching models.")
        }
        if (!isValidApiKeyHeader(cleanKey)) {
            return@withContext AiApiResult.Error("API key contains invalid spaces, line breaks, or non-ASCII characters.")
        }
        val url = normalizeEndpoint(baseUrl, "models")
            ?: return@withContext AiApiResult.Error("Invalid API Base URL.")

        val startMs = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startMs
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMessage = runCatching {
                        errorAdapter.fromJson(bodyText)?.error?.message
                    }.getOrNull()
                    return@withContext AiApiResult.Error(
                        apiMessage ?: "HTTP ${response.code} fetching models",
                        response.code
                    )
                }
                val parsed = runCatching { modelsAdapter.fromJson(bodyText) }.getOrNull()
                val ids = parsed?.data
                    ?.map { it.id }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
                AiApiResult.Success(ids, latency)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (iae: IllegalArgumentException) {
            AiApiResult.Error(iae.localizedMessage ?: "Invalid request configuration.")
        } catch (io: IOException) {
            AiApiResult.Error(io.localizedMessage ?: "Network error fetching models")
        }
    }
}
