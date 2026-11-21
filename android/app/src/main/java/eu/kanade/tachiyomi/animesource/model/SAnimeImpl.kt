package eu.kanade.tachiyomi.animesource.model

class SAnimeImpl : SAnime {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var initialized: Boolean = false

    // Stored under its own name: anime saved before this used the manga UpdateStrategy type for
    // update_strategy, and reading that back skips the old field and leaves this null.
    private var updateStrategy: AnimeUpdateStrategy? = null

    override var update_strategy: AnimeUpdateStrategy
        get() = updateStrategy ?: AnimeUpdateStrategy.ALWAYS_UPDATE
        set(value) {
            updateStrategy = value
        }

    companion object {
        // Pinned to the value from before update_strategy changed type, so anime saved with Java
        // serialization still read back.
        private const val serialVersionUID = 2758411142803207100L
    }
}
