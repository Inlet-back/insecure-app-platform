package com.example.insecureappplatform

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * 第3章 (ネットワーク境界)。
 *
 * 送るものは第1章で保存した認証情報そのもの。
 * 「端末の中では OS が守ってくれたデータが、外に出た瞬間どうなるか」を見る。
 *
 * 3つのボタンは同じデータを同じサーバに送る。違うのは経路の守り方だけ。
 *   1. HTTP           → 誰でも読める。nc や Burp でそのまま見える
 *   2. HTTPS          → 通るかどうかは「端末が相手の証明書を信じるか」で決まる。
 *                        Burp を挟むと、CA をどこに入れたかで結果が変わる (差分実験)
 *   3. HTTPS + ピン留め → 通常の TLS 検証を通したうえで公開鍵も照合する。
 *                        Burp の CA を端末に入れても、ピンが合わないので失敗する
 *
 * 接続先はエミュレータからホストへのループバック (10.0.2.2) のみ。
 * tools/mockserver/server.py を起動してから使うこと。外部サービスには接続しない。
 */
class NetworkActivity : AppCompatActivity() {

    /**
     * 手順3で使う公開鍵ピン (SubjectPublicKeyInfo の SHA-256, Base64)。
     * 初期値はわざと外してある。tools/mockserver/server.py の起動時に表示される
     * pin をここに貼って再ビルドすると、初めて «ピンの照合は» 通る。
     *
     * ただしモックサーバの証明書は自己署名なので、通常の TLS 検証のほうが先に落ちる。
     * 手順6 では、その2段階が別物であることを切り分けて観察する。
     */
    private val expectedPin = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network)

        tvResult = findViewById(R.id.tv_result)
        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_http).setOnClickListener {
            send("http://10.0.2.2:8080/login", pinned = false)
        }
        findViewById<Button>(R.id.btn_https).setOnClickListener {
            send("https://10.0.2.2:8443/login", pinned = false)
        }
        findViewById<Button>(R.id.btn_https_pinned).setOnClickListener {
            send("https://10.0.2.2:8443/login", pinned = true)
        }
    }

    private fun credentials(): String {
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "no-user")
        val pass = prefs.getString("password", "no-password")
        return "username=$user&password=$pass"
    }

    private fun send(endpoint: String, pinned: Boolean) {
        tvResult.text = "送信中: $endpoint"
        val body = credentials()
        Thread {
            val result = try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                if (conn is HttpsURLConnection && pinned) {
                    conn.sslSocketFactory = pinningSocketFactory()
                }
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                "[成功] HTTP $code\n送った中身: $body"
            } catch (e: Exception) {
                val causes = generateSequence(e as Throwable?) { it.cause }.toList()
                val mismatch = causes.filterIsInstance<PinMismatch>().firstOrNull()
                when {
                    // ② 追加のピン照合で落ちた
                    mismatch != null ->
                        "[② ピン不一致で切断]\n期待したピン: $expectedPin\n" +
                            "実際のピン  : ${mismatch.actual}\n" +
                            "通常の TLS 検証は通っている。落ちたのはピンの照合のほう。"

                    // ① 通常の TLS 検証で落ちた。ピンまで進んでいない
                    causes.any { it is CertificateExpiredException } ->
                        "[① 通常の TLS 検証で拒否] 証明書の有効期限切れ\n" +
                            "${e.message}\n" +
                            "ピンが一致していても、ここで落ちれば通らない。"

                    causes.any { it is CertPathValidatorException } ->
                        "[① 通常の TLS 検証で拒否] チェーンを検証できない\n" +
                            "${e.message}\n" +
                            "信頼できる発行者まで辿れていない。ピンの照合はここまで来ていない。"

                    else -> "[失敗] ${e.javaClass.simpleName}\n${e.message}"
                }
            }
            runOnUiThread { tvResult.text = result }
        }.start()
    }

    private class PinMismatch(val actual: String) : CertificateException("pin mismatch")

    /**
     * 通常の TLS 検証を «通したうえで» 、追加でピンを照合する。
     *
     * ピンだけを見る実装にすると、期限切れ・信頼されない CA・壊れたチェーンでも
     * ピンさえ一致すれば通ってしまう。ピン留めは通常の検証の «置き換え» ではなく
     * «上乗せ» なので、順番はこの向きでなければならない。
     *
     * 本番のアプリでこれを手書きする理由はほとんどない。
     * Network Security Config の <pin-set> で宣言するほうが、
     * 検証の順番を間違えようがないぶん安全 (第3章の本文を参照)。
     */
    private fun pinningSocketFactory() = SSLContext.getInstance("TLS").apply {
        val platform = defaultTrustManager()
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                platform.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // ① まず端末の信頼ストアによる通常の検証。
                //    チェーン・有効期限・発行者はここで見る。
                platform.checkServerTrusted(chain, authType)
                // ② そのうえで、公開鍵が想定どおりかを追加で確かめる。
                //    チェーンのどこか1つでも一致すればよい (中間 CA へのピン留めも許す)。
                val pins = chain.map { pinOf(it) }
                if (expectedPin !in pins) throw PinMismatch(pins.first())
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
        }
        init(null, arrayOf(tm), null)
    }.socketFactory

    /**
     * 通常の検証を担当する TrustManager。
     *
     * モックサーバの証明書は教材用の CA (res/raw/mockserver_ca.pem) が発行している。
     * その CA を trust anchor として渡すことで、チェーン・有効期限・発行者の検証が
     * «普通に» 行われるようにしている。
     *
     * ここを `init(null)` にして端末の信頼ストアを使うと、教材の CA は入っていないので
     * ピン以前に必ず落ちる。逆に trust anchor を渡すだけで pin を見なければ、
     * 期限切れの証明書でも通ってしまう。2つは別の検証であり、両方要る。
     */
    private fun defaultTrustManager(): X509TrustManager {
        val anchors = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            resources.openRawResource(R.raw.mockserver_ca).use { input ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificates(input)
                    .forEachIndexed { i, cert -> setCertificateEntry("teaching-ca-$i", cert) }
            }
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(anchors)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** SubjectPublicKeyInfo の SHA-256 を Base64 にしたもの。NSC の pin と同じ計算。 */
    private fun pinOf(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
