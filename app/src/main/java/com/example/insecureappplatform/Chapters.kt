package com.example.insecureappplatform

import androidx.appcompat.app.AppCompatActivity

/**
 * 章の定義。章は「脆弱性の種類」ではなく「どの信頼境界が破られたか」で束ねる。
 * 詳細は リポジトリ直下の RESEARCH-DIRECTION.md を参照。
 *
 * 新しい章を足すときは Chapters.all に1エントリ追加するだけでよい。
 */
data class Chapter(
    val boundary: String,
    val title: String,
    val masvsId: String,
    val layer: String,
    val activityClass: Class<out AppCompatActivity>
)

object Chapters {

    /** 学習の層。Basic だけ通しても一周できる。Deep は任意。 */
    object Layer {
        const val BASIC = "Basic"
        const val STANDARD = "Standard"
        const val DEEP = "Deep"
    }

    private const val B1 = "第1章 プロセス境界"
    private const val B2 = "第2章 IPC境界"
    private const val B3 = "第3章 ネットワーク境界"
    private const val B4 = "第4章 実行環境・TEE境界"

    val all: List<Chapter> = listOf(
        Chapter(B1, "ログイン情報を保存する", "MASVS-STORAGE-1", Layer.BASIC, InsecureStorageActivity::class.java),
        Chapter(B1, "ログイン情報を保存する (暗号化版)", "MASVS-STORAGE-1", Layer.STANDARD, SecureStorageActivity::class.java),
        Chapter(B1, "保存方式 A〜D を比べる", "MASVS-STORAGE-1", Layer.DEEP, FixCompareActivity::class.java),

        Chapter(B2, "ログイン画面", "MASVS-AUTH-1", Layer.BASIC, AuthActivity::class.java),
        Chapter(B2, "管理者コンソール", "MASVS-PLATFORM-1", Layer.BASIC, AdminActivity::class.java),

        Chapter(B3, "ログイン情報を送信する", "MASVS-NETWORK-1", Layer.BASIC, NetworkActivity::class.java),

        Chapter(B4, "文字列を暗号化する", "MASVS-CRYPTO-1", Layer.BASIC, CryptoActivity::class.java),
        Chapter(B4, "文字列を暗号化する (Keystore)", "MASVS-CRYPTO-2", Layer.STANDARD, KeystoreActivity::class.java),
    )
}
