package io.github.k1een.braid.sample.paging

internal sealed interface PagingSampleItem {

    data class User(val id: Long, val name: String, val description: String) : PagingSampleItem

    data class Banner(val id: Long, val title: String) : PagingSampleItem
}
