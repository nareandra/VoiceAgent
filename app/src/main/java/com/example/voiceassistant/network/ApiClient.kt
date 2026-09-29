package com.example.voiceassistant.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class ApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val candidateBaseUrls: List<String> = buildList {
        add(baseUrl.trimEnd('/'))
        add("http://163.143.165.144:8000")
        add("http://10.0.2.2:8000")
        add("http://192.168.0.30:8000")
        add("http://127.0.0.1:8000")
    }.distinct()

    @Volatile
    private var verifiedBaseUrl: String? = null

    // ---------------------------------------------------------
    // Response returned by /v1/converse
    // ---------------------------------------------------------

    data class ConverseResult(
        val sessionId: String,
        val transcript: String,
        val reply: String,
        val audioBase64: String?
    )

    // ---------------------------------------------------------
    // Response returned by /v1/converse-text
    // ---------------------------------------------------------

    data class TextConverseResult(
        val sessionId: String,
        val transcript: String,
        val reply: String,
        val audioBase64: String?
    )

    // ---------------------------------------------------------
    // POST /v1/converse
    // ---------------------------------------------------------

    suspend fun converse(
        audioFile: File,
        sessionId: String? = null,
        fastTts: Boolean = true
    ): ConverseResult = withContext(Dispatchers.IO) {

        val audioRequestBody = audioFile
            .asRequestBody("audio/mp4".toMediaType())

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                audioFile.name,
                audioRequestBody
            )

        if (!sessionId.isNullOrBlank()) {
            multipartBuilder.addFormDataPart(
                "session_id",
                sessionId
            )
        }

        val requestBody = multipartBuilder.build()
        val targets = getTargetUrls("/v1/converse")
        var lastException: Exception? = null

        for (url in targets) {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("X-API-Key", apiKey)

            if (fastTts) {
                requestBuilder.addHeader("X-TTS-Mode", "native")
            }

            val request = requestBuilder.build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()

                    if (response.isSuccessful) {
                        val base = url.substringBefore("/v1/")
                        verifiedBaseUrl = base

                        val json = JSONObject(responseText)
                        val returnedSessionId = json.optString("session_id", "")
                        val transcript = json.optString("transcript", "")
                        val reply = json.optString("reply", "")
                        val audioBase64 = if (json.has("audio_base64") && !json.isNull("audio_base64")) {
                            json.optString("audio_base64", "")
                        } else null

                        return@withContext ConverseResult(
                            sessionId = returnedSessionId,
                            transcript = transcript,
                            reply = reply,
                            audioBase64 = audioBase64
                        )
                    }

                    throw RuntimeException("Backend error ${response.code}: $responseText")
                }
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: RuntimeException("Could not connect to Kalki server at any candidate address")
    }

    // ---------------------------------------------------------
    // POST /v1/converse-text
    //
    // Useful for testing the LLM + TTS without microphone.
    // ---------------------------------------------------------

    suspend fun converseText(
        text: String,
        sessionId: String? = null,
        fastTts: Boolean = true
    ): TextConverseResult = withContext(Dispatchers.IO) {

        val jsonBody = JSONObject().apply {
            put("text", text)

            if (!sessionId.isNullOrBlank()) {
                put("session_id", sessionId)
            }
        }

        val requestBody = jsonBody
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val url = "${baseUrl.trimEnd('/')}/v1/converse-text"

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-API-Key", apiKey)

        if (fastTts) {
            requestBuilder.addHeader("X-TTS-Mode", "native")
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->

            val responseText = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw RuntimeException(
                    "Backend error ${response.code}: $responseText"
                )
            }

            if (responseText.isBlank()) {
                throw RuntimeException(
                    "Backend returned an empty response"
                )
            }

            val json = JSONObject(responseText)

            val returnedSessionId =
                json.optString("session_id", "")

            val transcript =
                json.optString("transcript", "")

            val reply =
                json.optString("reply", "")

            val audioBase64 =
                if (
                    json.has("audio_base64") &&
                    !json.isNull("audio_base64")
                ) {
                    json.optString("audio_base64", "")
                        .takeIf { it.isNotBlank() }
                } else {
                    null
                }

            TextConverseResult(
                sessionId = returnedSessionId,
                transcript = transcript,
                reply = reply,
                audioBase64 = audioBase64
            )
        }
    }

    // ---------------------------------------------------------
    // POST /v1/speak
    //
    // Sends text to Fish Audio TTS through your backend.
    // Returns raw MP3 bytes.
    // ---------------------------------------------------------

    suspend fun speak(
        text: String
    ): ByteArray = withContext(Dispatchers.IO) {

        val jsonBody = JSONObject().apply {
            put("text", text)
        }

        val requestBody = jsonBody
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val url = "${baseUrl.trimEnd('/')}/v1/speak"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-API-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                val errorText =
                    response.body?.string().orEmpty()

                throw RuntimeException(
                    "TTS backend error ${response.code}: $errorText"
                )
            }

            response.body?.bytes()
                ?: throw RuntimeException(
                    "TTS backend returned empty audio"
                )
        }
    }

    // ---------------------------------------------------------
    // Convert backend audio_base64 → MP3 bytes
    // ---------------------------------------------------------

    fun decodeAudioBase64(
        audioBase64: String
    ): ByteArray {

        if (audioBase64.isBlank()) {
            throw IllegalArgumentException(
                "audio_base64 is empty"
            )
        }

        return try {
            Base64.decode(
                audioBase64,
                Base64.DEFAULT
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Could not decode audio_base64",
                e
            )
        }
    }

    // ---------------------------------------------------------
    // GET /healthz
    // ---------------------------------------------------------

    suspend fun healthCheck(): Boolean =
        withContext(Dispatchers.IO) {

            try {

                val url =
                    "${baseUrl.trimEnd('/')}/healthz"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->

                    response.isSuccessful
                }

            } catch (e: Exception) {

                false
            }
        }

    // ---------------------------------------------------------
    // GET /v1/history/{session_id}
    // ---------------------------------------------------------

    suspend fun getHistory(
        sessionId: String
    ): String = withContext(Dispatchers.IO) {

        val url =
            "${baseUrl.trimEnd('/')}/v1/history/$sessionId"

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("X-API-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->

            val responseText =
                response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw RuntimeException(
                    "History error ${response.code}: $responseText"
                )
            }

            responseText
        }
    }

    // ---------------------------------------------------------
    // DELETE /v1/history/{session_id}
    // ---------------------------------------------------------

    suspend fun clearHistory(
        sessionId: String
    ): Boolean = withContext(Dispatchers.IO) {

        val url =
            "${baseUrl.trimEnd('/')}/v1/history/$sessionId"

        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("X-API-Key", apiKey)
            .build()

        client.newCall(request).execute().use { response ->

            response.isSuccessful
        }
    }

    private fun getTargetUrls(endpoint: String): List<String> {
        val known = verifiedBaseUrl
        val endpointClean = "/" + endpoint.trimStart('/')
        if (known != null) {
            return listOf("$known$endpointClean")
        }
        return candidateBaseUrls.map { "$it$endpointClean" }
    }
}