package ani.arkhime.com.media.manga

import ani.arkhime.com.media.MediaDetailsViewModel
import ani.arkhime.com.media.SourceAdapter
import ani.arkhime.com.media.SourceSearchDialogFragment
import ani.arkhime.com.parsers.ShowResponse
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