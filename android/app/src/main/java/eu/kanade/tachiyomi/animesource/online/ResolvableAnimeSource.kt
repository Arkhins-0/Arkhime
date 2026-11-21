package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * A source that can open an anime or episode from a link to its site.
 *
 * Part of the Aniyomi extensions-lib API (lib 14). Extensions that implement it can't load at all
 * unless the app has this interface, even though Arkhime doesn't open links through it yet.
 */
interface ResolvableAnimeSource : AnimeSource {

    /** What kind of page [uri] points at. */
    fun getUriType(uri: String): UriType

    /** The anime at [uri], or null if it isn't one. */
    suspend fun getAnime(uri: String): SAnime?

    /** The episode at [uri], or null if it isn't one. */
    suspend fun getEpisode(uri: String): SEpisode?
}

sealed interface UriType {
    data object Anime : UriType
    data object Episode : UriType
    data object Unknown : UriType
}
