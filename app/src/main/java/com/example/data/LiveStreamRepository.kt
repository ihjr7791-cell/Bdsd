package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.utils.FuzzyMatcher
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LiveStreamRepository(
    private val dao: LiveStreamDao,
    private val xtreamService: XtreamApiService,
    private val geminiService: GeminiApiService
) {
    companion object {
        const val TAG = "LiveStreamRepository"
    }

    // Server parameters
    private val username = "V62qpTrr325"
    private val password = "3v1nyYOrfb"

    /**
     * Cache streams from Xtream Codes server inside local DB if empty or forced.
     */
    suspend fun syncChannels(force: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = dao.getStreamCount()
            if (count > 0 && !force) {
                return@withContext Result.success(count)
            }

            Log.d(TAG, "Fetching live streams from Xtream Codes IPTV server...")
            val response = xtreamService.getLiveStreams(username = username, password = password)
            Log.d(TAG, "Successfully fetched ${response.size} live streams from Xtream server.")

            if (response.isNotEmpty()) {
                dao.clearAll()
                dao.insertAll(response)
                Result.success(response.size)
            } else {
                Result.failure(Exception("سيرفر IPTV لم يرجع أي قنوات. يرجى التأكد من صلاحية الاشتراك."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing channels from Xtream API", e)
            Result.failure(e)
        }
    }

    /**
     * Identify channel names from a user prompt using Gemini 3.5 Flash + Search Grounding
     */
    suspend fun findChannelsWithAI(prompt: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(Exception("مفتاح API الخاص بـ Gemini غير مهيأ. يرجى إضافته عبر لوحة الأسرار (Secrets Panel) في AI Studio."))
            }

            val systemInstructionText = """
                You are a sports match IPTV channel locator.
                Your task is to identify live sports matches from the user's transcript (usually in Arabic), search Google (via search grounding) for today's/current broadcast schedule, find the official Arabic and international channels that are broadcasting this match right now, and return a clean JSON array of strings listing these channels (for example, ["beIN Sports 1", "beIN Sports HD 2", "SSC 1 HD", "SSC Extra 1", "SSC 5", "AD Sports 1 Premium", "Alkass 1 HD"]).
                ONLY return the JSON list of matching channels, with no conversational preamble, markdown headers, or other text.
            """.trimIndent()

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = "The user says: \"$prompt\". Find channels showing this match live right now.")))
                ),
                tools = listOf(GeminiTool(googleSearchRetrieval = GoogleSearchRetrieval())),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstructionText))),
                generationConfig = GeminiConfig(
                    responseMimeType = "application/json",
                    responseSchema = GeminiSchema(
                        type = "ARRAY",
                        items = GeminiSchema(type = "STRING")
                    )
                )
            )

            val response = geminiService.generateContent(apiKey = apiKey, request = request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("لم يرجع الذكاء الاصطناعي أي استجابة قنوات."))

            Log.d(TAG, "Gemini Response Text: $jsonText")

            // Parse response array safely
            val listType = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = NetworkClient.getMoshi().adapter<List<String>>(listType)
            val channelRecommendations = adapter.fromJson(jsonText)

            if (!channelRecommendations.isNullOrEmpty()) {
                Result.success(channelRecommendations)
            } else {
                Result.failure(Exception("لم يجد الذكاء الاصطناعي أي قنوات ناقلة للمباراة المذكورة."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in match search via Gemini", e)
            Result.failure(e)
        }
    }

    /**
     * Map recommended channels with cached channels using Fuzzy String Matching
     */
    suspend fun getMatchedLiveStreams(recommendedChannels: List<String>): List<MatchedStream> = withContext(Dispatchers.IO) {
        val allCached = dao.getAllStreams()
        if (allCached.isEmpty()) return@withContext emptyList()

        val matchedList = mutableListOf<MatchedStream>()

        for (rec in recommendedChannels) {
            for (cached in allCached) {
                val score = FuzzyMatcher.computeMatchScore(rec, cached.name)
                // Threshold 0.40 - let's capture potential matches reasonably
                if (score >= 0.40) {
                    matchedList.add(MatchedStream(stream = cached, queryChannel = rec, matchScore = score))
                }
            }
        }

        // De-duplicate IPTV channels to only keep the highest match score if matched multiple times
        val uniqueMatches = matchedList.groupBy { it.stream.streamId }
            .map { (_, group) -> group.maxByOrNull { it.matchScore }!! }
            .sortedByDescending { it.matchScore }

        return@withContext uniqueMatches
    }

    /**
     * Construct stream URL for Xtream live channel
     */
    fun buildStreamUrl(streamId: Int): String {
        return "http://hynour.com:80/live/$username/$password/$streamId.ts"
    }

    /**
     * Direct query to fuzzy search local cache by manual name if AI fails or user types manually
     */
    suspend fun manualSearch(query: String): List<MatchedStream> = withContext(Dispatchers.IO) {
        val allCached = dao.getAllStreams()
        val matchedList = mutableListOf<MatchedStream>()

        for (cached in allCached) {
            val score = FuzzyMatcher.computeMatchScore(query, cached.name)
            if (score >= 0.45) {
                matchedList.add(MatchedStream(stream = cached, queryChannel = query, matchScore = score))
            }
        }

        return@withContext matchedList.sortedByDescending { it.matchScore }
    }
}

data class MatchedStream(
    val stream: LiveStreamEntity,
    val queryChannel: String,
    val matchScore: Double
)
