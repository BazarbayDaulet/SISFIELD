package com.example.finalfa

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class AdminActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var rvUsers: RecyclerView
    private lateinit var tvUserCount: TextView
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        rvUsers = findViewById(R.id.rvUsers)
        tvUserCount = findViewById(R.id.tvUserCount)

        rvUsers.layoutManager = LinearLayoutManager(this)

        fetchUsers()
    }

    private fun fetchUsers() {
        db.collection("users").get()
            .addOnSuccessListener { documents ->
                userList.clear()
                for (doc in documents) {
                    val user = doc.toObject(User::class.java)
                    userList.add(user)
                }
                tvUserCount.text = "Total users: ${userList.size}"
                rvUsers.adapter = UserAdapter(userList)
            }
    }

    // Внутренний класс для адаптера списка
    class UserAdapter(private val users: List<User>) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val email: TextView = view.findViewById(R.id.tvUserEmail)
            val role: TextView = view.findViewById(R.id.tvUserRole)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]
            holder.email.text = user.email
            holder.role.text = "Role: ${user.role}"
        }

        override fun getItemCount() = users.size
    }
}