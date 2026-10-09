package com.talhaphysics.academy

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import org.jsoup.Jsoup

/** Shows one page/post: its YouTube lectures, its links, and its text, all taken from the blog. */
class DetailActivity : AppCompatActivity() {

    private val youtube =
        Regex("""(?:youtube(?:-nocookie)?\.com/(?:embed/|watch\?v=|shorts/)|youtu\.be/)([\w-]{11})""")
    private val imageLink =
        Regex("""\.(jpe?g|png|gif|webp)(\?|#|\z)""", RegexOption.IGNORE_CASE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.loadCache(this)
        val item = Repo.find(intent.getStringExtra("url").orEmpty())
        if (item == null) {
            finish()
            return
        }
        title = item.title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(box) })

        val doc = Jsoup.parse(item.html, Repo.BASE)

        // 1) YouTube lectures (embedded players and plain links)
        val videos = LinkedHashMap<String, String>()
        doc.select("iframe[src]").forEach { f ->
            youtube.find(f.attr("src"))?.groupValues?.get(1)?.let {
                videos.putIfAbsent(it, "Lecture ${videos.size + 1}")
            }
        }
        doc.select("a[href]").forEach { a ->
            youtube.find(a.absUrl("href"))?.groupValues?.get(1)?.let {
                videos.putIfAbsent(it, a.text().trim().ifBlank { "Lecture ${videos.size + 1}" })
            }
        }
        if (videos.isNotEmpty()) {
            box.addView(header("Video lectures"))
            videos.forEach { (id, label) ->
                box.addView(row("▶  $label") { openUrl("https://www.youtube.com/watch?v=$id") })
            }
        }

        // 2) Other links (notes, PDFs, MCQs, other pages of the blog)
        val seen = HashSet<String>()
        val links = doc.select("a[href]").mapNotNull { a ->
            val href = a.absUrl("href")
            when {
                !href.startsWith("http") -> null
                youtube.containsMatchIn(href) -> null
                imageLink.containsMatchIn(href) -> null
                "googleusercontent.com" in href -> null
                !seen.add(href) -> null
                else -> a.text().trim().ifBlank { Uri.parse(href).host.orEmpty() } to href
            }
        }
        if (links.isNotEmpty()) {
            box.addView(header("Notes & links"))
            links.forEach { (label, href) ->
                box.addView(row(label) {
                    val target = Repo.find(href)
                    if (target != null) {
                        startActivity(Intent(this, DetailActivity::class.java).putExtra("url", target.url))
                    } else openUrl(href)
                })
            }
        }

        // 3) The written text
        doc.select("iframe,script,style,img,noscript").remove()
        val text = HtmlCompat.fromHtml(doc.body().html(), HtmlCompat.FROM_HTML_MODE_COMPACT)
        if (text.toString().isNotBlank()) {
            box.addView(header("Details"))
            box.addView(TextView(this).apply {
                this.text = text
                textSize = 16f
                movementMethod = LinkMovementMethod.getInstance()
            })
        }

        box.addView(header(""))
        box.addView(row("Open on website") { openUrl(item.url) })
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun row(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 16f
        setPadding(0, dp(12), 0, dp(12))
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        setBackgroundResource(tv.resourceId)
        setOnClickListener { onClick() }
    }

    private fun openUrl(u: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: Exception) { /* no app can open it */ }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
