package com.example.insecureappplatform

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 第1章 Deep — 修正案 A〜D の比較。
 *
 * 同じパスワードを4通りの「修正」で保存し、それぞれ別の
 * shared_prefs ファイルに書き出す。学習者は保存後に
 *   adb shell run-as com.example.insecureappplatform cat shared_prefs/fix_X.xml
 * を4回打ち、「暗号化した/していない」の二値ではなく
 * 何を守り何を守らないかを比べる。
 *
 *  A: Base64 のみ            → 暗号化ではない。鍵なしで誰でも戻せる
 *  B: AES/ECB + ハードコード鍵 → 鍵が APK 内にある。同じ平文が同じ暗号文になる
 *  C: AES/CBC + IV 固定       → ECB よりましだが、同じ平文が同じ暗号文になる点は同じ
 *  D: EncryptedSharedPreferences → 鍵は Keystore。ファイルを取られても復号できない
 *     (ただしプロセス内でコードを実行できる相手には、鍵を «使わせる» ことができる。手順11 を参照)
 *
 * 注意: androidx.security の EncryptedSharedPreferences と MasterKey は、
 * 安定版 1.1.0 で «非推奨» になっている。ここで使い続けているのは、
 * 「ファイルを取られる攻撃には効き、プロセスを取られる攻撃には効かない」という
 * 境界を見せる教材として分かりやすいからで、いま新しく書くアプリの推奨実装ではない。
 * 現行の選択肢を選ぶときは、そもそも端末に機密を永続化する必要があるかを先に問うこと。
 * また、これらのファイルは Auto Backup から除外する必要がある
 * (res/xml/backup_rules.xml と res/xml/data_extraction_rules.xml を参照)。
 */
class FixCompareActivity : AppCompatActivity() {

    // 修正案 B / C が抱える問題そのもの: 鍵がソースに埋まっている
    private val hardcodedKey = "1234567890abcdef".toByteArray()
    private val fixedIv = "abcdefghijklmnop".toByteArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fix_compare)

        val etPassword = findViewById<EditText>(R.id.et_password)
        val tvResult = findViewById<TextView>(R.id.tv_result)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_save_all).setOnClickListener {
            val password = etPassword.text.toString()

            val a = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
            write("fix_a", a)

            val b = encrypt("AES/ECB/PKCS5Padding", password, null)
            write("fix_b", b)

            val c = encrypt("AES/CBC/PKCS5Padding", password, IvParameterSpec(fixedIv))
            write("fix_c", c)

            val masterKey = MasterKey.Builder(applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                applicationContext,
                "fix_d",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            securePrefs.edit().putString("password", password).apply()

            // どの案が何をしているかはガイド側で扱う。ここは結果だけ出す。
            tvResult.text = buildString {
                appendLine("4通りで保存した。shared_prefs/fix_a〜fix_d を比べること。")
                appendLine()
                appendLine("A = $a")
                appendLine("B = $b")
                appendLine("C = $c")
                appendLine("D = shared_prefs/fix_d.xml を参照")
            }
        }
    }

    private fun encrypt(transformation: String, plain: String, iv: IvParameterSpec?): String {
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(hardcodedKey, "AES")
        if (iv == null) cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        else cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun write(file: String, value: String) {
        getSharedPreferences(file, Context.MODE_PRIVATE)
            .edit()
            .putString("password", value)
            .apply()
    }
}
