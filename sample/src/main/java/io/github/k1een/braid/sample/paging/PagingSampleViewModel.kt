package io.github.k1een.braid.sample.paging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

internal class PagingSampleViewModel : ViewModel() {

    val items: Flow<PagingData<PagingSampleItem>> = Pager(
        config = PagingConfig(
            pageSize = PagingSampleSource.PAGE_SIZE,
            initialLoadSize = PagingSampleSource.PAGE_SIZE,
            prefetchDistance = 5,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            PagingSampleSource()
        },
    ).flow.cachedIn(viewModelScope)
}
