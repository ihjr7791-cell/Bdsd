package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
class GoogleSearchRetrieval

@JsonClass(generateAdapter = true)
data class GeminiTool(
    @Json(name = "googleSearchRetrieval") val googleSearchRetrieval: GoogleSearchRetrieval? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSchema(
    @Json(name = "type") val type: String,
    @Json(name = "items") val items: GeminiSchema? = null,
    @Json(name = "properties") val properties: Map<String, GeminiSchema>? = null,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiConfig(
    @Json(name = "responseMimeType") val responseMimeType: String? = "application/json",
    @Json(name = "responseSchema") val responseSchema: GeminiSchema? = null,
    @Json(name = "temperature") val temperature: Float? = 0.2f
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "tools") val tools: List<GeminiTool>? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiConfig? = null,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
