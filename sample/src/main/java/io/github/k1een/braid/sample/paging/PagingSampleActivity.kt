package io.github.k1een.braid.sample.paging

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.k1een.braid.paging.braidPagingDataAdapter
import io.github.k1een.braid.sample.databinding.ActivityPagingSampleBinding
import io.github.k1een.braid.sample.databinding.ItemPagingBannerBinding
import io.github.k1een.braid.sample.databinding.ItemPagingUserBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PagingSampleActivity : AppCompatActivity() {

    private val viewModel: PagingSampleViewModel by viewModels()

    private val pagingAdapter = braidPagingDataAdapter<PagingSampleItem> {
        viewBinding(
            inflate = ItemPagingUserBinding::inflate,
            keySelector = PagingSampleItem.User::id,
        ) { item ->
            tvName.text = item.name
            tvDescription.text = item.description
        }

        statefulViewBinding(
            inflate = ItemPagingBannerBinding::inflate,
            keySelector = PagingSampleItem.Banner::id,
            stateFactory = {
                BannerState()
            },
            recycle = { state ->
                clearBannerAnimation(state)
                btnAnimate.setOnClickListener(null)
            },
            detachedFromWindow = { state ->
                clearBannerAnimation(state)
            },
        ) { item, state ->
            tvTitle.text = item.title
            clearBannerAnimation(state)
            btnAnimate.setOnClickListener {
                clearBannerAnimation(state)
                state.animation = ObjectAnimator.ofFloat(
                    accent,
                    View.ALPHA,
                    1f,
                    0.2f,
                    1f,
                ).apply {
                    duration = BANNER_ANIMATION_DURATION_MILLIS
                    start()
                }
            }
        }
    }

    private lateinit var binding: ActivityPagingSampleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPagingSampleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom,
            )
            insets
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.btnRefresh.setOnClickListener {
            pagingAdapter.refresh()
        }
        binding.btnInitialRetry.setOnClickListener {
            pagingAdapter.retry()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = pagingAdapter.withLoadStateHeaderAndFooter(
            header = PagingLoadStateAdapter(pagingAdapter::retry),
            footer = PagingLoadStateAdapter(pagingAdapter::retry),
        )

        collectPagingData()
    }

    private fun collectPagingData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collectLatest { pagingData ->
                        pagingAdapter.submitData(pagingData)
                    }
                }
                launch {
                    pagingAdapter.loadStateFlow.collectLatest(::renderLoadState)
                }
            }
        }
    }

    private fun renderLoadState(loadStates: CombinedLoadStates) {
        val refresh = loadStates.refresh
        val hasNoItems = pagingAdapter.itemCount == 0
        val showInitialLoading = hasNoItems && refresh is LoadState.Loading
        val showInitialError = hasNoItems && refresh is LoadState.Error
        val showEmpty = hasNoItems &&
            refresh is LoadState.NotLoading &&
            loadStates.append.endOfPaginationReached

        binding.progressInitial.isVisible = showInitialLoading
        binding.initialErrorContainer.isVisible = showInitialError
        binding.tvEmpty.isVisible = showEmpty
        binding.recyclerView.isVisible =
            !showInitialLoading && !showInitialError && !showEmpty
        binding.btnRefresh.isEnabled = refresh !is LoadState.Loading
    }

    private fun ItemPagingBannerBinding.clearBannerAnimation(
        state: BannerState,
    ) {
        state.animation?.cancel()
        state.animation = null
        accent.alpha = 1f
    }

    private companion object {
        const val BANNER_ANIMATION_DURATION_MILLIS: Long = 600L
    }
}

private class BannerState(
    var animation: ObjectAnimator? = null,
)
