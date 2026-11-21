package com.arkhins.arkhime.connections

import com.arkhins.arkhime.R
import com.arkhins.arkhime.Refresh
import com.arkhins.arkhime.connections.anilist.Anilist
import com.arkhins.arkhime.connections.mal.MAL
import com.arkhins.arkhime.currContext
import com.arkhins.arkhime.media.Media
import com.arkhins.arkhime.media.emptyMedia
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.arkhins.arkhime.media.MediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Saves episode/chapter progress after watching or reading (restored from Dantotsu).
 * In rescue mode the update is queued in [PrefName.PendingProgressUpdates] and synced later
 * by [syncPendingProgressUpdates], instead of being sent to AniList straight away.
 */
fun updateProgress(media: Media, number: String) {
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)
    if (incognito) {
        toast("Sneaky sneaky :3")
        return
    }
    if (Anilist.userid == null) {
        toast(currContext()?.getString(R.string.login_anilist_account))
        return
    }
    val progress = number.toFloatOrNull()?.toInt() ?: return
    if (progress <= (media.userProgress ?: -1)) return
    val status = if (media.userStatus == "REPEATING") "REPEATING" else "CURRENT"
    media.userProgress = progress
    CoroutineScope(Dispatchers.IO).launch {
        if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
            val pending = PendingProgressUpdate(
                mediaId = media.id,
                idMAL = media.idMAL,
                isAnime = media.anime != null,
                progress = progress,
                status = status,
                isPrivate = media.isListPrivate,
            )
            val existing: List<PendingProgressUpdate> =
                PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
            PrefManager.setVal(
                PrefName.PendingProgressUpdates,
                existing.filterNot { it.mediaId == media.id } + pending
            )
        } else {
            Anilist.mutation.editList(media.id, progress, status = status)
            MAL.query.editList(media.idMAL, media.anime != null, progress, null, status)
        }
        toast(currContext()?.getString(R.string.setting_progress, progress))
        Refresh.all()
    }
}

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
