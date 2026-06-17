package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.SpeechToTextManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SportsMatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val TAG = "SportsMatchViewModel"
    }

    // Initialize Room & Repository
    private val database = AppDatabase.getDatabase(application)
    private val repository = LiveStreamRepository(
        dao = database.liveStreamDao(),
        xtreamService = NetworkClient.xtreamService,
        geminiService = NetworkClient.geminiService
    )

    // STT Manager
    val sttManager = SpeechToTextManager(application)

    // UI States
    private val _syncState = MutableStateFlow<IptvSyncState>(IptvSyncState.Idle)
    val syncState: StateFlow<IptvSyncState> = _syncState.asStateFlow()

    private val _searchState = MutableStateFlow<MatchSearchState>(MatchSearchState.Idle)
    val searchState: StateFlow<MatchSearchState> = _searchState.asStateFlow()

    private val _playingStream = MutableStateFlow<LiveStreamEntity?>(null)
    val playingStream: StateFlow<LiveStreamEntity?> = _playingStream.asStateFlow()

    // Hold current transcription as backup & manual text search input
    private val _manualInputText = MutableStateFlow("")
    val manualInputText: StateFlow<String> = _manualInputText.asStateFlow()

    init {
        // Automatically sync & load IPTV channels on first startup
        syncIptvChannels()
        observeSttResults()
    }

    /**
     * Start/Stop STT Recording
     */
    fun toggleRecording() {
        if (sttManager.isListening.value) {
            sttManager.stopListening()
        } else {
            _playingStream.value = null // Stop playing while speaking a new match
            _searchState.value = MatchSearchState.Listening
            sttManager.startListening()
        }
    }

    /**
     * Observe speech recognizer hasil results
     */
    private fun observeSttResults() {
        viewModelScope.launch {
            sttManager.recognizedText.collect { text ->
                if (text.isNotEmpty() && !sttManager.isListening.value) {
                    processVoiceTranscript(text)
                }
            }
        }
        viewModelScope.launch {
            sttManager.errorMsg.collect { error ->
                if (error != null) {
                    _searchState.value = MatchSearchState.Error(error)
                }
            }
        }
    }

    fun updateManualInputText(text: String) {
        _manualInputText.value = text
    }

    /**
     * Trigger manual typed matching if user types and hits enter
     */
    fun performManualSearch() {
        val query = _manualInputText.value.trim()
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            _playingStream.value = null
            _searchState.value = MatchSearchState.MatchingStreams
            try {
                val matches = repository.manualSearch(query)
                if (matches.isNotEmpty()) {
                    _searchState.value = MatchSearchState.FoundMatches(matches)
                } else {
                    _searchState.value = MatchSearchState.NoMatchFound(
                        recChannels = emptyList(),
                        explanation = "لم يتم العثور على أي قنوات مطابقة لكلمة البحث '$query' في اشتراكك الحالي."
                    )
                }
            } catch (e: Exception) {
                _searchState.value = MatchSearchState.Error("خطأ في البحث اليدوي: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Sync IPTV streams from server
     */
    fun syncIptvChannels(force: Boolean = false) {
        _syncState.value = IptvSyncState.Syncing
        viewModelScope.launch {
            val result = repository.syncChannels(force)
            result.onSuccess { count ->
                _syncState.value = IptvSyncState.Loaded(count)
                Log.d(TAG, "Cached $count streams from IPTV server.")
            }.onFailure { exception ->
                _syncState.value = IptvSyncState.Error(exception.localizedMessage ?: "فشل تحميل القنوات من السيرفر")
            }
        }
    }

    /**
     * Process Voice input via Gemini and then Match details
     */
    fun processVoiceTranscript(transcript: String) {
        _manualInputText.value = transcript
        _searchState.value = MatchSearchState.AnalyzingMatch(transcript)

        viewModelScope.launch {
            // Step 1: Query Gemini AI for broadcasting channels
            val aiResult = repository.findChannelsWithAI(transcript)
            aiResult.onSuccess { aiChannels ->
                Log.d(TAG, "AI Recommended channels: $aiChannels")
                _searchState.value = MatchSearchState.MatchingStreams

                // Step 2: Compare against local iptv catalog using Fuzzy String Matcher
                val matchedStreams = repository.getMatchedLiveStreams(aiChannels)
                if (matchedStreams.isNotEmpty()) {
                    _searchState.value = MatchSearchState.FoundMatches(matchedStreams)
                } else {
                    val formattedAiChannels = aiChannels.joinToString(", ")
                    _searchState.value = MatchSearchState.NoMatchFound(
                        recChannels = aiChannels,
                        explanation = "البث منقول على [$formattedAiChannels]، لكن هذه القنوات غير متوفرة في اشتراكك IPTV الحالي."
                    )
                }
            }.onFailure { exception ->
                _searchState.value = MatchSearchState.Error(
                    exception.localizedMessage ?: "حدث خطأ غير معروف أثناء تحليل المباراة عبر الذكاء الاصطناعي."
                )
            }
        }
    }

    /**
     * Play selected channel
     */
    fun playStream(stream: LiveStreamEntity) {
        _playingStream.value = stream
    }

    fun stopPlayback() {
        _playingStream.value = null
    }

    /**
     * Custom URL Constructor
     */
    fun getStreamUrl(streamId: Int): String {
        return repository.buildStreamUrl(streamId)
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.release()
    }
}

sealed interface IptvSyncState {
    object Idle : IptvSyncState
    object Syncing : IptvSyncState
    data class Loaded(val channelCount: Int) : IptvSyncState
    data class Error(val message: String) : IptvSyncState
}

sealed interface MatchSearchState {
    object Idle : MatchSearchState
    object Listening : MatchSearchState
    data class AnalyzingMatch(val voiceInput: String) : MatchSearchState
    object MatchingStreams : MatchSearchState
    data class FoundMatches(val matches: List<MatchedStream>) : MatchSearchState
    data class NoMatchFound(val recChannels: List<String>, val explanation: String) : MatchSearchState
    data class Error(val message: String) : MatchSearchState
}
