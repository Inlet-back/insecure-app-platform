import java.io.File

/**
 * 教材用の署名鍵をローカルに用意する。
 *
 * 第2章の signature permission は「要求する側が、宣言した側と同じ証明書で署名されている」
 * ときだけ付与される。被害者アプリと攻撃者アプリを既定の debug keystore
 * （~/.android/debug.keystore）で署名すると両者の証明書が一致してしまい、
 * 教材の結論（攻撃者アプリは permission を宣言しても弾かれる）が逆転する。
 *
 * そこで、モジュールごとに別々の鍵をこの関数で用意する。
 *
 * 鍵はリポジトリにコミットしない（keys/ は .gitignore 済み）。
 * 無ければ JDK 同梱の keytool でその場に生成するので、学習者は普段どおり
 * ./gradlew installDebug するだけでよい。パスワードは教材専用の既定値であり、
 * ここで作られる鍵は配布物にも本番にも使わない。
 */
object TeachingKeystore {

    const val STORE_PASSWORD = "insecureappplatform"
    const val KEY_PASSWORD = "insecureappplatform"

    fun ensure(file: File, alias: String, commonName: String): File {
        if (file.exists()) return file
        file.parentFile?.mkdirs()

        val keytool = File(File(System.getProperty("java.home"), "bin"), "keytool")
        val command = listOf(
            keytool.absolutePath, "-genkeypair",
            "-keystore", file.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", STORE_PASSWORD,
            "-keypass", KEY_PASSWORD,
            "-alias", alias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=$commonName, OU=InsecureAppPlatform, O=Teaching Material, C=JP",
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            file.delete()
            error("教材用の署名鍵を生成できなかった: ${file.absolutePath}\n$output")
        }
        return file
    }
}
