package ani.arkhime.com.connections

import ani.arkhime.com.R
import ani.arkhime.com.Refresh
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.mal.MAL
import ani.arkhime.com.currContext
import ani.arkhime.com.media.Media
import ani.arkhime.com.media.emptyMedia
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ani.arkhime.com.media.MediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Sync all pending progress updates (cached during rescue mode) to AniList. */
fun syncPendingProgressUpdates() {

    if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) return
    if (Anilist.userid == null) return
    val pending: List<PendingProgressUpdate> = try {
        PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
    } catch (e: Exception) {
        PrefManager.setVal(PrefName.PendingProgressUpdates, listOf<PendingProgressUpdate>())
        return
    }
    if (pending.isEmpty()) return
    toast(currContext()?.getString(R.string.syncing_progress, pending.size))
    CoroutineScope(Dispatchers.IO).launch {
        val remaining = pending.toMutableList()
        for (update in pending) {
            try {
                
                val anilistId: Int = if (update.idMAL != null && update.mediaId == update.idMAL) {
                    val type = if (update.isAnime) "ANIME" else "MANGA"
                    val resolved = Anilist.query.getMedia(update.idMAL, mal = true, type = type)?.id
                    if (resolved == null) {
                        if (Anilist.anilistDisabledSignal) break
                        remaining.remove(update)
                        continue
                    }
                    resolved
                } else {
                    update.mediaId
                }
                Anilist.mutation.editList(
                    mediaID = anilistId,
                    progress = update.progress,
                    score = update.score,
                    repeat = update.rewatch,
                    notes = update.notes,
                    status = update.status,
                    private = update.isPrivate,
                    startedAt = update.startDate,
                    completedAt = update.endDate,
                    customList = update.customLists,
                )
                if (!Anilist.anilistDisabledSignal) {
                    remaining.remove(update)
                } else {
                    break
                }
            } catch (_: Exception) {
            }
        }
        PrefManager.setVal(PrefName.PendingProgressUpdates, remaining)
        if (remaining.isEmpty()) {
            toast(currContext()?.getString(R.string.sync_complete))
        } else {
            toast(currContext()?.getString(R.string.sync_partial, remaining.size))
        }
        Refresh.all()
    }
}

// sync changes to anilist in background 
fun syncPendingDeletions() {
    if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) return
    if (Anilist.userid == null) return
    val pending: List<PendingDeletion> = try {
        PrefManager.getVal(PrefName.PendingDeletions, listOf())
    } catch (e: Exception) {
        PrefManager.setVal(PrefName.PendingDeletions, listOf<PendingDeletion>())
        return
    }
    if (pending.isEmpty()) return
    toast(currContext()?.getString(R.string.syncing_deletions, pending.size))
    CoroutineScope(Dispatchers.IO).launch {
        val remaining = pending.toMutableList()
        for (deletion in pending) {
            if (Anilist.anilistDisabledSignal) break
            try {
                val anilistId: Int = if (deletion.idMAL != null && deletion.mediaId == deletion.idMAL) {
                    val type = if (deletion.isAnime) "ANIME" else "MANGA"
                    val resolved = Anilist.query.getMedia(deletion.idMAL, mal = true, type = type)?.id
                    if (resolved == null) {
                        if (Anilist.anilistDisabledSignal) break  // AniList down — abort entire sync
                        remaining.remove(deletion)
                        continue
                    }
                    resolved
                } else {
                    deletion.mediaId
                }
                val fakeMedia = emptyMedia().copy(id = anilistId, idMAL = deletion.idMAL)
                val listId = Anilist.query.userMediaDetails(fakeMedia).userListId
                if (listId != null) {
                    Anilist.mutation.deleteList(listId)
                }
                val removeList = PrefManager.getCustomVal<Set<String>>("removeList", emptySet())
                PrefManager.setCustomVal("removeList", removeList.minus(anilistId.toString()))
                val progressUpdates: List<PendingProgressUpdate> =
                    PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
                val filteredUpdates = progressUpdates.filterNot { update ->
                    update.mediaId == deletion.mediaId ||
                        (deletion.idMAL != null && update.idMAL == deletion.idMAL && update.mediaId == update.idMAL)
                }
                if (filteredUpdates.size != progressUpdates.size) {
                    PrefManager.setVal(PrefName.PendingProgressUpdates, filteredUpdates)
                }
                if (!Anilist.anilistDisabledSignal) {
                    remaining.remove(deletion)
                }
            } catch (_: Exception) {
            }
        }
        PrefManager.setVal(PrefName.PendingDeletions, remaining)
        if (remaining.isEmpty()) {
            toast(currContext()?.getString(R.string.sync_complete))
        } else {
            toast(currContext()?.getString(R.string.sync_partial, remaining.size))
        }

        Refresh.all()
    }
}
