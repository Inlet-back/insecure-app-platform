package com.example.insecureappplatform

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<LinearLayout>(R.id.chapter_container)

        // 境界ごとに見出しを付けて並べる。エントリの追加は Chapters.all だけで済む。
        Chapters.all.groupBy { it.boundary }.forEach { (boundary, chapters) ->
            container.addView(TextView(this).apply {
                text = boundary
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 32, 0, 8)
            })
            chapters.forEach { chapter ->
                container.addView(Button(this).apply {
                    text = "[${chapter.layer}] ${chapter.title}"
                    isAllCaps = false
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, chapter.activityClass))
                    }
                })
            }
        }
    }
}
