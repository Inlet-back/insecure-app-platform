package com.example.insecureappplatform

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Squeeze の通常利用画面。
 *
 * 教材上の章や脆弱性はここには表示しない。商品を選び、カートに入れ、注文し、
 * アカウントや Wallet を管理する一続きの操作の裏側で各教材ロジックが動く。
 */
class MainActivity : AppCompatActivity() {

    private data class Product(val name: String, val description: String, val price: Int, val mark: String)

    private val products = listOf(
        Product("シトラスブレンド", "国産みかんとレモンの爽やかな一本", 680, "🍊"),
        Product("ルビーベリーミックス", "いちご・ラズベリー・ブルーベリー", 760, "🍓"),
        Product("グリーンアップル", "青りんごとケールのすっきりブレンド", 720, "🍏"),
        Product("3種のテイスティングセット", "人気の3フレーバーを一度に", 1980, "🎁"),
    )

    private lateinit var content: LinearLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var cartBadge: TextView
    private lateinit var bottomNav: BottomNavigationView
    private var selectedTab = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content_container)
        scroll = findViewById(R.id.main_scroll)
        cartBadge = findViewById(R.id.tv_cart_badge)
        bottomNav = findViewById(R.id.bottom_nav)

        cartBadge.setOnClickListener {
            bottomNav.selectedItemId = R.id.nav_cart
        }
        bottomNav.setOnItemSelectedListener {
            selectedTab = it.itemId
            render()
            true
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun render() {
        content.removeAllViews()
        updateCartBadge()
        when (selectedTab) {
            R.id.nav_cart -> renderCart()
            R.id.nav_orders -> renderOrders()
            R.id.nav_account -> renderAccount()
            else -> renderHome()
        }
        scroll.scrollTo(0, 0)
    }

    private fun renderHome() {
        addTitle("からだに、おいしい。")
        addSubtitle("旬の果実をぎゅっと詰めた、毎日のためのジュース。")

        content.addView(MaterialCardView(this).apply {
            radius = 22.dp.toFloat()
            cardElevation = 0f
            layoutParams = matchParams(top = 22, bottom = 14).apply { height = 210.dp }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.juice_catalog_hero)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "季節のジュースコレクション"
                layoutParams = LinearLayout.LayoutParams(-1, -1)
            })
        })

        addSection("おすすめ")
        products.forEach { addProductCard(it) }
    }

    private fun addProductCard(product: Product) {
        val card = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 15.dp, 12.dp, 15.dp)
        }
        row.addView(TextView(this).apply {
            text = product.mark
            textSize = 36f
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface_soft))
            layoutParams = LinearLayout.LayoutParams(64.dp, 64.dp)
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 0, 8.dp, 0)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(bodyText(product.name, bold = true, size = 16f))
            addView(bodyText(product.description, muted = true, size = 12f).apply { setPadding(0, 3.dp, 0, 4.dp) })
            addView(bodyText("¥${product.price}", bold = true, color = R.color.blue_600, size = 15f))
        })
        row.addView(MaterialButton(this).apply {
            text = "追加"
            isAllCaps = false
            cornerRadius = 14.dp
            setOnClickListener { addToCart(product) }
        })
        card.addView(row)
        content.addView(card)
    }

    private fun addToCart(product: Product) {
        val prefs = storePrefs()
        val names = prefs.getString("cart_items", "").orEmpty()
        prefs.edit()
            .putString("cart_items", listOf(names, product.name).filter { it.isNotBlank() }.joinToString("|"))
            .putInt("cart_count", prefs.getInt("cart_count", 0) + 1)
            .putInt("cart_total", prefs.getInt("cart_total", 0) + product.price)
            .apply()
        updateCartBadge()
        Toast.makeText(this, "${product.name}をカートに追加しました", Toast.LENGTH_SHORT).show()
    }

    private fun renderCart() {
        addTitle("ショッピングバッグ")
        val prefs = storePrefs()
        val count = prefs.getInt("cart_count", 0)
        if (count == 0) {
            addEmpty("🧃", "バッグは空です", "気になるジュースを追加してみましょう。")
            addPrimaryButton("商品を見る") { bottomNav.selectedItemId = R.id.nav_home }
            return
        }

        addSubtitle("$count 点の商品が入っています。")
        prefs.getString("cart_items", "").orEmpty().split("|").filter { it.isNotBlank() }.forEach {
            content.addView(card().apply {
                addView(LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(18.dp, 16.dp, 18.dp, 16.dp)
                    addView(bodyText("🧃", size = 28f))
                    addView(bodyText(it, bold = true, size = 15f).apply {
                        setPadding(14.dp, 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    })
                })
            })
        }

        content.addView(card().apply {
            addView(LinearLayout(this@MainActivity).apply {
                setPadding(18.dp, 18.dp, 18.dp, 18.dp)
                addView(bodyText("合計", muted = true, size = 15f).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                })
                addView(bodyText("¥${prefs.getInt("cart_total", 0)}", bold = true, size = 22f))
            })
        })

        addPrimaryButton("購入手続きへ") {
            startActivity(Intent(this, NetworkActivity::class.java))
        }
        addTextButton("バッグを空にする") {
            prefs.edit().remove("cart_items").putInt("cart_count", 0).putInt("cart_total", 0).apply()
            render()
        }
    }

    private fun renderOrders() {
        addTitle("注文履歴")
        val prefs = storePrefs()
        val orderId = prefs.getString("last_order_id", null)
        if (orderId == null) {
            addEmpty("📦", "まだ注文はありません", "ご注文後の配送状況をここで確認できます。")
        } else {
            addSubtitle("最近のご注文")
            content.addView(card().apply {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(18.dp, 18.dp, 18.dp, 18.dp)
                    addView(bodyText("注文番号  $orderId", bold = true, size = 16f))
                    addView(bodyText("発送準備中", color = R.color.success, bold = true, size = 14f).apply { setPadding(0, 8.dp, 0, 4.dp) })
                    addView(bodyText("合計 ¥${prefs.getInt("last_order_total", 0)}", muted = true, size = 14f))
                })
            })
        }

        addSection("ギフト")
        addActionCard("🎁", "デジタルギフトを贈る", "メッセージ付きコードを発行") {
            startActivity(Intent(this, CryptoActivity::class.java))
        }
    }

    private fun renderAccount() {
        addTitle("アカウント")
        val session = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val user = session.getString("username", null)
        val role = session.getString("role", "guest") ?: "guest"

        content.addView(card().apply {
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(18.dp, 18.dp, 18.dp, 18.dp)
                addView(bodyText(if (user == null) "👤" else "😊", size = 34f))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(14.dp, 0, 0, 0)
                    addView(bodyText(user ?: "ゲスト", bold = true, size = 18f))
                    addView(bodyText(membershipLabel(role), muted = true, size = 13f).apply { setPadding(0, 3.dp, 0, 0) })
                })
            })
        })

        addSection("会員情報")
        addActionCard("✏️", "プロフィールとログイン情報", "この端末に会員情報を保存") {
            startActivity(Intent(this, InsecureStorageActivity::class.java))
        }
        addActionCard("💳", "お支払い方法", "保存済みのお支払い情報を管理") {
            startActivity(Intent(this, SecureStorageActivity::class.java))
        }
        addActionCard("👛", "Squeeze Wallet", "ギフト残高と利用時の確認") {
            startActivity(Intent(this, KeystoreActivity::class.java))
        }

        addSection("ストア")
        addActionCard("🔐", "スタッフログイン", "商品の在庫と注文を管理") {
            startActivity(Intent(this, AuthActivity::class.java))
        }
    }

    private fun membershipLabel(role: String): String = when (role) {
        "admin", "staff" -> "スタッフアカウント"
        "gold" -> "ゴールド会員・20% OFF"
        else -> "レギュラー会員"
    }

    private fun updateCartBadge() {
        cartBadge.text = "バッグ ${storePrefs().getInt("cart_count", 0)}"
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 30f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.navy_900))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 10.dp, 0, 0)
        })
    }

    private fun addSubtitle(text: String) {
        content.addView(bodyText(text, muted = true, size = 14f).apply { setPadding(0, 6.dp, 0, 8.dp) })
    }

    private fun addSection(text: String) {
        content.addView(bodyText(text, bold = true, size = 18f).apply { setPadding(2.dp, 24.dp, 0, 10.dp) })
    }

    private fun addEmpty(mark: String, title: String, subtitle: String) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20.dp, 72.dp, 20.dp, 42.dp)
            addView(bodyText(mark, size = 52f))
            addView(bodyText(title, bold = true, size = 19f).apply { setPadding(0, 16.dp, 0, 6.dp) })
            addView(bodyText(subtitle, muted = true, size = 14f).apply { gravity = Gravity.CENTER })
        })
    }

    private fun addActionCard(mark: String, title: String, subtitle: String, action: () -> Unit) {
        content.addView(card().apply {
            isClickable = true
            isFocusable = true
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(17.dp, 15.dp, 14.dp, 15.dp)
                addView(bodyText(mark, size = 26f))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(14.dp, 0, 8.dp, 0)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(bodyText(title, bold = true, size = 15f))
                    addView(bodyText(subtitle, muted = true, size = 12f).apply { setPadding(0, 3.dp, 0, 0) })
                })
                addView(bodyText("›", color = R.color.blue_600, size = 28f))
            })
            setOnClickListener { action() }
        })
    }

    private fun addPrimaryButton(label: String, action: () -> Unit) {
        content.addView(MaterialButton(this).apply {
            text = label
            isAllCaps = false
            cornerRadius = 15.dp
            layoutParams = matchParams(top = 20)
            setOnClickListener { action() }
        })
    }

    private fun addTextButton(label: String, action: () -> Unit) {
        content.addView(MaterialButton(this).apply {
            text = label
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.slate_500))
            layoutParams = matchParams(top = 6)
            setOnClickListener { action() }
        })
    }

    private fun card() = MaterialCardView(this).apply {
        radius = 17.dp.toFloat()
        cardElevation = 0f
        strokeWidth = 1.dp
        strokeColor = ContextCompat.getColor(this@MainActivity, R.color.slate_200)
        setCardBackgroundColor(Color.WHITE)
        layoutParams = matchParams(bottom = 10)
    }

    private fun bodyText(
        value: String,
        bold: Boolean = false,
        muted: Boolean = false,
        color: Int? = null,
        size: Float = 14f,
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(ContextCompat.getColor(this@MainActivity, color ?: if (muted) R.color.slate_500 else R.color.navy_900))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun matchParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, top.dp, 0, bottom.dp)
        }

    private fun storePrefs() = getSharedPreferences("squeeze_store", Context.MODE_PRIVATE)
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
