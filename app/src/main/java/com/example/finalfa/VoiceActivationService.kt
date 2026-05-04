package com.example.finalfa

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class VoiceActivationService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val keyword = "vision voice" // Твое ключевое слово

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initSpeechRecognizer()
    }

    private fun startForegroundService() {
        val channelId = "voice_service_channel"
        val channel = NotificationChannel(channelId, "Voice Activation", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VisionVoice запущен")
            .setContentText("Я слушаю команду...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (match in matches) {
                        if (match.lowercase().contains(keyword)) {
                            // КЛЮЧЕВОЕ СЛОВО НАЙДЕНО! Будим приложение
                            wakeUpApp()
                        }
                    }
                }
                // Начинаем слушать снова
                speechRecognizer?.startListening(intent)
            }

            override fun onError(error: Int) {
                // Если ошибка (тишина), пробуем заново через секунду
                speechRecognizer?.startListening(intent)
            }

            // Остальные методы (пустые)
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun wakeUpApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("auto_analyze", true)
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}