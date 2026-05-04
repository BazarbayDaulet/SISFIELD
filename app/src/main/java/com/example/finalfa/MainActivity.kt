package com.example.finalfa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.finalfa.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var tts: TextToSpeech? = null
    private lateinit var generativeModel: GenerativeModel

    // Флаг языка: "en" или "kk"
    private var currentLanguage = "en"

    // Память контекста (последние ответы для "Short-term memory")
    private val contextHistory = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Инициализация Gemini ИИ
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyBW7q5A91xPRTBV4JPtRvv2zpuemt0-I9k"
        )

        // 2. Инициализация Озвучки (TTS)
        tts = TextToSpeech(this, this)

        // 3. Запуск фоновой службы (Wake-word listener)
        val serviceIntent = Intent(this, VoiceActivationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // 4. Проверка разрешений и запуск камеры
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10
            )
        }

        // Кнопка анализа
        binding.btnAnalyze.setOnClickListener {
            analyzeScene()
        }

        // Кнопка переключения языка (добавь её в макет, если хочешь)
        binding.tvAiStatus.setOnClickListener {
            currentLanguage = if (currentLanguage == "en") "kk" else "en"
            Toast.makeText(this, "Language: $currentLanguage", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ОБЪЕДИНЕННАЯ ФУНКЦИЯ АНАЛИЗА
    private fun analyzeScene() {
        val bitmap = binding.viewFinder.bitmap ?: return
        binding.btnAnalyze.isEnabled = false
        binding.tvAiStatus.text = "Analyzing ($currentLanguage)..."

        // Собираем историю для контекста (память)
        val historyText = contextHistory.takeLast(5).joinToString(". ")

        // Формируем промпт в зависимости от языка
        val prompt = if (currentLanguage == "kk") {
            "Контекст: $historyText. Алдыңда не тұрғанын қазақша қысқаша сипаттап бер. Кедергілер мен мәтінге назар аудар."
        } else {
            "Context: $historyText. Describe what's in front of the camera in English briefly. Focus on obstacles and text."
        }

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(content {
                    image(bitmap)
                    text(prompt)
                })
                val result = response.text ?: ""

                // Добавляем результат в память (sliding window)
                contextHistory.add(result)
                if (contextHistory.size > 20) contextHistory.removeAt(0)

                // Озвучиваем ответ
                speak(result)
                binding.tvAiStatus.text = "System: Ready"
            } catch (e: Exception) {
                speak("Error connecting to AI")
            } finally {
                binding.btnAnalyze.isEnabled = true
            }
        }
    }

    private fun speak(text: String) {
        // Установка языка для озвучки
        if (currentLanguage == "kk") {
            tts?.language = Locale("kk", "KZ")
        } else {
            tts?.language = Locale.US
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        // Если служба активировала приложение через Wake-word
        if (intent.getBooleanExtra("auto_analyze", false)) {
            analyzeScene()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}