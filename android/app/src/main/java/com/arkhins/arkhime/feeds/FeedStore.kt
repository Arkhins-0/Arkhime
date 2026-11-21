package com.arkhins.arkhime.feeds

import android.content.Context
import com.arkhins.arkhime.feeds.nyaa.NyaaCategory
import com.arkhins.arkhime.feeds.nyaa.NyaaSite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class FeedSource(val label: String) {
    NYAA("nyaa.si"), SUKEBEI("sukebei.nyaa.si"), CRUNCHYROLL("Crunchyroll");

    /** The nyaa site behind this source, or null for Crunchyroll. */
    val nyaaSite get() = when (this) {
        NYAA -> NyaaSite.NYAA
        SUKEBEI -> NyaaSite.SUKEBEI
        CRUNCHYROLL -> null
    }

    companion object {
        fun of(site: NyaaSite) = if (site == NyaaSite.SUKEBEI) SUKEBEI else NYAA
    }
}

/**
 * A keyword watch: every keyword has to appear in a
 * release for it to count. Nyaa feeds also remember the category and filter they run with.
 */
@Serializable
data class TrackedFeed(
    val id: Long = System.currentTimeMillis(),
    val source: FeedSource,
    val keywords: List<String>,
    val category: String? = null,
    val filter: Int = 0,
    val notify: Boolean = true,
    /** Epoch millis of the newest release already seen, so only newer ones notify. */
    val lastSeen: Long = System.currentTimeMillis(),
    val newCount: Int = 0,
) {
    val query get() = keywords.joinToString(" ")

    /** The nyaa category this feed searches, falling back to the default for the feed's site. */
    val nyaaCategory: NyaaCategory
        get() {
            val site = source.nyaaSite ?: NyaaSite.NYAA
            val c = category?.let { NyaaCategory.from(it) }
            return if (c != null && c.site == site) c else NyaaCategory.defaultFor(site)
        }
    val label get() = keywords.joinToString(" · ")

    fun matches(vararg titles: String?): Boolean = titles.filterNotNull().any { title ->
        keywords.all { title.contains(it, ignoreCase = true) }
    }
}

object FeedStore {
    private const val PREFS = "arkhime_tracked_feeds"
    private const val KEY = "feeds"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parseKeywords(input: String): List<String> =
        input.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    fun all(context: Context): List<TrackedFeed> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { json.decodeFromString<List<TrackedFeed>>(raw) }.getOrDefault(emptyList())
    }

    fun of(context: Context, source: FeedSource) = all(context).filter { it.source == source }

    @Synchronized
    fun save(context: Context, feeds: List<TrackedFeed>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(feeds)).apply()
        FeedCheckWorker.schedule(context, feeds.any { it.notify })
    }

    fun add(context: Context, feed: TrackedFeed) {
        val existing = all(context)
        if (existing.any { it.source == feed.source && it.category == feed.category &&
                    it.keywords.map(String::lowercase).toSet() == feed.keywords.map(String::lowercase).toSet() }
        ) return
        save(context, existing + feed)
    }

    fun update(context: Context, feed: TrackedFeed) =
        save(context, all(context).map { if (it.id == feed.id) feed else it })

    fun remove(context: Context, id: Long) = save(context, all(context).filterNot { it.id == id })

    fun clearNew(context: Context, id: Long) {
        val feeds = all(context)
        if (feeds.any { it.id == id && it.newCount > 0 }) {
            save(context, feeds.map { if (it.id == id) it.copy(newCount = 0) else it })
        }
    }
}
