package ani.arkhime.com.media.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.FuzzyDate
import ani.arkhime.com.connections.mal.MAL
import ani.arkhime.com.media.Media
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.tryWithSuspend
import kotlinx.coroutines.delay

class ListViewModel : ViewModel() {
    var grid = MutableLiveData(PrefManager.getVal<Boolean>(PrefName.ListGrid))

    /** Show each tab as series (sequels, prequels, side stories together) instead of single titles */
    val groupBySeries = MutableLiveData(PrefManager.getVal<Boolean>(PrefName.ListGroupBySeries))
    /** Batch edit mode: change many entries, then save them in one go */
    val editMode = MutableLiveData(false)
    /** Unsaved changes by media id */
    val edits = MutableLiveData<Map<Int, EntryPatch>>(emptyMap())
    /** (saved so far, total) while a batch save runs, null otherwise */
    val saveProgress = MutableLiveData<Pair<Int, Int>?>(null)

    /** Media id -> series, built from the whole collection when grouping is on */
    @Volatile
    var seriesIndex: Map<Int, Series> = emptyMap()
        private set
    /** Details of related titles that aren't on the list, kept across reloads */
    private val notOnList = java.util.concurrent.ConcurrentHashMap<Int, Media>()

    private val lists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    private val unfilteredLists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    fun getLists(): LiveData<MutableMap<String, ArrayList<Media>>> = lists
    suspend fun loadLists(anime: Boolean, userId: Int, sortOrder: String? = null) {
        val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
        if (rescueMode) {
            loadListsFromMAL(anime)
            return
        }
        tryWithSuspend {
            val grouped = groupBySeries.value == true
            val res = Anilist.query.getMediaLists(anime, userId, sortOrder, withRelations = grouped)
            val collection = res["All"].orEmpty()
            seriesIndex = if (grouped) buildSeries(collection, notOnList) else emptyMap()
            lists.postValue(res)
            unfilteredLists.postValue(res)
            if (grouped && loadNotOnList(collection)) {
                seriesIndex = buildSeries(collection, notOnList)
                lists.postValue(lists.value ?: res)
            }
        }
    }
    /** Fetches related titles missing from the list, 50 per request. True when any arrived. */
    private suspend fun loadNotOnList(collection: List<Media>): Boolean {
        val onList = collection.mapTo(HashSet()) { it.id }
        val missing = collection.flatMap { it.seriesRelatedIds() }
            .filter { it !in onList && !notOnList.containsKey(it) }
            .distinct()
        var added = false
        for (chunk in missing.chunked(50)) {
            val found = Anilist.query.getMediaList(chunk) ?: break
            found.filter { it.userStatus == null }.forEach { notOnList[it.id] = it }
            added = added || found.isNotEmpty()
        }
        return added
    }

    private suspend fun loadListsFromMAL(anime: Boolean) {
        tryWithSuspend {
            val statuses = if (anime)
                listOf("watching" to "Watching", "completed" to "Completed", "plan_to_watch" to "Planned",
                    "on_hold" to "Paused", "dropped" to "Dropped")
            else
                listOf("reading" to "Reading", "completed" to "Completed", "plan_to_read" to "Planned",
                    "on_hold" to "Paused", "dropped" to "Dropped")

            val result = mutableMapOf<String, ArrayList<Media>>()
            for ((malStatus, label) in statuses) {
                var offset = 0
                val limit = 1000
                val mediaList = ArrayList<Media>()
                var hasNext = true
                while (hasNext) {
                    val response = if (anime)
                        MAL.query.getUserAnimeList(status = malStatus, limit = limit, offset = offset)
                    else
                        MAL.query.getUserMangaList(status = malStatus, limit = limit, offset = offset)

                    response?.data?.let { entries ->
                        mediaList.addAll(entries.map { Media(it, anime) })
                    }
                    if (response?.paging?.next != null) {
                        offset += limit
                    } else {
                        hasNext = false
                    }
                }
                if (mediaList.isNotEmpty()) {
                    result[label] = mediaList
                }
            }
            lists.postValue(result)
            unfilteredLists.postValue(result)
        }
    }

    fun filterLists(genre: String) {
        if (genre == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                genre in media.genres
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

    fun filterListsByTag(tag: String) {
        if (tag == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                tag in media.tags
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

    fun getAllTags(): List<String> {
        val allMedia = unfilteredLists.value?.values?.flatten() ?: return emptyList()
        return allMedia.flatMap { it.tags }.distinct().sorted()
    }

    fun getAllGenres(): List<String> {
        val allMedia = unfilteredLists.value?.values?.flatten() ?: return emptyList()
        val listGenres = allMedia.flatMap { it.genres }.distinct().sorted()
        return if (listGenres.isNotEmpty()) listGenres else PrefManager.getVal<Set<String>>(PrefName.GenresList).sorted()
    }

    fun searchLists(search: String) {
        if (search.isEmpty()) {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                media.name?.contains(
                    search,
                    ignoreCase = true
                ) == true || media.synonyms.any { it.contains(search, ignoreCase = true) } ||
                        media.nameRomaji.contains(
                            search,
                            ignoreCase = true
                        )
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

    fun unfilterLists() {
        lists.postValue(unfilteredLists.value)
    }

    /** Records a change to [media], with AniList's automatic status/date rules applied */
    fun patch(media: Media, change: EntryPatch) {
        val current = edits.value.orEmpty()
        val next = media.applyPatch(current[media.id], change, FuzzyDate().getToday())
        val updated = current.toMutableMap()
        if (next == null) updated.remove(media.id) else updated[media.id] = next
        edits.value = updated
    }

    fun discardEdits() {
        edits.value = emptyMap()
    }

    /**
     * Saves every pending change, one entry at a time, spaced to stay under AniList's rate limit
     * (waiting it out and retrying when it is hit). Failed entries stay pending.
     * Returns (saved, failed).
     */
    suspend fun saveEdits(): Pair<Int, Int> {
        val pending = edits.value.orEmpty().toList()
        val remaining = pending.toMap().toMutableMap()
        var saved = 0
        saveProgress.postValue(0 to pending.size)
        for ((index, entry) in pending.withIndex()) {
            val (mediaId, patch) = entry
            var ok = false
            var attempts = 0
            while (!ok && attempts < 4) {
                attempts++
                val waitMs = Anilist.rateLimitReset * 1000 - System.currentTimeMillis()
                if (waitMs > 0) delay(waitMs + 1000)
                ok = Anilist.mutation.saveListEntry(
                    mediaId = mediaId,
                    status = patch.status,
                    progress = patch.progress,
                    scoreRaw = patch.score,
                    startedAt = patch.startedAt,
                    completedAt = patch.completedAt,
                )
                // Only a rate limit is worth retrying; anything else is reported as failed
                if (!ok && Anilist.rateLimitReset * 1000 <= System.currentTimeMillis()) break
            }
            if (ok) {
                saved++
                remaining.remove(mediaId)
            }
            saveProgress.postValue(index + 1 to pending.size)
            if (index < pending.lastIndex) {
                // AniList allows 30 requests a minute (90 when not degraded): go fast while there is
                // headroom, then settle at about one save every two seconds
                val left = Anilist.rateLimitRemaining
                delay(
                    when {
                        left < 0 -> 2000L
                        left > 20 -> 300L
                        left > 10 -> 1000L
                        else -> 2100L
                    }
                )
            }
        }
        if (saved > 0) {
            Anilist.query.invalidateUserStatusCache()
            Anilist.query.invalidateHomePageCache()
        }
        edits.postValue(remaining)
        saveProgress.postValue(null)
        return saved to pending.size - saved
    }

}
