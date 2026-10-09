package com.talhaphysics.academy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // 0 = "More" (other pages + blog posts)
    private val tabs = mapOf(
        R.id.nav_9 to 9, R.id.nav_10 to 10, R.id.nav_11 to 11, R.id.nav_12 to 12, R.id.nav_more to 0
    )
    private var tab = R.id.nav_9
    private var shown: List<Item> = emptyList()
    private var loading = false

    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var status: TextView
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        refresh = findViewById(R.id.refresh)
        status = findViewById(R.id.status)
        search = findViewById(R.id.search)
        val list = findViewById<ListView>(R.id.list)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            startActivity(Intent(this, DetailActivity::class.java).putExtra("url", shown[pos].url))
        }

        findViewById<BottomNavigationView>(R.id.nav).setOnItemSelectedListener {
            tab = it.itemId
            search.setText("")
            show()
            true
        }
        search.doAfterTextChanged { show() }
        refresh.setOnRefreshListener { load() }

        Repo.loadCache(this)
        show()
        load()
    }

    private fun load() {
        loading = true
        refresh.isRefreshing = true
        show()
        lifecycleScope.launch {
            val ok = Repo.refresh(this@MainActivity)
            loading = false
            refresh.isRefreshing = false
            if (!ok && Repo.all().isNotEmpty()) {
                Toast.makeText(this@MainActivity, R.string.offline_toast, Toast.LENGTH_SHORT).show()
            }
            show()
        }
    }

    private fun show() {
        val n = tabs[tab] ?: 9
        val base = if (n == 0) Repo.moreItems() else Repo.classItems(n)
        val q = search.text.toString().trim()
        shown = if (q.isEmpty()) base else base.filter { it.title.contains(q, ignoreCase = true) }

        adapter.clear()
        adapter.addAll(shown.map { it.title })

        status.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        status.setText(
            when {
                loading && Repo.all().isEmpty() -> R.string.loading
                Repo.all().isEmpty() -> R.string.load_failed
                else -> R.string.nothing_found
            }
        )
    }
}
