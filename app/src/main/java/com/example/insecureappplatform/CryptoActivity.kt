package com.example.insecureappplatform

import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Chapter 4: Insecure Cryptography (MASVS-CRYPTO)
 * 脆弱性:
 *  1) 暗号鍵がソースコードにハードコードされている（APK 逆コンパイルで抽出可能）
 *  2) ECB モードを使用しており、同一平文ブロックが同一暗号文ブロックとして
 *     出力されるため、パターンが漏洩しブロック単位の推測・置換攻撃が可能
 */
class CryptoActivity : AppCompatActivity() {

    // 脆弱性: ハードコードされた鍵 (16 bytes = AES-128)
    private val hardcodedKey = "1234567890abcdef".toByteArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crypto)

        val etPlainText = findViewById<EditText>(R.id.et_plaintext)
        val btnEncrypt = findViewById<Button>(R.id.btn_encrypt)
        val tvResult = findViewById<TextView>(R.id.tv_result)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        btnEncrypt.setOnClickListener {
            val plainText = etPlainText.text.toString()

            // 脆弱性: AES/ECB/PKCS5Padding + ハードコード鍵
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(hardcodedKey, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(plainText.toByteArray())

            tvResult.text = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }
    }
}
