package com.example.finalfa

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class VoiceActivationService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var recognizerIntent: Intent
    private lateinit var audioManager: AudioManager
    private val keyword = "vision voice" // Ключевое слово для пробуждения

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        startMyForegroundService()
        initSpeechRecognizer()
    }

    private fun startMyForegroundService() {
        val channelId = "voice_service_channel"
        val channelName = "Voice Activation Service"
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("VisionVoice активен")
            .setContentText("Слушаю команду 'Vision Voice'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun initSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            // Эти параметры помогают распознаванию быть более стабильным
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this@VoiceActivationService.packageName)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (match in matches) {
                        Log.d("VOICE_SERVICE", "Услышано: $match")
                        if (match.lowercase().contains(keyword)) {
                            wakeUpApp()
                        }
                    }
                }
                // Перезапуск после успешного распознавания
                restartListening(500)
            }

            override fun onError(error: Int) {
                // Если ошибка (тишина, сбой сети и т.д.), ждем 2 секунды перед повтором
                // Это убирает бесконечный треск на эмуляторе
                Log.e("VOICE_SERVICE", "Ошибка распознавания: $error")
                restartListening(2000)
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d("VOICE_SERVICE", "Микрофон готов")
            }

            // Остальные методы оставляем пустыми
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        startListeningWithMute()
    }

    private fun startListeningWithMute() {
        try {
            // Глушим системный звук "бип" перед включением микрофона
            audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)

            speechRecognizer?.startListening(recognizerIntent)

            // Возвращаем системный звук через полсекунды (чтобы слышать ответы ИИ)
            Handler(Looper.getMainLooper()).postDelayed({
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
            }, 500)
        } catch (e: Exception) {
            Log.e("VOICE_SERVICE", "Ошибка старта: ${e.message}")
        }
    }

    private fun restartListening(delayMillis: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            startListeningWithMute()
        }, delayMillis)
    }

    private fun wakeUpApp() {
        Log.d("VOICE_SERVICE", "Ключевое слово найдено! Пробуждаю приложение...")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("auto_analyze", true)
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Чтобы служба перезапускалась при нехватке памяти
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false) // На всякий случай включаем звук
        super.onDestroy()
    }
}