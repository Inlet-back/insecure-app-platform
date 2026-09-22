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
import android.util.Log
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
 * 第4章 (Android Keystoreと鍵の使用)。
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
            tvResult.text = if (mode == Mode.AUTH) {
                "Walletの利用時に画面ロックで確認します。"
            } else {
                "Walletをすぐに利用できる設定です。"
            }
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
            tvResult.text = "ギフトコードを入力してください。"
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
            Log.d("SqueezeWallet", "saved alias=${mode.alias} blob=$blob")
            tvResult.text = "ギフトコードをWalletに追加しました。"
        }
    }

    private fun onDecrypt(authenticated: Boolean) {
        val blob = prefs().getString(blobKey(), null)
        if (blob == null) {
            tvResult.text = "Walletにギフトコードがありません。"
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
                "利用できるギフトコード: ${String(cipher.doFinal(body))}"
            }.getOrElse { explain(it) }
            return
        }

        withCipher(Cipher.DECRYPT_MODE, iv) { cipher ->
            tvResult.text = generationNote + "確認が完了しました\n利用できるギフトコード: ${String(cipher.doFinal(body))}"
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
            tvResult.text = "Walletの保護設定をリセットしました。"
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
        Log.d("SqueezeWallet", "alias=${mode.alias} level=$level auth=${info.isUserAuthenticationRequired}")
        buildString {
            appendLine("この端末でWalletを保護しています")
            appendLine("保護レベル: ${level.substringBefore(" (")}")
            append("画面ロックでの確認: ${if (info.isUserAuthenticationRequired) "オン" else "オフ"}")
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
            Log.d("SqueezeWallet", "authentication unavailable: $can")
            tvResult.text = "画面ロックが設定されていません。端末の設定を確認してください。"
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorized = result.cryptoObject?.cipher
                    if (authorized == null) {
                        tvResult.text = "確認を完了できませんでした。もう一度お試しください。"
                        return
                    }
                    runCatching { use(authorized) }.onFailure { tvResult.text = explain(it) }
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    Log.d("SqueezeWallet", "authentication error $code: $msg")
                    tvResult.text = "確認がキャンセルされました。"
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Squeeze Wallet")
                .setSubtitle("Wallet残高を利用するため確認してください")
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
        Log.d("SqueezeWallet", "wallet operation failed", e)
        val cause = generateSequence(e) { it.cause }.toList()
        return when {
            cause.any { it is UserNotAuthenticatedException } ->
                "画面ロックでの確認が必要です。"

            cause.any { it is KeyPermanentlyInvalidatedException } ->
                "端末のセキュリティ設定が変更されました。Walletをリセットしてください。"

            cause.any { it.javaClass.simpleName == "AEADBadTagException" } ->
                "このギフトコードを開けませんでした。Walletへ追加し直してください。"

            else -> "Walletを利用できませんでした。もう一度お試しください。"
        }
    }

    /** 暗号文を作ったときの鍵と、いまの鍵が同じ世代かどうかを先に言う。 */
    private fun generationNote(): String {
        val keyGen = prefs().getString(keyGenKey(), null)
        val blobGen = prefs().getString(blobGenKey(), null)
        if (keyGen == null || blobGen == null || keyGen == blobGen) return ""
        Log.d("SqueezeWallet", "wallet generation mismatch: blob=$blobGen key=$keyGen")
        return "Walletの保護設定が変更されています。\n"
    }

    // ---------------------------------------------------------------- 保存場所

    private fun prefs(): SharedPreferences =
        getSharedPreferences("tee_demo", Context.MODE_PRIVATE)

    private fun blobKey() = "blob_${mode.alias}"
    private fun blobGenKey() = "blobgen_${mode.alias}"
    private fun keyGenKey() = "keygen_${mode.alias}"
}
