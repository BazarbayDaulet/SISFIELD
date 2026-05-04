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
    private var currentLanguage = "en"
    private val contextHistory = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyBW7q5A91xPRTBV4JPtRvv2zpuemt0-I9k"
        )

        tts = TextToSpeech(this, this)

        // Запуск службы в фоне
        val serviceIntent = Intent(this, VoiceActivationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 10)
        }

        binding.btnAnalyze.setOnClickListener { analyzeScene() }
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
                cameraProvider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera error", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeScene() {
        val bitmap = binding.viewFinder.bitmap ?: return
        binding.btnAnalyze.isEnabled = false
        binding.tvAiStatus.text = "Analyzing..."

        val historyText = contextHistory.takeLast(5).joinToString(". ")
        val prompt = if (currentLanguage == "kk") {
            "Контекст: $historyText. Алдыңда не тұрғанын қазақша сипатта."
        } else {
            "Context: $historyText. Describe what is in front of the camera in English briefly."
        }

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(content {
                    image(bitmap)
                    text(prompt)
                })
                val result = response.text ?: ""
                contextHistory.add(result)
                speak(result)
                binding.tvAiStatus.text = "System: Ready"
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "AI Error", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnAnalyze.isEnabled = true
            }
        }
    }

    private fun speak(text: String) {
        tts?.language = if (currentLanguage == "kk") Locale("kk", "KZ") else Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra("auto_analyze", false)) analyzeScene()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
    }
}