package com.example.finalfa

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val user = FirebaseAuth.getInstance().currentUser
        val db = FirebaseFirestore.getInstance()

        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val tvRole = findViewById<TextView>(R.id.tvProfileRole)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvEmail.text = user?.email ?: "No Email"

        // Подгружаем роль из Firestore
        user?.uid?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")
                        tvRole.text = "Role: $role"
                    }
                }
        }

        // Кнопка выхода
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            // Очищаем историю переходов, чтобы нельзя было вернуться назад
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}