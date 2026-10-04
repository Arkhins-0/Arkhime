package ani.arkhime.com.notifications.updates

import android.content.Context
import androidx.annotation.StringRes
import ani.arkhime.com.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Kinds of update the Updates tab shows and that can be pushed (each one switchable in settings). */
enum class UpdateType(@StringRes val label: Int, val siteWide: Boolean) {
    ANIME_ADDED(R.string.update_anime_added, true),
    MANGA_ADDED(R.string.update_manga_added, true),
    PREMIERE(R.string.update_premiere, true),
    EPISODE(R.string.update_episode, true),
    FINISHED(R.string.update_finished, true),
    RELATED(R.string.update_related, false),
    DATA_CHANGE(R.string.update_data_change, false),
    MERGE(R.string.update_merge, false),
    DELETION(R.string.update_deletion, false);

    companion object {
        /** Pushed out of the box; site-wide episodes and new manga are noisy, so they start off */
        val defaultAlerts = setOf(ANIME_ADDED, PREMIERE, FINISHED, RELATED, DATA_CHANGE, MERGE, DELETION)
            .map { it.name }.toSet()
    }
}

@Serializable
data class UpdateEvent(
    /** Unique per real-world event, so re-fetching never duplicates it */
    val key: String,
    val type: UpdateType,
    /** Epoch seconds */
    val time: Long,
    val mediaId: Int,
    val anime: Boolean,
    val title: String,
    val text: String,
    val cover: String? = null,
    val banner: String? = null,
    /** Episodes/chapters, so "Completed" can fill in progress */
    val total: Int? = null,
    /** The viewer's list status when the event was seen, null when not on the list */
    val listStatus: String? = null,
)

/** The Updates feed, kept on the device (newest first, capped). */
object UpdateStore {
    private const val FILE = "updates_feed.json"
    private const val MAX_EVENTS = 3000
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    fun all(context: Context): List<UpdateEvent> = synchronized(lock) { read(context) }

    /** Stores [events] and returns the ones that weren't stored before. */
    fun add(context: Context, events: List<UpdateEvent>): List<UpdateEvent> = synchronized(lock) {
        val current = read(context)
        val known = current.mapTo(HashSet()) { it.key }
        val fresh = events.distinctBy { it.key }.filter { it.key !in known }
        if (fresh.isNotEmpty()) {
            val merged = (fresh + current).sortedByDescending { it.time }.take(MAX_EVENTS)
            File(context.filesDir, FILE).writeText(json.encodeToString(merged))
        }
        fresh
    }

    private fun read(context: Context): List<UpdateEvent> {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<UpdateEvent>>(file.readText()) }.getOrDefault(emptyList())
    }
}
