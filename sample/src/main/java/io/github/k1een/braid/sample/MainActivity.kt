package io.github.k1een.braid.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.k1een.braid.braidListAdapter
import io.github.k1een.braid.sample.databinding.ActivityMainBinding
import io.github.k1een.braid.sample.databinding.ItemSampleBinding
import io.github.k1een.braid.sample.list.SampleItem
import io.github.k1een.braid.sample.paging.PagingSampleActivity

class MainActivity : AppCompatActivity() {

    private val adapter = braidListAdapter<SampleItem> {
        viewBinding(
            inflate = ItemSampleBinding::inflate,
            keySelector = SampleItem::id,
        ) { item ->
            tvTitle.text = item.title
            tvDescription.text = item.description
            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.btnOpenPagingSample.setOnClickListener {
            startActivity(Intent(this, PagingSampleActivity::class.java))
        }

        adapter.submitList(sampleItems)
    }

    private fun onItemClick(item: SampleItem) {
        Toast.makeText(
            this,
            getString(R.string.item_selected, item.title),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private companion object {
        val sampleItems: List<SampleItem> = listOf(
            SampleItem(
                id = 1L,
                title = "ListAdapter foundation",
                description = "Uses AndroidX ListAdapter for immutable list updates and async diffing.",
            ),
            SampleItem(
                id = 2L,
                title = "Per-delegate diffing",
                description = "Each delegate owns identity, content comparison, and payload rules.",
            ),
            SampleItem(
                id = 3L,
                title = "Type-safe ViewBinding",
                description = "Bindings and item types stay explicit from creation through bind.",
            ),
            SampleItem(
                id = 4L,
                title = "Production diagnostics",
                description = "Missing and conflicting delegate registrations fail with clear errors.",
            ),
        )
    }
}
