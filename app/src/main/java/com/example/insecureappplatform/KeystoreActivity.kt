package com.example.insecureappplatform

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * 第4章 (実行環境境界)。
 *
 * [CryptoActivity] との違いは鍵の置き場所だけ。
 * こちらは Android Keystore に鍵を作るので、鍵のバイト列はアプリのプロセスにも
 * ファイルシステムにも現れず、このアプリからはエクスポートできない。
 * 実際にどのセキュリティレベル (SOFTWARE / TRUSTED_ENVIRONMENT / STRONGBOX) で
 * 保護されているかは端末次第なので、[keyInfo] で実測値を表示する。
 *
 * だが「鍵を取り出せない」ことと「復号させられない」ことは別。
 * このアプリのプロセスに入り込めば decrypt() を呼べてしまう。
 * その実演が Deep 層の Frida 手順 (tools/frida/ch4-use-keystore-key.js)。
 *
 * = 鍵の「保持」と「使用」は別の話、という第4章の山場。
 *
 * 鍵は2本ある。[Mode] を切り替えて、同じ操作の結果がどう変わるかを見る。
 * 条件を後から変えても既存の鍵の条件は変わらないので、alias ごと分けてある。
 */
class KeystoreActivity : AppCompatActivity() {

    /**
     * 使用条件だけが違う2本の鍵。
     *
     * 同じ alias のまま `setUserAuthenticationRequired` を書き換えて上書きインストールしても、
     * 既存の鍵の使用条件は変わらない（[secretKey] は既にある鍵をそのまま返す）。
     * ソースを1行変えれば結果が変わる、という誤解を避けるために alias を分けている。
     */
    private enum class Mode(val alias: String, val label: String) {
        NO_AUTH("tee_demo_key", "認証なしの鍵"),
        AUTH("tee_demo_key_auth", "認証つきの鍵"),
    }

    private var mode = Mode.NO_AUTH

    private lateinit var etPlain: EditText
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keystore)

        etPlain = findViewById(R.id.et_plaintext)
        tvResult = findViewById(R.id.tv_result)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        findViewById<RadioGroup>(R.id.rg_mode).setOnCheckedChangeListener { _, id ->
            mode = if (id == R.id.rb_auth) Mode.AUTH else Mode.NO_AUTH
            tvResult.text = "使う鍵: ${mode.label} (alias = ${mode.alias})"
        }

        findViewById<Button>(R.id.btn_encrypt).setOnClickListener { onEncrypt() }
        findViewById<Button>(R.id.btn_decrypt).setOnClickListener { onDecrypt(authenticated = false) }
        findViewById<Button>(R.id.btn_decrypt_auth).setOnClickListener { onDecrypt(authenticated = true) }
        findViewById<Button>(R.id.btn_keyinfo).setOnClickListener { tvResult.text = keyInfo() }
        findViewById<Button>(R.id.btn_recreate).setOnClickListener { onRecreate() }
    }

    // ------------------------------------------------------------------ 操作

    private fun onEncrypt() {
        val plain = etPlain.text.toString()
        if (plain.isEmpty()) {
            tvResult.text = "暗号化する文字列を入れること。"
            return
        }
        withCipher(Cipher.ENCRYPT_MODE, iv = null) { cipher ->
            val out = cipher.iv + cipher.doFinal(plain.toByteArray())
            val blob = Base64.encodeToString(out, Base64.NO_WRAP)
            prefs().edit()
                .putString(blobKey(), blob)
                // どの世代の鍵で作った暗号文かを一緒に残す。
                // これがないと、復号の失敗が「認証で拒否された」のか
                // 「鍵を作り直したので合わない」のか区別できない。
                .putString(blobGenKey(), prefs().getString(keyGenKey(), "?"))
                .apply()
            tvResult.text = buildString {
                appendLine("使った鍵: ${mode.label} (alias = ${mode.alias})")
                appendLine("暗号文 (shared_prefs/tee_demo.xml にも保存):")
                append(blob)
            }
        }
    }

    private fun onDecrypt(authenticated: Boolean) {
        val blob = prefs().getString(blobKey(), null)
        if (blob == null) {
            tvResult.text = "この鍵ではまだ暗号化していない。先に暗号化すること。"
            return
        }
        val generationNote = generationNote()
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val iv = raw.copyOfRange(0, 12)
        val body = raw.copyOfRange(12, raw.size)

        if (!authenticated) {
            // 認証を挟まずにそのまま使う。認証つきの鍵ならここで Keystore に断られる。
            tvResult.text = generationNote + runCatching {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
                "認証なしで復号できた: ${String(cipher.doFinal(body))}"
            }.getOrElse { explain(it) }
            return
        }

        withCipher(Cipher.DECRYPT_MODE, iv) { cipher ->
            tvResult.text = generationNote + "認証のあとで復号できた: ${String(cipher.doFinal(body))}"
        }
    }

    private fun onRecreate() {
        // 暗号文はわざと消さない。鍵だけを作り直すと古い暗号文が復号できなくなる、
        // という「世代のずれ」を、認証による拒否と並べて見せるため。
        runCatching {
            keyStore().deleteEntry(mode.alias)
            prefs().edit().remove(keyGenKey()).apply()
            secretKey()
        }.onSuccess {
            tvResult.text = buildString {
                appendLine("${mode.alias} を削除して作り直した。")
                appendLine("暗号文はそのまま残してある。")
                append("この状態で復号すると何が起きるかを予想してから押すこと。")
            }
        }.onFailure { tvResult.text = explain(it) }
    }

    // ------------------------------------------------------------------ 鍵

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Keystore に鍵がなければ作る。鍵素材はここには一切現れない。 */
    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(mode.alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            mode.alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .apply {
                if (mode == Mode.AUTH) {
                    // 鍵の «使用» 条件を Keystore 側に置く。
                    // 有効期間を 0 / -1 にすると「1回の操作ごとに認証」になり、
                    // BiometricPrompt に CryptoObject を渡す経路でしか使えなくなる。
                    setUserAuthenticationRequired(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            0,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
            }
            .build()
        generator.init(spec)
        val key = generator.generateKey()
        // 鍵の «世代» を記録する。alias が同じでも、作り直せばこの値が変わる。
        prefs().edit().putString(keyGenKey(), UUID.randomUUID().toString().take(8)).apply()
        return key
    }

    /** 鍵がどのセキュリティレベルで保護されているかを、この端末の実測値として表示する。 */
    private fun keyInfo(): String = try {
        val key = secretKey()
        val factory = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        val info = factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE (ソフトウェア実装)"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT (TEE)"
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX (専用チップ)"
                KeyProperties.SECURITY_LEVEL_UNKNOWN -> "UNKNOWN (判定できない)"
                else -> "UNKNOWN_SECURE (${info.securityLevel})"
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "ハードウェア (isInsideSecureHardware=true)"
            else "SOFTWARE (isInsideSecureHardware=false)"
        }
        buildString {
            appendLine("alias = ${mode.alias} (${mode.label})")
            appendLine("KeyInfo.getSecurityLevel() = $level")
            appendLine("鍵の使用にユーザー認証が要るか: ${info.isUserAuthenticationRequired}")
            appendLine("鍵の世代: ${prefs().getString(keyGenKey(), "?")}")
            appendLine()
            appendLine("この値は端末の実測値であり、Android Keystore の鍵が常に")
            append("TEE や StrongBox にあるとは限らない。エミュレータでは SOFTWARE になる。")
        }
    } catch (e: Exception) {
        "鍵情報の取得に失敗: ${explain(e)}"
    }

    // ---------------------------------------------------------------- 認証経路

    /**
     * 認証つきの鍵を使う «正規の» 経路。
     *
     * Cipher を BiometricPrompt.CryptoObject に包んで渡し、認証が通った Cipher だけが
     * 返ってくる。認証を飛ばした Cipher では Keystore が操作を断る。
     */
    private fun withCipher(opmode: Int, iv: ByteArray?, use: (Cipher) -> Unit) {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").also {
                if (iv == null) it.init(opmode, secretKey())
                else it.init(opmode, secretKey(), GCMParameterSpec(128, iv))
            }
        } catch (e: Exception) {
            tvResult.text = explain(e)
            return
        }

        if (mode == Mode.NO_AUTH) {
            runCatching { use(cipher) }.onFailure { tvResult.text = explain(it) }
            return
        }

        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val can = BiometricManager.from(this).canAuthenticate(allowed)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            tvResult.text = buildString {
                appendLine("この端末では認証経路を使えない (canAuthenticate = $can)。")
                append("エミュレータなら、設定で画面ロック (PIN) か指紋を登録してから再実行すること。")
            }
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    if (authorized == null) {
                        tvResult.text = "認証は通ったが Cipher が返ってこなかった。"
                        return
                    }
                    runCatching { use(authorized) }.onFailure { tvResult.text = explain(it) }
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    tvResult.text = "認証が完了しなかった ($code): $msg\n鍵は使えないままになる。"
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("鍵を使うための認証")
                .setSubtitle("alias = ${mode.alias}")
                .setAllowedAuthenticators(allowed)
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    // ------------------------------------------------------------ 結果の読み分け

    /**
     * 失敗の理由を、教材で区別したい3つに分けて説明する。
     * ここを一括で「復号できなかった」にすると、認証による拒否と
     * 鍵の作り直しによる失敗が同じに見えてしまう。
     */
    private fun explain(e: Throwable): String {
        val cause = generateSequence(e) { it.cause }.toList()
        return when {
            cause.any { it is UserNotAuthenticatedException } ->
                "[未認証] UserNotAuthenticatedException\n" +
                    "鍵は存在するが、ユーザー認証がまだなので Keystore が使用を断った。\n" +
                    "判定しているのはアプリのコードではなく Keystore 側。"

            cause.any { it is KeyPermanentlyInvalidatedException } ->
                "[鍵が無効化] KeyPermanentlyInvalidatedException\n" +
                    "画面ロックや生体情報の登録が変わったため、この鍵は永久に使えなくなった。\n" +
                    "認証で断られたのとは別の失敗。作り直すしかない。"

            cause.any { it.javaClass.simpleName == "AEADBadTagException" } ->
                "[復号失敗] AEADBadTagException\n" +
                    "認証とは関係ない。鍵と暗号文の世代が合っていない。\n" +
                    "鍵を作り直したなら、暗号文も作り直す必要がある。"

            else -> "[失敗] ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** 暗号文を作ったときの鍵と、いまの鍵が同じ世代かどうかを先に言う。 */
    private fun generationNote(): String {
        val keyGen = prefs().getString(keyGenKey(), null)
        val blobGen = prefs().getString(blobGenKey(), null)
        if (keyGen == null || blobGen == null || keyGen == blobGen) return ""
        return "[世代のずれ] 暗号文は鍵 $blobGen で作られ、いまの鍵は $keyGen。\n" +
            "この失敗は認証とは関係ない。\n\n"
    }

    // ---------------------------------------------------------------- 保存場所

    private fun prefs(): SharedPreferences =
        getSharedPreferences("tee_demo", Context.MODE_PRIVATE)

    private fun blobKey() = "blob_${mode.alias}"
    private fun blobGenKey() = "blobgen_${mode.alias}"
    private fun keyGenKey() = "keygen_${mode.alias}"
}
