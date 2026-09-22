package com.example.insecureappplatform

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 第4章の結論を守る回帰テスト。端末 / エミュレータ上で走る。
 *
 *   ・Android Keystore の鍵からは、この API ではバイト列を取り出せない
 *   ・それでも暗号化と復号は «使える»
 *   ・鍵を作り直すと、古い暗号文は復号できなくなる（認証による拒否とは別の失敗）
 *
 * 認証つきの鍵 (setUserAuthenticationRequired) は、人間の操作を挟まないと
 * 成立しないので、ここでは自動化しない。手順はガイドの第4章 Deep を参照。
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {

    private val alias = "insecureappplatform_test_key"

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun newKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun key(): SecretKey =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: newKey()

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain.toByteArray())
    }

    private fun decrypt(blob: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, blob.copyOfRange(0, 12))
        )
        return String(cipher.doFinal(blob.copyOfRange(12, blob.size)))
    }

    @Before
    @After
    fun clean() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    @Test
    fun 鍵のバイト列はこのAPIからは取り出せない() {
        assertNull("getEncoded() が値を返した", key().encoded)
        // これが示すのは «この API から取り出せない» ことだけで、
        // ハードウェア保護の証明ではない。保護レベルは KeyInfo で別に見る。
    }

    @Test
    fun 取り出せない鍵でも暗号化と復号はできる() {
        val secret = "himitsu-no-memo"
        assertEquals(secret, decrypt(encrypt(secret)))
    }

    @Test
    fun 鍵を作り直すと古い暗号文は復号できない() {
        val blob = encrypt("himitsu-no-memo")

        keyStore().deleteEntry(alias)
        newKey()

        val failure = runCatching { decrypt(blob) }.exceptionOrNull()
        assertTrue(
            "鍵を作り直したのに古い暗号文が復号できてしまった (${failure?.javaClass?.simpleName})",
            failure != null
        )
        assertEquals(
            "認証による拒否ではなく、鍵と暗号文の世代のずれとして失敗するはず",
            "AEADBadTagException",
            failure!!.javaClass.simpleName
        )
    }

    @Test
    fun 同じ平文でも暗号文は毎回変わる() {
        // GCM は IV が毎回変わる。ECB (CryptoActivity) との対比。
        val a = encrypt("same-plaintext")
        val b = encrypt("same-plaintext")
        assertNotEquals(a.toList(), b.toList())
    }
}
