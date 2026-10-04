package ani.arkhime.com.media.user

import ani.arkhime.com.connections.anilist.api.FuzzyDate
import ani.arkhime.com.media.Media

/**
 * A pending, unsaved change to one list entry. Only the fields the user touched are set,
 * so a patch is always a minimal diff against what AniList has.
 */
data class EntryPatch(
    val status: String? = null,
    val progress: Int? = null,
    /** POINT_100, like [Media.userScore] */
    val score: Int? = null,
    val startedAt: FuzzyDate? = null,
    val completedAt: FuzzyDate? = null,
) {
    /** Values from [other] win over this patch's */
    infix fun then(other: EntryPatch) = EntryPatch(
        status = other.status ?: status,
        progress = other.progress ?: progress,
        score = other.score ?: score,
        startedAt = other.startedAt ?: startedAt,
        completedAt = other.completedAt ?: completedAt,
    )
}

val Media.isOnList: Boolean get() = userStatus != null
val Media.totalUnits: Int? get() = anime?.totalEpisodes ?: manga?.totalChapters

fun Media.statusWith(patch: EntryPatch?): String? = patch?.status ?: userStatus
fun Media.progressWith(patch: EntryPatch?): Int = patch?.progress ?: userProgress ?: 0
fun Media.scoreWith(patch: EntryPatch?): Int = patch?.score ?: userScore

/**
 * Applies [change] on top of [current] the way AniList's own editor does: the first episode
 * starts a planned show, the last one completes it, completing fills in progress, and the
 * start/finish dates are stamped when missing. Returns null when nothing differs from AniList
 * any more, so nudging a value and putting it back leaves no phantom change.
 */
fun Media.applyPatch(current: EntryPatch?, change: EntryPatch, today: FuzzyDate): EntryPatch? {
    val merged = (current ?: EntryPatch()) then change
    val total = totalUnits
    val savedProgress = userProgress ?: 0
    val nextProgress = merged.progress ?: savedProgress
    val nextStatus = merged.status ?: userStatus
    val started = userStartedAt.year != null
    val finished = userCompletedAt.year != null
    var extra = EntryPatch()

    if (change.progress != null && savedProgress == 0 && nextProgress > 0 &&
        (nextStatus == null || nextStatus == "PLANNING")
    ) {
        extra = extra then EntryPatch(status = "CURRENT", startedAt = today.takeUnless { started })
    }
    if (change.progress != null && total != null && nextProgress >= total &&
        nextStatus != "COMPLETED" && nextStatus != "REPEATING"
    ) {
        extra = extra then EntryPatch(status = "COMPLETED", completedAt = today.takeUnless { finished })
    }
    if (change.status == "COMPLETED") {
        extra = extra then EntryPatch(
            progress = total?.takeIf { nextProgress < it },
            completedAt = today.takeUnless { finished },
        )
    }
    if ((change.status == "CURRENT" || change.status == "REPEATING") && !started) {
        extra = extra then EntryPatch(startedAt = today)
    }

    val next = merged then extra
    val diff = EntryPatch(
        status = next.status?.takeIf { it != userStatus },
        progress = next.progress?.takeIf { it != savedProgress },
        score = next.score?.takeIf { it != userScore },
    )
    // Dates only ever ride along with a real change
    if (diff.status == null && diff.progress == null && diff.score == null) return null
    return diff.copy(startedAt = next.startedAt, completedAt = next.completedAt)
}

/** One franchise: seasons, movies and side stories, oldest first. */
class Series(val id: Int, val entries: List<Media>) {
    val title: String get() = entries.first().userPreferredName
    val onList: Int get() = entries.count { it.isOnList }
    val notOnList: Int get() = entries.size - onList

    /** Share of the series finished, 0..1, counting pending edits */
    fun completion(edits: Map<Int, EntryPatch>): Float {
        var done = 0
        var total = 0
        for (media in entries) {
            val units = media.totalUnits ?: continue
            total += units
            done += media.progressWith(edits[media.id]).coerceAtMost(units)
        }
        return if (total == 0) 0f else done.toFloat() / total
    }
}

// Relations that keep titles in one series. ADAPTATION, CHARACTER, SOURCE and OTHER are left out
// so unrelated titles don't merge.
private val SAME_SERIES = setOf(
    "SEQUEL", "PREQUEL", "SIDE_STORY", "SPIN_OFF", "PARENT",
    "ALTERNATIVE", "SUMMARY", "COMPILATION", "CONTAINS",
)

/** Ids of titles in the same series as this one */
fun Media.seriesRelatedIds(): List<Int> =
    seriesLinks.orEmpty().filter { it.second in SAME_SERIES }.map { it.first }

/**
 * Clusters the whole collection into series (union-find over relation links). Related titles that
 * aren't on the list join their series when their details are in [notOnList]. Returns each media
 * id's series.
 */
fun buildSeries(collection: List<Media>, notOnList: Map<Int, Media>): Map<Int, Series> {
    val byId = LinkedHashMap<Int, Media>()
    collection.forEach { byId.putIfAbsent(it.id, it) }
    collection.forEach { media ->
        media.seriesRelatedIds().forEach { id -> notOnList[id]?.let { byId.putIfAbsent(id, it) } }
    }

    val parent = HashMap<Int, Int>()
    fun find(x: Int): Int {
        var root = x
        while (parent.getOrPut(root) { root } != root) root = parent.getValue(root)
        var cur = x
        while (cur != root) {
            val next = parent.getValue(cur)
            parent[cur] = root
            cur = next
        }
        return root
    }
    collection.forEach { media ->
        media.seriesRelatedIds().filter { it in byId }.forEach { other ->
            val a = find(media.id)
            val b = find(other)
            if (a != b) parent[a] = b
        }
    }

    val oldestFirst = compareBy<Media, FuzzyDate?>(nullsLast()) { it.startDate?.takeIf { d -> d.year != null } }
        .thenBy { it.userPreferredName }
    val result = HashMap<Int, Series>()
    byId.values.groupBy { find(it.id) }.values.forEach { members ->
        val sorted = members.sortedWith(oldestFirst)
        val series = Series(sorted.minOf { it.id }, sorted)
        sorted.forEach { result[it.id] = series }
    }
    return result
}

/** The series a tab touches, in the tab's own order (so the sort menu still applies). */
fun seriesInTab(tab: List<Media>, index: Map<Int, Series>): List<Series> =
    tab.mapNotNull { index[it.id] }.distinctBy { it.id }
