package com.example.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SpeechToTextManager(private val context: Context) {
    companion object {
        const val TAG = "SpeechToTextManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val _soundLevel = MutableStateFlow(0f) // Normalized 0f - 1f for the UI wave animation
    val soundLevel: StateFlow<Float> = _soundLevel

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _errorMsg.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        _recognizedText.value = ""
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // rmsdB typically goes from -2 to 10
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _soundLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت"
                            SpeechRecognizer.ERROR_CLIENT -> "خطأ في الاتصال بالهاتف"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحيات المايكروفون غير متوفرة"
                            SpeechRecognizer.ERROR_NETWORK -> "فشل في شبكة الاتصال"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة الشبكة"
                            SpeechRecognizer.ERROR_NO_MATCH -> "لم يتم التعرف على الصوت. هل يمكنك المحاولة مرة أخرى؟"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة التعرف على الصوت مشغولة"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "لم يتم سماع أي صوت"
                            else -> "حدث خطأ غير متوقع في التعرف على الصوت"
                        }
                        Log.e(TAG, "Speech recognition error block: $error - $message")
                        _errorMsg.value = message
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _recognizedText.value = matches[0]
                        } else {
                            _errorMsg.value = "لم يتم التقاط أي نص صوتي."
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _recognizedText.value = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            _errorMsg.value = "التعرف على الصوت غير مدعوم على هذا الجهاز."
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        _errorMsg.value = null
        _recognizedText.value = ""
        _soundLevel.value = 0f

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA") // Focus on Arabic, but falls back normally
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("ar", "en"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting speech recognizer", e)
            _errorMsg.value = "فشل بدء تسجيل الصوت: ${e.message}"
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping speech recognizer", e)
        }
        _isListening.value = false
    }

    fun release() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
