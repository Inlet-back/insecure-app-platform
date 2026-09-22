package com.example.insecureappplatform

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 第2章 (IPC 境界) — 認証の先にあるはずの画面。
 *
 * 脆弱性: exported="true" のため、ログイン画面を通らずに
 *   adb shell am start -n com.example.insecureappplatform/.AdminActivity
 * や、他アプリからの明示的 Intent で直接開けてしまう。
 * 画面側でもセッションの有効性を確認していない。
 */
class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val role = prefs.getString("role", "guest")
        val user = prefs.getString("username", "(未保存)")

        // 解説はガイド側の仕事。ここは状態を表示するだけに留める。
        findViewById<TextView>(R.id.tv_admin).text = buildString {
            appendLine("=== 管理者コンソール ===")
            appendLine("user = $user")
            appendLine("role = $role")
        }
    }
}
