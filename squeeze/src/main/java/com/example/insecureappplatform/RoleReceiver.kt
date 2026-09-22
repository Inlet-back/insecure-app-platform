package com.example.insecureappplatform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 第2章 (IPC 境界) — 書き込み側の窓口。
 *
 * 脆弱性: exported="true" で外部からのブロードキャストを受け付け、
 * 送信元が誰かを一切確認せずに Intent の中身をそのまま権限として保存する。
 * Intent は「外部からの入力」であって信用できない、という原則の反例。
 *
 * 防御側の観点は、効く順に次の3つ。
 *   1. 外部公開が不要なら `android:exported="false"`。
 *   2. 公開が必要なら receiver 側に permission を要求する。
 *      同じ開発者のアプリだけに許すなら protectionLevel="signature"。
 *      → 実装は [ProtectedRoleReceiver]。
 *   3. extras は外部入力なので、権限や認証状態としてそのまま信用しない。
 *
 * ここで `Binder.getCallingUid()` を送信元の識別に «使ってはならない»。
 * onReceive() は送信側の Binder トランザクションの中で走っていないので、
 * 返るのは自プロセスの UID であって、ブロードキャストを投げた相手の UID ではない。
 * 送信元を知りたい場合は API 34 以降の [BroadcastReceiver.getSentFromUid] /
 * [BroadcastReceiver.getSentFromPackage] を使う。ただしこれらは API 34 未満では
 * 使えず、システムが情報を持たない経路では意味のある値が返らないので、
 * 「取れたら追加で確認する」以上の役割は負わせられない。
 * 到達そのものを止めるのは、あくまで上の 1 か 2 の側。
 */
class RoleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 脆弱性: 送信元の検証なしに Intent の extras を信用している
        val role = intent.getStringExtra("role") ?: return
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .edit()
            .putString("role", role)
            .apply()
    }

    companion object {
        const val ACTION = "com.example.insecureappplatform.squeeze.action.SET_ROLE"
    }
}
