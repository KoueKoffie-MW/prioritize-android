package com.example.prioritize.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

object SpeechTranscriber {
    private const val TAG = "SpeechTranscriber"

    private var recognizer: SpeechRecognizer? = null
    private var isContinuous = false
    private val accumulatedText = StringBuilder()
    private var latestPartialText = ""
    private var currentIntent: Intent? = null
    
    private var onPartialResultCallback: ((String) -> Unit)? = null
    private var onFinalResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun startListening(
        context: Context,
        languageCode: String = "en-US",
        continuous: Boolean = true,
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        mainHandler.post {
            // Clean up any active session first
            cancelInternal()
            
            isContinuous = continuous
            accumulatedText.clear()
            latestPartialText = ""
            
            onPartialResultCallback = onPartialResult
            onFinalResultCallback = onFinalResult
            onErrorCallback = onError

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, languageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            }
            currentIntent = intent

            try {
                val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                newRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech Recognizer is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            else -> "Unknown error"
                        }
                        Log.w(TAG, "onError: $errorMsg ($error)")
                        
                        mainHandler.post {
                            if (isContinuous) {
                                Log.i(TAG, "Continuous mode active. Restarting listener after error: $errorMsg")
                                try {
                                    recognizer?.startListening(currentIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to restart listening after error", e)
                                }
                            } else {
                                val fallbackText = if (latestPartialText.isNotBlank()) {
                                    if (accumulatedText.isEmpty()) latestPartialText else "$accumulatedText $latestPartialText"
                                } else {
                                    accumulatedText.toString()
                                }.trim()

                                if (fallbackText.isNotBlank()) {
                                    Log.i(TAG, "onError triggered but fallback text is available. Invoking onFinalResult.")
                                    onFinalResultCallback?.invoke(fallbackText)
                                } else {
                                    onErrorCallback?.invoke(errorMsg)
                                }
                                cleanUp()
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        
                        mainHandler.post {
                            latestPartialText = ""
                            if (text.isNotBlank()) {
                                if (accumulatedText.isEmpty()) {
                                    accumulatedText.append(text)
                                } else {
                                    accumulatedText.append(" ").append(text)
                                }
                            }
                            
                            if (isContinuous) {
                                onPartialResultCallback?.invoke(accumulatedText.toString())
                                try {
                                    recognizer?.startListening(currentIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to restart listening in onResults", e)
                                }
                            } else {
                                onFinalResultCallback?.invoke(accumulatedText.toString().trim())
                                cleanUp()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull() ?: ""
                        mainHandler.post {
                            latestPartialText = partial
                            val fullPartial = if (accumulatedText.isEmpty()) partial else "${accumulatedText} $partial"
                            onPartialResultCallback?.invoke(fullPartial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                
                recognizer = newRecognizer
                newRecognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
                onError("Failed to start speech recognizer: ${e.message}")
            }
        }
    }

    fun stopListening() {
        isContinuous = false
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                recognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop listening", e)
            }
        }
    }

    fun cancel() {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            cancelInternal()
        }
    }

    private fun cancelInternal() {
        isContinuous = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        } finally {
            recognizer = null
            onPartialResultCallback = null
            onFinalResultCallback = null
            onErrorCallback = null
            currentIntent = null
        }
    }

    private fun cleanUp() {
        isContinuous = false
        try {
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanup", e)
        } finally {
            recognizer = null
            onPartialResultCallback = null
            onFinalResultCallback = null
            onErrorCallback = null
            currentIntent = null
        }
    }
}
