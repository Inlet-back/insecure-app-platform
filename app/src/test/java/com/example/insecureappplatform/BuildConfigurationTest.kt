package com.example.insecureappplatform

import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * 教材の結論がビルド設定に依存している箇所を、ビルド設定そのもので確かめる。
 *
 * 署名鍵が分かれているかどうかは APK を見ないと分からないので、
 * そちらは tools/verify/verify-signatures.sh が担当する。ここで見るのは
 * 「分けるつもりの設定が書いてあるか」まで。
 */
class BuildConfigurationTest {

    @Test
    fun `app と attacker が別々の署名鍵を設定している`() {
        val app = RepoPaths.text("app/build.gradle.kts")
        val attacker = RepoPaths.text("attacker/build.gradle.kts")
        listOf("app" to app, "attacker" to attacker).forEach { (name, text) ->
            assertTrue("$name に signingConfigs がない", text.contains("signingConfigs"))
            assertTrue("$name が TeachingKeystore を使っていない",
                text.contains("TeachingKeystore.ensure"))
        }
        assertTrue("app が victim の鍵を使っていない", app.contains("victim-teaching.p12"))
        assertTrue("attacker が attacker の鍵を使っていない",
            attacker.contains("attacker-teaching.p12"))
        assertTrue("2つのモジュールが同じ keystore を指している",
            !attacker.contains("victim-teaching.p12"))
    }

    @Test
    fun `攻撃者アプリが READ_SESSION を実際に要求している`() {
        // 要求していない状態で P4 が失敗しても、
        // 「宣言していないから」なのか「署名が違うから」なのか区別できない。
        val text = RepoPaths.text("attacker/src/main/AndroidManifest.xml")
        assertTrue(
            "attacker が READ_SESSION の uses-permission を宣言していない",
            text.contains("""<uses-permission android:name="com.example.insecureappplatform.permission.READ_SESSION" />""")
        )
    }

    @Test
    fun `READ_SESSION の protectionLevel は signature`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(RepoPaths.file("app/src/main/AndroidManifest.xml"))
        val permissions = doc.getElementsByTagName("permission")
        val read = (0 until permissions.length)
            .map { permissions.item(it) as Element }
            .single {
                it.getAttribute("android:name") ==
                    "com.example.insecureappplatform.permission.READ_SESSION"
            }
        assertTrue(
            "protectionLevel が signature でない",
            read.getAttribute("android:protectionLevel") == "signature"
        )
    }

    @Test
    fun `バックアップ規則が Manifest から参照されている`() {
        val manifest = RepoPaths.text("app/src/main/AndroidManifest.xml")
        assertTrue("fullBackupContent が未設定",
            manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue("dataExtractionRules が未設定",
            manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
    }

    @Test
    fun `Keystore 由来のファイルが Auto Backup から除外されている`() {
        val encrypted = listOf("user_session_secure.xml", "fix_d.xml", "tee_demo.xml")
        listOf(
            "app/src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
        ).forEach { path ->
            val text = RepoPaths.text(path)
            encrypted.forEach {
                assertTrue("$path が $it を除外していない",
                    text.contains("""<exclude domain="sharedpref" path="$it" />"""))
            }
        }
        // device-transfer は cloud-backup と別に書く必要がある
        val extraction = RepoPaths.text("app/src/main/res/xml/data_extraction_rules.xml")
        assertTrue("device-transfer の指定がない", extraction.contains("<device-transfer>"))
    }

    @Test
    fun `認証つきの鍵は認証なしの鍵と別の alias を使う`() {
        // 同じ alias のまま条件だけ変えても、既存の鍵の使用条件は変わらない。
        val source = RepoPaths.text(
            "app/src/main/java/com/example/insecureappplatform/KeystoreActivity.kt"
        )
        assertTrue("認証なしの alias がない", source.contains(""""tee_demo_key""""))
        assertTrue("認証つきの alias がない", source.contains(""""tee_demo_key_auth""""))
        assertTrue("BiometricPrompt を使った正規の認証経路がない",
            source.contains("BiometricPrompt.CryptoObject"))
        assertTrue("未認証の失敗を区別していない",
            source.contains("UserNotAuthenticatedException"))
        assertTrue("鍵の世代ずれを区別していない", source.contains("AEADBadTagException"))
    }
}
