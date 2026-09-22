package com.example.insecureappplatform

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InsecureStorageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insecure_storage)

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnSave = findViewById<Button>(R.id.btn_save)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        btnSave.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            // 脆弱性: SharedPreferences に平文で保存
            val sharedPref = getSharedPreferences("user_session", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("username", username)
                putString("password", password)
                apply()
            }

            Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_SHORT).show()
        }
    }
}