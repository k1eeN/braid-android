package io.github.k1een.braid.sample.paging

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.k1een.braid.sample.R
import io.github.k1een.braid.sample.databinding.ItemPagingLoadStateBinding

internal class PagingLoadStateAdapter(private val retry: () -> Unit) :
    LoadStateAdapter<PagingLoadStateAdapter.LoadStateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder = LoadStateViewHolder(
        binding = ItemPagingLoadStateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        ),
        retry = retry
    )

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    internal class LoadStateViewHolder(private val binding: ItemPagingLoadStateBinding, retry: () -> Unit) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnRetry.setOnClickListener {
                retry()
            }
        }

        fun bind(loadState: LoadState) {
            val isError = loadState is LoadState.Error
            binding.progressBar.isVisible = loadState is LoadState.Loading
            binding.tvError.isVisible = isError
            binding.btnRetry.isVisible = isError

            if (isError) {
                binding.tvError.setText(R.string.paging_append_error)
            }
        }
    }
}
