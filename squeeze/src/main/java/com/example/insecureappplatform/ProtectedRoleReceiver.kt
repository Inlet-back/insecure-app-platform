package com.example.insecureappplatform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 第2章 (IPC 境界) — 守った窓口（書き込み側）。
 *
 * [RoleReceiver] との違いは2つだけ。
 *   1. AndroidManifest 側で
 *        android:permission="com.example.insecureappplatform.squeeze.permission.READ_SESSION"
 *      を要求している。protectionLevel は signature なので、
 *      別の鍵で署名されたアプリは «宣言しても» 付与されない。
 *      送信側にこの permission がなければ、この receiver には配送されない。
 *   2. 届いた extras をそのまま保存せず、想定した値かどうかを確認している。
 *
 * 2 だけでは守りにならない。攻撃者は想定内の値 (admin) を送ってくるので、
 * 値の検証は «送信元を絞ったあとの» 二重化として置いている。
 * 到達を止めているのは 1 のほう。
 *
 * ここでも `Binder.getCallingUid()` は使わない。onReceive() は送信側の
 * Binder トランザクションの中で走っていないので、送信元の UID は取れない。
 * API 34 以降なら [getSentFromUid] / [getSentFromPackage] で «参考値として»
 * 送信元を記録できるが、それが使えない端末でも成立する防御を先に置く。
 */
class ProtectedRoleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val role = intent.getStringExtra("role") ?: return
        if (role !in ALLOWED_ROLES) return

        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .edit()
            .putString("role", role)
            .apply()
    }

    companion object {
        const val ACTION = "com.example.insecureappplatform.squeeze.action.SET_ROLE_PROTECTED"
        private val ALLOWED_ROLES = setOf("guest", "member")
    }
}
