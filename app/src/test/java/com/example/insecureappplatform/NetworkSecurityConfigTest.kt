package com.example.insecureappplatform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 第3章の結論は Network Security Config の中身に依存している。
 *
 *   手順5「Burp の CA をユーザー証明書として入れても復号できない」は、
 *   trust-anchors に user が «入っていない» ことが前提。
 *   差分実験のためにコメントを外したまま戻し忘れると、教材の結論が黙って反転する。
 */
class NetworkSecurityConfigTest {

    private val nscPath = "app/src/main/res/xml/network_security_config.xml"

    private fun domainConfig(): Element {
        val factory = DocumentBuilderFactory.newInstance()
        // コメントの中身は «設定» ではないので、パース結果には現れない。
        val doc = factory.newDocumentBuilder().parse(RepoPaths.file(nscPath))
        val nodes = doc.getElementsByTagName("domain-config")
        assertEquals("domain-config はちょうど1つ", 1, nodes.length)
        return nodes.item(0) as Element
    }

    @Test
    fun `信頼するのはシステム証明書だけで、ユーザー証明書は有効になっていない`() {
        val anchors = domainConfig().getElementsByTagName("certificates")
        val sources = (0 until anchors.length)
            .map { (anchors.item(it) as Element).getAttribute("src") }
        assertEquals("trust-anchors は system だけであること", listOf("system"), sources)
    }

    @Test
    fun `平文通信を許しているのはモックサーバのループバックだけ`() {
        val config = domainConfig()
        assertEquals("true", config.getAttribute("cleartextTrafficPermitted"))
        val domains = config.getElementsByTagName("domain")
        val names = (0 until domains.length).map { domains.item(it).textContent.trim() }
        assertEquals(listOf("10.0.2.2"), names)
    }

    @Test
    fun `base-config で全体に平文を許していない`() {
        val text = RepoPaths.text(nscPath)
        // コメント部分を落としてから見る
        val effective = text.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertTrue("base-config を足すなら、この検査も一緒に見直すこと",
            !effective.contains("<base-config"))
    }

    @Test
    fun `ピン留めの教材実装は、通常の TLS 検証を先に通している`() {
        // ピンだけを見る実装は「ピンが一致すれば期限切れでも通る」ので、
        // 安全な TLS 実装の例にはならない。順番が逆になっていないことを見る。
        val source = RepoPaths.text(
            "app/src/main/java/com/example/insecureappplatform/NetworkActivity.kt"
        )
        val body = source.substringAfter("override fun checkServerTrusted")
            .substringBefore("override fun getAcceptedIssuers")
        val platformCheck = body.indexOf("platform.checkServerTrusted")
        val pinCheck = body.indexOf("expectedPin")
        assertTrue("既定の TrustManager による検証が見当たらない", platformCheck >= 0)
        assertTrue("ピンの照合が見当たらない", pinCheck >= 0)
        assertTrue("通常の TLS 検証より先にピンを見ている", platformCheck < pinCheck)
    }
}
