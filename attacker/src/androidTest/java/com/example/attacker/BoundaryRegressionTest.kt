package com.example.attacker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 第1章・第2章の結論を守る回帰テスト。
 *
 * 攻撃者アプリ (別 UID・別署名) の側から走らせる。被害者アプリが
 * インストールされていることが前提なので、次の順で実行する。
 *
 *   ./gradlew :app:installDebug
 *   ./gradlew :attacker:connectedDebugAndroidTest
 *
 * 守りたい結論:
 *   ・被害者アプリと攻撃者アプリの署名は一致しない
 *   ・signature permission は、要求していても付与されない
 *   ・permission なしの exported provider は別アプリから読める
 *   ・permission 付きの provider は SecurityException で止まる
 *   ・別 UID のアプリから被害者の private data を直接は開けない
 */
@RunWith(AndroidJUnit4::class)
class BoundaryRegressionTest {

    private val victim = "com.example.insecureappplatform"
    private val readSession = "$victim.permission.READ_SESSION"

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun 被害者アプリがいること() {
        val installed = runCatching {
            context.packageManager.getPackageInfo(victim, 0)
        }.isSuccess
        assertTrue("先に ./gradlew :app:installDebug を実行すること", installed)
    }

    @Test
    fun 被害者アプリと攻撃者アプリの署名は一致しない() {
        // ここが一致すると、下の「permission が付与されない」が成立しなくなる。
        assertEquals(
            "同じ鍵で署名されている。keys/ を消して両モジュールを再ビルドすること",
            PackageManager.SIGNATURE_NO_MATCH,
            context.packageManager.checkSignatures(context.packageName, victim)
        )
    }

    @Test
    fun signature_permissionは要求していても付与されない() {
        val declared = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(readSession) == true
        assertTrue("attacker が READ_SESSION を要求していない。この検証は成立しない", declared)

        assertEquals(
            "要求しただけで付与されてしまっている",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(readSession)
        )
    }

    @Test
    fun 無防備なproviderは別アプリから読める() {
        val cursor = context.contentResolver
            .query(Uri.parse("content://$victim.session/creds"), null, null, null, null)
        assertNotNull("permission なしの exported provider が読めなくなっている", cursor)
        cursor!!.close()
    }

    @Test
    fun permission付きproviderはSecurityExceptionで止まる() {
        val failure = runCatching {
            context.contentResolver.query(
                Uri.parse("content://$victim.session.protected/creds"),
                null, null, null, null
            )?.close()
        }.exceptionOrNull()

        assertTrue(
            "止まらなかった。署名が分かれているか確認すること (実際: $failure)",
            failure is SecurityException
        )
    }

    @Test
    fun 被害者のprivate_dataは直接開けない() {
        val failure = runCatching {
            java.io.File("/data/data/$victim/shared_prefs/user_session.xml").readText()
        }.exceptionOrNull()

        // 返る例外は端末と OS バージョンで変わる (EACCES / ENOENT)。
        // 固定のエラーコードではなく「開けないこと」を結論にする。
        assertNotNull("別 UID のアプリから被害者の private data が読めてしまった", failure)
    }

    @Test
    fun 自分自身のprivate_dataは同じコードで読める() {
        // 上のテストが「攻撃者アプリのバグ」ではないことの対照実験。
        val own = java.io.File(context.filesDir, "control.txt")
        own.writeText("attacker own data")
        assertEquals("attacker own data", own.readText())
    }

    @Test
    fun permission付きreceiverには配送されない() {
        val prefs = "role"
        // 送信自体はエラーにならない。判定は受信側で行われる。
        context.sendBroadcast(
            Intent("$victim.action.SET_ROLE_PROTECTED").apply {
                setPackage(victim)
                putExtra(prefs, "admin")
            }
        )
        // 届いたかどうかは送信側からは観測できない。
        // 実際に書き換わっていないことは、無防備な provider 経由で読んで確かめる。
        Thread.sleep(1_000)
        val cursor = context.contentResolver
            .query(Uri.parse("content://$victim.session/creds"), null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use { c ->
            while (c.moveToNext()) {
                if (c.getString(0) == prefs) {
                    assertTrue(
                        "permission 付き receiver に届いて role が書き換わっている",
                        c.getString(1) != "admin"
                    )
                }
            }
        }
    }
}
