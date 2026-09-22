package com.example.insecureappplatform

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Chapter 1 (Fixed): [InsecureStorageActivity] の修正版。
 * Keystore に保存された鍵で AES256-GCM / AES256-SIV 暗号化する
 * EncryptedSharedPreferences を用い、shared_prefs XML を adb pull しても
 * 平文の username/password が読めないようにする。
 *
 * 注意: androidx.security の EncryptedSharedPreferences と MasterKey は、
 * 安定版 1.1.0 で «非推奨» になっている。ここで使い続けているのは、
 * 「ファイルを取られる攻撃には効き、プロセスを取られる攻撃には効かない」という
 * 境界を見せる教材として分かりやすいからで、いま新しく書くアプリの推奨実装ではない。
 * 現行の選択肢を選ぶときは、そもそも端末に機密を永続化する必要があるかを先に問うこと。
 * また、これらのファイルは Auto Backup から除外する必要がある
 * (res/xml/backup_rules.xml と res/xml/data_extraction_rules.xml を参照)。
 */
class SecureStorageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secure_storage)

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnSave = findViewById<Button>(R.id.btn_save)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val securePrefs = EncryptedSharedPreferences.create(
            applicationContext,
            "user_session_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        btnSave.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            with(securePrefs.edit()) {
                putString("username", username)
                putString("password", password)
                apply()
            }

            Toast.makeText(this, "Saved Securely!", Toast.LENGTH_SHORT).show()
        }
    }
}
