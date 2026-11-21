package com.arkhins.arkhime.media.manga

import com.arkhins.arkhime.media.MediaDetailsViewModel
import com.arkhins.arkhime.media.SourceAdapter
import com.arkhins.arkhime.media.SourceSearchDialogFragment
import com.arkhins.arkhime.parsers.ShowResponse
import kotlinx.coroutines.CoroutineScope

class MangaSourceAdapter(
    sources: List<ShowResponse>,
    val model: MediaDetailsViewModel,
    val i: Int,
    val id: Int,
    fragment: SourceSearchDialogFragment,
    scope: CoroutineScope
) : SourceAdapter(sources, fragment, scope) {
    override suspend fun onItemClick(source: ShowResponse) {
        model.overrideMangaChapters(i, source, id)
    }
}