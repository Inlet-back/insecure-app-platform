package com.example.attacker

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 攻撃者アプリ。被害者アプリ (com.example.insecureappplatform) とは
 * 別の APK / 別の UID でインストールされる、普通のサードパーティアプリ。
 *
 * 各ボタンは「プローブ」であり、被害者アプリの資産に触りに行って
 * 成功したか OS に止められたかを表示するだけ。特別な権限は持っていない。
 *
 * 第1章 (プロセス境界): P1 は必ず失敗する。これは攻撃者アプリの不具合ではなく
 *   Android が「1アプリ = 1 Linux ユーザー」として隔離しているため。
 * 第2章 (IPC 境界): P3〜P7 は、被害者側の窓口の作り方次第で成功したり失敗したりする。
 */
class AttackerActivity : AppCompatActivity() {

    private val victim = "com.example.insecureappplatform"

    private val READ_SESSION = "com.example.insecureappplatform.permission.READ_SESSION"

    private lateinit var log: TextView
    private lateinit var logScroll: ScrollView

    private data class Probe(
        val id: String,
        val title: String,
        val expectation: String,
        val run: () -> String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attacker)

        log = findViewById(R.id.tv_log)
        logScroll = findViewById(R.id.log_scroll)
        findViewById<Button>(R.id.btn_clear).setOnClickListener { log.text = "" }

        val container = findViewById<LinearLayout>(R.id.probe_container)
        probes().forEach { probe ->
            container.addView(Button(this).apply {
                text = "${probe.id}: ${probe.title}"
                isAllCaps = false
                setOnClickListener { execute(probe) }
            })
        }

        append("攻撃者アプリ UID = ${Process.myUid()}")
        append("先に予想してからボタンを押すこと。")
    }

    private fun probes(): List<Probe> = listOf(
        Probe("P0", "自分と被害者の UID を比べる", "違う UID のはず") {
            val victimUid = packageManager.getApplicationInfo(victim, 0).uid
            "攻撃者 UID = ${Process.myUid()} / 被害者 UID = $victimUid\n" +
                if (victimUid == Process.myUid()) "同じ UID。隔離されていない。"
                else "別の UID。OS から見ると別人のアカウント。"
        },

        Probe("P1", "被害者の shared_prefs を直接読む", "失敗するはず") {
            val f = File("/data/data/$victim/shared_prefs/user_session.xml")
            f.readText()
        },

        Probe("P2", "自分の shared_prefs を読む (対照実験)", "成功するはず") {
            val own = File(filesDir.parentFile, "shared_prefs")
            own.mkdirs()
            val f = File(own, "attacker_own.xml")
            if (!f.exists()) f.writeText("<map><string name=\"note\">攻撃者自身のデータ</string></map>")
            f.readText()
        },

        Probe("P3", "被害者の ContentProvider を読む (無防備な窓口)", "第2章: 成功してしまう") {
            queryProvider("content://$victim.session/creds")
        },

        Probe("P4", "permission で守った ContentProvider を読む", "第2章: 止められるはず") {
            queryProvider("content://$victim.session.protected/creds")
        },

        Probe("P4b", "READ_SESSION が自分に付与されたか調べる", "第2章: 付与されないはず") {
            val granted = checkSelfPermission(READ_SESSION) == PackageManager.PERMISSION_GRANTED
            val declared = packageManager
                .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions
                ?.contains(READ_SESSION) == true
            buildString {
                appendLine("AndroidManifest で要求しているか: $declared")
                appendLine("実際に付与されているか      : $granted")
                appendLine(sameSignature())
                append(
                    if (!declared) "要求していないので、P4 が拒否された理由はこれ。"
                    else if (granted) "要求し、かつ付与されている。P4 は通るはず。署名が一致していないか確認すること。"
                    else "要求しているのに付与されていない。protectionLevel=signature で、署名が一致しないため。"
                )
            }
        },

        Probe("P5", "被害者の管理画面を直接起動する (exported=true)", "第2章: 起動できてしまう") {
            startVictimActivity(".AdminActivity")
        },

        // 比較対象は «同じ第2章の» 非公開画面にそろえる。第1章の画面を借りると、
        // 資料の手順4 と別のものを見ることになる。
        Probe("P6", "被害者の非公開画面を直接起動する (exported=false)", "第2章: 止められるはず") {
            startVictimActivity(".AuthActivity")
        },

        Probe("P7", "被害者に Intent を投げて設定を書き換える (Receiver)", "第2章: 通ってしまう") {
            val i = Intent("$victim.action.SET_ROLE").apply {
                setPackage(victim)
                putExtra("role", "admin")
            }
            sendBroadcast(i)
            "ブロードキャスト送信済み。被害者アプリの管理画面で role を確認すること。"
        },

        Probe("P7b", "permission で守った Receiver に同じ Intent を投げる", "第2章: 届かないはず") {
            val i = Intent("$victim.action.SET_ROLE_PROTECTED").apply {
                setPackage(victim)
                putExtra("role", "admin")
            }
            sendBroadcast(i)
            buildString {
                appendLine("送信自体はエラーにならない。sendBroadcast は «届いたか» を返さない。")
                appendLine("判定は受信側の permission で行われ、こちらには何も通知されない。")
                appendLine(sameSignature())
                append("被害者アプリの管理画面で role を確認すること。ただし role が変わらないことは、")
                append("«配送されなかった» ことの証明ではない。受信側が受け取って何もしなかった場合と、")
                append("そもそも配送されなかった場合を、送信側からは区別できない。")
            }
        }
    )

    /** 被害者アプリと同じ証明書で署名されているかを OS に聞く。 */
    private fun sameSignature(): String {
        val match = packageManager.checkSignatures(packageName, victim)
        val verdict = when (match) {
            PackageManager.SIGNATURE_MATCH -> "一致 (SIGNATURE_MATCH)"
            PackageManager.SIGNATURE_NO_MATCH -> "不一致 (SIGNATURE_NO_MATCH)"
            PackageManager.SIGNATURE_UNKNOWN_PACKAGE -> "被害者アプリが見つからない"
            else -> "判定不能 ($match)"
        }
        return "被害者アプリとの署名の照合  : $verdict"
    }

    private fun queryProvider(uri: String): String {
        val cursor = contentResolver.query(Uri.parse(uri), null, null, null, null)
            ?: return "cursor が null。Provider が見つからないか、可視性で隠されている。"
        cursor.use { c ->
            if (c.count == 0) return "0 行。被害者アプリでデータを保存してから再試行。"
            val sb = StringBuilder("${c.count} 行取得:\n")
            while (c.moveToNext()) {
                (0 until c.columnCount).forEach { i ->
                    sb.append("  ${c.getColumnName(i)} = ${c.getString(i)}\n")
                }
            }
            return sb.toString()
        }
    }

    /**
     * 明示 Intent で被害者アプリの Activity を起動する。
     *
     * 戻り値で «画面が出た» とは書かない。startActivity() が例外を投げなかったことは
     * 起動要求が受理されたところまでしか意味せず、実際に前面に出たかは対象アプリ側で見る。
     * 「認証を通さずに」とも書かない。この教材のログイン画面は管理画面へ遷移しないので、
     * 越えるべき関門がそもそも無く、通っていないものを «通さずに» とは言えない。
     */
    private fun startVictimActivity(cls: String): String {
        startActivity(Intent().apply {
            component = ComponentName(victim, victim + cls)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        return "起動要求は拒否されなかった。実際に画面が出たかは被害者アプリ側で確認すること。"
    }

    private fun execute(probe: Probe) {
        append("")
        append("── ${probe.id} ${probe.title}")
        append("   予想: ${probe.expectation}")
        val result = try {
            "[成功] " + probe.run()
        } catch (e: SecurityException) {
            "[OS が拒否] SecurityException\n   ${e.message}\n" +
                "   → 攻撃者アプリの不具合ではない。Android が権限チェックで止めた。"
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val victimPath = msg.contains("/data/data/$victim")
            when {
                msg.contains("EACCES") || msg.contains("Permission denied") ->
                    "[OS が拒否] ${e.javaClass.simpleName}\n   $msg\n" +
                        "   → 攻撃者アプリの不具合ではない。Linux のファイル権限 (DAC) で止められた。"

                // 返るエラーコードは端末・OS バージョン・経由する API によって変わる。
                // EACCES のこともあれば ENOENT のこともあるので、結論はコードではなく
                // 「別 UID のアプリからは被害者の private data を開けない」に置く。
                msg.contains("ENOENT") && victimPath ->
                    "[OS が拒否] ${e.javaClass.simpleName}\n   $msg\n" +
                        "   → 攻撃者アプリの不具合ではない。P2 が成功することがその証拠。\n" +
                        "     被害者アプリで保存済みなら、このファイルは実際には存在する。\n" +
                        "     別 UID のアプリからは、UID 分離・ファイル権限 (DAC)・SELinux により\n" +
                        "     被害者の private data に到達できない。\n" +
                        "     なお返るのが EACCES か ENOENT かは端末と OS バージョンで変わる。\n" +
                        "     この端末で何が返ったかは上の1行が実測値。"

                else -> "[失敗] ${e.javaClass.simpleName}: $msg"
            }
        }
        append(result)
    }

    private fun append(line: String) {
        log.text = "${log.text}\n$line"
        // 最新の結果が画面外に出ていかないよう、末尾まで送る
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
