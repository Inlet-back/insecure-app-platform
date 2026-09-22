package com.example.insecureappplatform

/**
 * 第2章 (IPC 境界) — 守った窓口。
 *
 * 中身は [SessionProvider] と全く同じ。違いは AndroidManifest 側だけで、
 *   android:permission="com.example.insecureappplatform.squeeze.permission.READ_SESSION"
 * が付いており、その permission の protectionLevel は signature。
 *
 * 差分実験のためにコードを同一にしてある。攻撃が止まるのはコードのおかげではなく、
 * Manifest の1行と、それを AMS / ContentResolver 側が検査しているおかげ。
 */
class ProtectedSessionProvider : SessionProvider() {
    companion object {
        const val AUTHORITY = "com.example.insecureappplatform.squeeze.session.protected"
    }
}
