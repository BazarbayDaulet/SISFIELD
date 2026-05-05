package com.example.finalfa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
    private var imageCapture: ImageCapture? = null

    // Переключение только между RU и EN
    private var currentLanguage = "ru"

    private val contextHistory = mutableListOf<String>()
    private var totalWordsInMemory = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyBW7q5A91xPRTBV4JPtRvv2zpuemt0-I9k"
        )

        tts = TextToSpeech(this, this)

        try {
            val serviceIntent = Intent(this, VoiceActivationService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("SERVICE_ERROR", "Wake-word service failed")
        }

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10)
        }

        binding.btnAnalyze.setOnClickListener { startCaptureProcess() }

        binding.btnSimulateVoice.setOnClickListener {
            val text = binding.etDebugVoice.text.toString()
            if (text.isNotEmpty()) {
                processVoiceCommand(text)
                binding.etDebugVoice.text.clear()
            }
        }

        binding.btnOpenProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Логика переключения RU <-> EN
        binding.btnSwitchLang.setOnClickListener {
            currentLanguage = if (currentLanguage == "ru") "en" else "ru"
            binding.btnSwitchLang.text = if(currentLanguage == "ru") "RU | EN" else "EN | RU"
            speak(if(currentLanguage == "ru") "Русский язык" else "English language")
        }
    }

    private fun startCaptureProcess() {
        speak(if(currentLanguage == "ru") "Включаю камеру" else "Starting camera")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
                takePhoto(cameraProvider)
            } catch (e: Exception) { speak("Error") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto(cameraProvider: ProcessCameraProvider) {
        imageCapture?.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()
                cameraProvider.unbindAll()
                speak(if(currentLanguage == "ru") "Анализирую" else "Analyzing")
                analyzeBitmap(bitmap, null)
            }
            override fun onError(e: ImageCaptureException) { cameraProvider.unbindAll() }
        })
    }

    private fun analyzeBitmap(bitmap: Bitmap, customUserQuestion: String?) {
        val historyText = contextHistory.takeLast(5).joinToString(". ")
        val prompt = if (customUserQuestion == null) {
            "Context: $historyText. Describe briefly in $currentLanguage what is in front of you."
        } else {
            "Context: $historyText. Answer the question: '$customUserQuestion' in $currentLanguage."
        }

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(content {
                    image(bitmap)
                    text(prompt)
                })
                val result = response.text ?: ""
                updateMemory(result)
                speak(result)
            } catch (e: Exception) { speak(if(currentLanguage=="ru") "Ошибка" else "Error") }
        }
    }

    private fun processVoiceCommand(command: String) {
        if (command.lowercase().contains("vision voice") || command.lowercase().contains("сканируй") || command.lowercase().contains("scan")) {
            startCaptureProcess()
        } else {
            startCaptureProcessWithQuestion(command)
        }
    }

    private fun startCaptureProcessWithQuestion(question: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)

            imageCapture?.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    cameraProvider.unbindAll()
                    analyzeBitmap(bitmap, question)
                }
                override fun onError(e: ImageCaptureException) { cameraProvider.unbindAll() }
            })
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun updateMemory(newText: String) {
        contextHistory.add(newText)
        totalWordsInMemory += newText.split(" ").size
        while (totalWordsInMemory > 150 && contextHistory.isNotEmpty()) {
            val removed = contextHistory.removeAt(0)
            totalWordsInMemory -= removed.split(" ").size
        }
        binding.tvContextMemory.text = "Память: $totalWordsInMemory слов"
    }

    private fun speak(text: String) {
        val locale = if (currentLanguage == "ru") Locale("ru", "RU") else Locale.US
        tts?.setLanguage(locale)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("ru", "RU"))
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra("auto_analyze", false)) startCaptureProcess()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}