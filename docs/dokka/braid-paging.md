# Module braid-paging

Optional Paging 3 integration for Braid delegates.

# Package io.github.k1een.braid.paging

Provides `BraidPagingDataAdapter`, which inherits the standard
`PagingDataAdapter` APIs for data submission, retry, refresh, load states,
snapshots, and `LoadStateAdapter` composition while sharing Braid's delegate
routing and diff rules.

Paging placeholders are not supported. Configure `PagingConfig` with
`enablePlaceholders = false`.
