package com.example.insecureappplatform

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * ログイン画面。対応する MASVS 管理策は **MASVS-AUTH-1**
 * (アプリが安全な認証・認可のプロトコルを使い、その実務慣行に従っていること)。
 *
 * 脆弱性: 認証情報がクライアント側にハードコードされており、
 * サーバとの通信なしにローカルで照合している。
 * jadx 等での静的解析、または Frida によるメソッドフックで
 * 資格情報の抽出・認証バイパスが可能。ロックアウトも存在しないため
 * 総当たり攻撃も成立する。
 *
 * MASTG のテスト ID は付けていない。この実装に «厳密に» 対応する現行のテストを
 * 確認できなかったため。以前ここには生体認証を対象とした非推奨のテストの ID が
 * 書かれていたが、この画面の内容とは対応していなかった。
 * 識別子を書くときは、公式ページの題名と教材の中身が一致することを必ず確かめる。
 *
 * この画面は現在の4章構成では主教材ではない。
 * 第2章は「窓口 ([AdminActivity]) が認証の «手前に» 露出している」ことを扱うので、
 * その «越えられるべき関門» として1枚必要になる。残してあるのはそのため。
 * ハードコードされた資格情報そのものの解析は、第4章 Basic の
 * 「APK から鍵を探す」と同じ手口なので、そちらに寄せてある。
 */
class AuthActivity : AppCompatActivity() {

    // 脆弱性: ハードコードされた認証情報
    private val validUsername = "admin"
    private val validPassword = "SuperSecret123!"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val etUsername = findViewById<EditText>(R.id.et_username)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin = findViewById<Button>(R.id.btn_login)
        val tvResult = findViewById<TextView>(R.id.tv_result)

        findViewById<Button>(R.id.btn_back_home).setOnClickListener { finish() }

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            // 脆弱性: クライアント側のみでの平文比較、ロックアウトなし
            tvResult.text = if (username == validUsername && password == validPassword) {
                "Login Success"
            } else {
                "Login Failed"
            }
        }
    }
}
