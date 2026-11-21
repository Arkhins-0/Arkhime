package eu.kanade.tachiyomi.animesource.model

/**
 * How an anime is treated by library updates. Part of the Aniyomi extensions-lib API: anime
 * extensions set it on [SAnime.update_strategy], so the name and constants must not change.
 */
enum class AnimeUpdateStrategy {
    /** The anime is updated whenever the library is. */
    ALWAYS_UPDATE,

    /** Details and episodes are fetched once and never again, for completed works. */
    ONLY_FETCH_ONCE,
}
