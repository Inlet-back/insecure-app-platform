package com.example.insecureappplatform

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/content 以下の JSON が構文として妥当で、参照している ID が実在することを確かめる。
 *
 * これらのファイルはガイドの HTML から data 属性で参照されるだけなので、
 * 壊れてもビルドは通り、ブラウザで開いたときに «静かに» 何も出なくなる。
 * 教材の結論を守る回帰テストとして、ここで落とす。
 */
class ContentIntegrityTest {

    private fun json(name: String) = RepoPaths.text("docs/content/$name")

    private fun obj(name: String) = JSONObject(json(name))
    private fun arr(name: String) = JSONArray(json(name))

    @Test
    fun `すべての content JSON が構文的に妥当`() {
        listOf("surface.json", "steps.json", "explain.json", "oracle.json", "inquiry.json", "threats.json").forEach {
            // 例外が出なければ妥当
            val text = json(it)
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        }
        JSONArray(json("quiz.json"))
    }

    @Test
    fun `すべての章が完全な脅威ブリーフを参照する`() {
        val threats = obj("threats.json")
        val required = listOf(
            "title", "protect", "opponent", "goal", "asset", "property", "attacker", "preconditions", "excluded",
            "claim", "success", "observe", "residual"
        )
        (1..4).forEach { n ->
            val id = "ch$n"
            assertTrue("$id の脅威ブリーフがない", threats.has(id))
            val threat = threats.getJSONObject(id)
            required.forEach { field ->
                assertTrue("$id.$field が空", threat.optString(field).isNotBlank())
            }
            val html = RepoPaths.text("docs/chapter$n-${listOf("process", "ipc", "network", "tee")[n - 1]}.html")
            assertTrue("$id が脅威ブリーフを参照していない", html.contains("data-threat=\"$id\""))
        }
    }

    @Test
    fun `quiz の answer は選択肢の範囲内を指し、correct タグと一致する`() {
        val quiz = arr("quiz.json")
        assertTrue("設問が空", quiz.length() > 0)
        for (i in 0 until quiz.length()) {
            val q = quiz.getJSONObject(i)
            val id = q.getString("id")
            val options = q.getJSONArray("options")
            val answer = q.getInt("answer")
            assertTrue("$id: answer が範囲外", answer in 0 until options.length())
            assertEquals(
                "$id: answer の指す選択肢が tag=correct でない",
                "correct",
                options.getJSONObject(answer).getString("tag")
            )
            val corrects = (0 until options.length())
                .count { options.getJSONObject(it).getString("tag") == "correct" }
            assertEquals("$id: correct タグがちょうど1つでない", 1, corrects)
        }
    }

    @Test
    fun `surface の各資産は、すべての立場に対する判定と根拠を持つ`() {
        val surface = obj("surface.json")
        val positions = surface.getJSONArray("positions")
        val ids = (0 until positions.length())
            .map { positions.getJSONObject(it).getString("id") }
        assertEquals("立場の id が重複している", ids.size, ids.toSet().size)

        val allowed = setOf("yes", "cond", "no", "na")
        val assets = surface.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("id")
            val reach = asset.getJSONObject("reach")
            val cells = asset.getJSONObject("cells")
            ids.forEach { pid ->
                assertTrue("$name: reach に $pid がない", reach.has(pid))
                assertTrue(
                    "$name.$pid: 判定が $allowed のどれでもない (${reach.getString(pid)})",
                    reach.getString(pid) in allowed
                )
                assertTrue("$name: cells に $pid がない", cells.has(pid))
                assertTrue("$name.$pid: 根拠が空", cells.getString(pid).isNotBlank())
            }
            // 未検証かどうかは reach とは別軸で持つ。混ぜると「届かない」と区別できない。
            if (asset.has("evidence")) {
                val evidence = asset.getJSONObject("evidence")
                evidence.keys().forEach { pid ->
                    assertTrue("$name: evidence の $pid が立場にない", pid in ids)
                    assertTrue(
                        "$name.$pid: evidence は observed / untested のみ",
                        evidence.getString(pid) in setOf("observed", "untested")
                    )
                }
            }
        }
    }

    @Test
    fun `ガイドの HTML が参照する step id は steps json に実在する`() {
        val steps = obj("steps.json")
        val referenced = RepoPaths.root.resolve("docs").listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "html" }
            .flatMap { file ->
                Regex("""data-step="([^"]+)"""").findAll(file.readText())
                    .map { file.name to it.groupValues[1] }
            }
        assertTrue("data-step の参照が1つもない", referenced.isNotEmpty())
        referenced.forEach { (file, id) ->
            assertTrue("$file が参照する step '$id' が steps.json にない", steps.has(id))
        }
    }

    @Test
    fun `steps json の手順は、どこかのページに置かれているか parked と書いてある`() {
        // 逆向きの検査。ページから参照が消えた手順が黙って残ると、
        // 記録ページに «到達できないのに永久に未着手» の行として出てしまう。
        // どこにも置いていない手順は parked を付ける。理由は内部メモに書く。
        val steps = obj("steps.json")
        val referenced = RepoPaths.root.resolve("docs").listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "html" }
            .flatMap { file ->
                Regex("""data-step="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }
            }
            .toSet()
        steps.keys().forEach { id ->
            if (id in referenced) return@forEach
            assertTrue(
                "step '$id' はどのページからも参照されていない。" +
                    "本文に戻すか、parked を付けて寝かせること",
                steps.getJSONObject(id).optBoolean("parked")
            )
        }
    }

    @Test
    fun `steps の予想は ok か ng のどちらか`() {
        val steps = obj("steps.json")
        steps.keys().forEach { id ->
            val step = steps.getJSONObject(id)
            assertTrue(
                "$id: answer が ok / ng でない",
                step.getString("answer") in setOf("ok", "ng")
            )
            listOf("title", "setup", "action", "question", "why").forEach {
                assertTrue("$id: $it が空", step.getString(it).isNotBlank())
            }
        }
    }

    @Test
    fun `教材に存在しない Frida スクリプトを参照していない`() {
        val referenced = mutableSetOf<String>()
        RepoPaths.root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("html", "json", "kt", "js", "md") }
            .filterNot {
                it.path.contains("/build/") || it.path.contains("/.git/") ||
                    // 修正指示書は「直すべき参照」を引用しているので対象外
                    it.name == "TECHNICAL-CORRECTIONS.md"
            }
            .forEach { file ->
                Regex("""tools/frida/[A-Za-z0-9._-]+\.js""").findAll(file.readText())
                    .forEach { referenced += it.value }
            }
        assertTrue("Frida スクリプトの参照が1つもない", referenced.isNotEmpty())
        referenced.forEach {
            assertTrue("存在しないスクリプトを参照している: $it", RepoPaths.root.resolve(it).isFile)
        }
    }

    @Test
    fun `BroadcastReceiver の対策として Binder getCallingUid を勧めていない`() {
        // onReceive() は送信側の Binder トランザクションの中で走らないので、
        // ここで取れる UID は送信元のものではない。教材から完全に消えていることを確かめる。
        val offenders = RepoPaths.root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("html", "json", "kt") }
            .filterNot {
                it.path.contains("/build/") ||
                    it.path.contains("/archive/") ||
                    it.name == "ContentIntegrityTest.kt"
            }
            .filter { file ->
                val text = file.readText()
                text.contains("getCallingUid") &&
                    // 「ここでは使えない」と書いてある箇所は許す
                    listOf("使ってはならない", "使わない", "使えません")
                        .none { warning -> text.contains(warning) }
            }
            .map { it.path }
            .toList()
        assertTrue("getCallingUid を対策として残している: $offenders", offenders.isEmpty())
    }

    @Test
    fun `非推奨の MASTG テスト ID を引用していない`() {
        val offenders = RepoPaths.root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("html", "json", "kt") }
            .filterNot {
                it.path.contains("/build/") ||
                    it.path.contains("/archive/") ||
                    it.name == "ContentIntegrityTest.kt"
            }
            .filter { it.readText().contains("MASTG-TEST-0018") }
            .map { it.path }
            .toList()
        assertTrue("非推奨の MASTG-TEST-0018 が残っている: $offenders", offenders.isEmpty())
    }

    @Test
    fun `Android Keystore を TEE と同一視する断定が残っていない`() {
        val banned = listOf(
            "鍵が TEE にあれば取り出せない",
            "TEE の中。Android 側からは見えない",
        )
        RepoPaths.root.walkTopDown()
            .filter { it.isFile && it.extension in setOf("html", "json", "kt") }
            .filterNot {
                it.path.contains("/build/") ||
                    it.path.contains("/archive/") ||
                    it.name == "ContentIntegrityTest.kt"
            }
            .forEach { file ->
                val text = file.readText()
                banned.forEach {
                    assertFalse("${file.path} に「$it」が残っている", text.contains(it))
                }
            }
    }
}
