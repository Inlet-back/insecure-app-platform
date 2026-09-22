package com.example.insecureappplatform

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * 第2章 (IPC 境界) — 脆弱な窓口。
 *
 * 第1章で「OS のプロセス境界に守られていて他アプリからは読めない」ことを確認した
 * user_session の中身を、ContentProvider として外に出してしまっている。
 * AndroidManifest で exported="true" かつ permission 指定なしのため、
 * 端末上のどのアプリからでも、また adb shell content query からでも読める。
 *
 * ポイント: ファイルの権限 (第1章) は何も変わっていない。
 * 箱は閉じたままで、窓口だけが開いている。
 */
open class SessionProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val prefs = ctx().getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf("key", "value"))
        prefs.all.forEach { (k, v) -> cursor.addRow(arrayOf(k, v?.toString())) }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.$AUTHORITY.creds"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun ctx(): Context = context!!

    companion object {
        const val AUTHORITY = "com.example.insecureappplatform.squeeze.session"
    }
}
