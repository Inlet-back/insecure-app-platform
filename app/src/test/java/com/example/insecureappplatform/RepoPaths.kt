package com.example.insecureappplatform

import java.io.File

/**
 * ローカル unit test からリポジトリ内のファイルを読むための入口。
 *
 * テストの作業ディレクトリはモジュール (app/) なので、リポジトリのルートは
 * settings.gradle.kts を目印に上へ辿って決める。
 */
object RepoPaths {

    val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("リポジトリのルートが見つからない (settings.gradle.kts を探した)")
    }

    fun file(relative: String): File = File(root, relative).also {
        require(it.isFile) { "ファイルがない: ${it.absolutePath}" }
    }

    fun text(relative: String): String = file(relative).readText()
}
