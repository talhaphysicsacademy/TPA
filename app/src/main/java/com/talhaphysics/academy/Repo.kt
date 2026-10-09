package com.talhaphysics.academy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Item(val title: String, val url: String, val html: String, val isPage: Boolean) {
    val slug: String get() = url.substringAfterLast('/').substringBefore('.')

    // "class-9-unit-2-kinematics" -> 9. Posts never belong to a class tab.
    val classNo: Int?
        get() = if (isPage) Regex("^class-(9|10|11|12)-").find(slug)?.groupValues?.get(1)?.toInt() else null
}

/** Reads the blog's public Blogger feeds and keeps a copy on disk for offline use. */
object Repo {
    const val BASE = "https://talhaphysicsacademy.blogspot.com"
    private const val PAGES_FEED = "$BASE/feeds/pages/default?alt=json&max-results=500"
    private const val POSTS_FEED = "$BASE/feeds/posts/default?alt=json&max-results=100"

    @Volatile var pages: List<Item> = emptyList()
    @Volatile var posts: List<Item> = emptyList()

    fun all() = pages + posts

    fun classItems(n: Int) = pages.filter { it.classNo == n }.sortedBy { it.slug }

    fun moreItems() = pages.filter { it.classNo == null && it.slug != "welcome" } + posts

    fun find(url: String): Item? = all().firstOrNull { clean(it.url) == clean(url) }

    private fun clean(u: String) =
        u.substringBefore('#').substringBefore('?')
            .removePrefix("http://").removePrefix("https://").trimEnd('/')

    fun loadCache(ctx: Context) {
        if (pages.isNotEmpty() || posts.isNotEmpty()) return
        pages = read(File(ctx.filesDir, "pages.json"), true)
        posts = read(File(ctx.filesDir, "posts.json"), false)
    }

    /** Downloads fresh data. Returns false if the network failed (cached data stays in use). */
    suspend fun refresh(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val p = download(PAGES_FEED)
            val q = download(POSTS_FEED)
            val newPages = parse(p, true)
            val newPosts = parse(q, false)
            File(ctx.filesDir, "pages.json").writeText(p)
            File(ctx.filesDir, "posts.json").writeText(q)
            pages = newPages
            posts = newPosts
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun download(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun read(f: File, isPage: Boolean): List<Item> =
        if (f.exists()) {
            try { parse(f.readText(), isPage) } catch (e: Exception) { emptyList() }
        } else emptyList()

    private fun parse(json: String, isPage: Boolean): List<Item> {
        val entries = JSONObject(json).getJSONObject("feed").optJSONArray("entry") ?: return emptyList()
        return (0 until entries.length()).map { i ->
            val e = entries.getJSONObject(i)
            var url = ""
            val links = e.optJSONArray("link")
            if (links != null) {
                for (j in 0 until links.length()) {
                    val l = links.getJSONObject(j)
                    if (l.optString("rel") == "alternate") url = l.optString("href")
                }
            }
            Item(
                title = e.optJSONObject("title")?.optString("\$t").orEmpty(),
                url = url,
                html = e.optJSONObject("content")?.optString("\$t").orEmpty(),
                isPage = isPage
            )
        }.filter { it.url.isNotEmpty() }
    }
}
