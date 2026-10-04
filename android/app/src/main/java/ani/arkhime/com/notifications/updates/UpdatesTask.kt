package ani.arkhime.com.notifications.updates

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.NotificationType
import ani.arkhime.com.media.MediaDetailsActivity
import ani.arkhime.com.notifications.Task
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.util.Logger
import com.bumptech.glide.Glide
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/**
 * Collects updates from AniList (every episode that aired, titles newly added to the site, and the
 * viewer's own media notifications), stores them for the Updates tab and pushes the enabled ones.
 */
class UpdatesTask(private val push: Boolean = true) : Task {

    override suspend fun execute(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            PrefManager.init(context)
            val loggedIn = Anilist.getSavedToken()
            val now = System.currentTimeMillis() / 1000
            val lastCheck = PrefManager.getVal<Long>(PrefName.UpdatesLastCheck)
            val firstRun = lastCheck == 0L
            // First run looks back a day so the tab isn't empty; later runs overlap a little (duplicates are dropped)
            val since = if (firstRun) now - DAY else (lastCheck - 300).coerceAtLeast(now - 3 * DAY)

            val events = mutableListOf<UpdateEvent>()
            events += airings(context, since, now)
            events += added(context, anime = true, firstRun, now)
            events += added(context, anime = false, firstRun, now)
            if (loggedIn) events += anilistNotifications(context)

            val fresh = UpdateStore.add(context, events)
            PrefManager.setVal(PrefName.UpdatesLastCheck, now)
            if (fresh.isNotEmpty()) {
                val unread = PrefManager.getVal<Int>(PrefName.UnreadUpdates)
                PrefManager.setVal(PrefName.UnreadUpdates, unread + fresh.size)
            }
            if (push && !firstRun) notify(context, fresh)
            true
        } catch (e: Exception) {
            Logger.log("UpdatesTask: ${e.message}")
            Logger.log(e)
            false
        }
    }

    /**
     * Pushes one alert of every type using real AniList titles that aren't on the viewer's list,
     * ignoring the alert settings, so the look and the action buttons can be tried. Nothing is
     * stored in the Updates tab. Returns how many were sent.
     */
    suspend fun sendTest(context: Context): Int = withContext(Dispatchers.IO) {
        PrefManager.init(context)
        val notOnList = if (Anilist.getSavedToken()) ",onList:false" else ""
        val data = query(
            """{a:Page(perPage:6){media(type:ANIME,sort:POPULARITY_DESC,isAdult:false$notOnList){$MEDIA_FIELDS}} m:Page(perPage:2){media(type:MANGA,sort:POPULARITY_DESC,isAdult:false$notOnList){$MEDIA_FIELDS}}}"""
        ) ?: return@withContext 0
        val anime = data.obj("a")?.arr("media")?.mapNotNull { it as? JsonObject }.orEmpty()
        val manga = data.obj("m")?.arr("media")?.mapNotNull { it as? JsonObject }.orEmpty()
        if (anime.size < 6 || manga.size < 2) return@withContext 0
        val now = System.currentTimeMillis() / 1000

        fun JsonObject.test(type: UpdateType, text: String) =
            toEvent("test:${type.name}", type, now, text).copy(listStatus = null)

        val episodes = anime[2].int("episodes") ?: 12
        val events = listOf(
            anime[0].test(UpdateType.ANIME_ADDED, context.getString(R.string.update_text_added, "TV · Not yet released")),
            manga[0].test(UpdateType.MANGA_ADDED, context.getString(R.string.update_text_added, "Manga · Releasing")),
            anime[1].test(UpdateType.PREMIERE, context.getString(R.string.update_text_premiere)),
            anime[2].test(UpdateType.EPISODE, context.getString(R.string.update_text_episode, 5, episodes.toString())),
            anime[3].test(UpdateType.FINISHED, context.getString(R.string.update_text_finished, anime[3].int("episodes") ?: 12)),
            manga[1].test(UpdateType.RELATED, context.getString(R.string.update_text_related)),
            anime[4].test(UpdateType.DATA_CHANGE, context.getString(R.string.update_text_data_change) + "\nUpdated the start date and studio"),
            anime[5].test(UpdateType.MERGE, context.getString(R.string.update_text_merge, "Duplicate entry")),
            UpdateEvent(
                key = "test:${UpdateType.DELETION.name}", type = UpdateType.DELETION, time = now,
                mediaId = 0, anime = true, title = "Example removed title",
                text = context.getString(R.string.update_text_deletion) + "\nDuplicate of another entry",
            ),
        )
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext 0
        val manager = NotificationManagerCompat.from(context)
        events.forEach { manager.notify(Notifications.CHANNEL_UPDATES, it.key.hashCode(), build(context, it)) }
        events.size
    }

    // ---- Sources -------------------------------------------------------------------------------

    private suspend fun query(gql: String): JsonObject? =
        (Anilist.executeQuery<JsonObject>(gql, force = true)?.get("data") as? JsonObject)

    /** Every episode that aired on AniList in [since, now] */
    private suspend fun airings(context: Context, since: Long, now: Long): List<UpdateEvent> {
        val out = mutableListOf<UpdateEvent>()
        for (page in 1..MAX_AIRING_PAGES) {
            val data = query(
                """{Page(page:$page,perPage:50){pageInfo{hasNextPage} airingSchedules(airingAt_greater:$since,airingAt_lesser:$now,sort:TIME_DESC){episode airingAt media{$MEDIA_FIELDS}}}}"""
            ) ?: break
            val pageObj = data.obj("Page") ?: break
            pageObj.arr("airingSchedules")?.forEach { item ->
                val schedule = item as? JsonObject ?: return@forEach
                val media = schedule.obj("media") ?: return@forEach
                if (media.bool("isAdult") == true && !Anilist.adult) return@forEach
                val episode = schedule.int("episode") ?: return@forEach
                val time = schedule.long("airingAt") ?: now
                val total = media.int("episodes")
                val base = media.toEvent("", UpdateType.EPISODE, time, "")
                out += base.copy(
                    key = "aired:${base.mediaId}:$episode",
                    text = context.getString(R.string.update_text_episode, episode, total?.toString() ?: "?"),
                )
                if (episode == 1) out += base.copy(
                    key = "premiere:${base.mediaId}",
                    type = UpdateType.PREMIERE,
                    text = context.getString(R.string.update_text_premiere),
                )
                if (total != null && episode == total) out += base.copy(
                    key = "finished:${base.mediaId}",
                    type = UpdateType.FINISHED,
                    text = context.getString(R.string.update_text_finished, total),
                )
            }
            if (pageObj.obj("pageInfo")?.bool("hasNextPage") != true) break
        }
        return out
    }

    /** Titles added to AniList since the last check (newest ids) */
    private suspend fun added(context: Context, anime: Boolean, firstRun: Boolean, now: Long): List<UpdateEvent> {
        val pref = if (anime) PrefName.UpdatesLastAnimeId else PrefName.UpdatesLastMangaId
        val lastId = PrefManager.getVal<Int>(pref)
        val adult = if (Anilist.adult) "" else ",isAdult:false"
        val data = query(
            """{Page(page:1,perPage:50){media(type:${if (anime) "ANIME" else "MANGA"},sort:ID_DESC$adult){$MEDIA_FIELDS}}}"""
        ) ?: return emptyList()
        val media = data.obj("Page")?.arr("media")?.mapNotNull { it as? JsonObject }.orEmpty()
        val maxId = media.maxOfOrNull { it.int("id") ?: 0 } ?: return emptyList()
        if (maxId > lastId) PrefManager.setVal(pref, maxId)
        // First run: show the latest few as a starting point, without treating them as news
        val newOnes = if (firstRun) media.take(10) else media.filter { (it.int("id") ?: 0) > lastId }
        val type = if (anime) UpdateType.ANIME_ADDED else UpdateType.MANGA_ADDED
        return newOnes.map { m ->
            val format = m.str("format")?.pretty()
            val status = m.str("status")?.pretty()
            val start = m.obj("startDate")?.int("year")?.toString()
            val detail = listOfNotNull(format, status, start).joinToString(" · ")
            m.toEvent("added:${m.int("id")}", type, now, context.getString(R.string.update_text_added, detail))
        }
    }

    /** The viewer's AniList media notifications: related additions, data changes, merges, deletions */
    private suspend fun anilistNotifications(context: Context): List<UpdateEvent> {
        val userId = PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull() ?: return emptyList()
        val res = Anilist.query.getNotifications(userId, resetNotification = false, type = true) ?: return emptyList()
        return res.data.page?.notifications.orEmpty().mapNotNull { n ->
            val type = when (n.notificationType) {
                NotificationType.RELATED_MEDIA_ADDITION.value -> UpdateType.RELATED
                NotificationType.MEDIA_DATA_CHANGE.value -> UpdateType.DATA_CHANGE
                NotificationType.MEDIA_MERGE.value -> UpdateType.MERGE
                NotificationType.MEDIA_DELETION.value -> UpdateType.DELETION
                else -> return@mapNotNull null // airings come from the site-wide schedule
            }
            val media = n.media
            val title = media?.title?.userPreferred ?: n.deletedMediaTitle ?: return@mapNotNull null
            val headline = when (type) {
                UpdateType.RELATED -> context.getString(R.string.update_text_related)
                UpdateType.DATA_CHANGE -> context.getString(R.string.update_text_data_change)
                UpdateType.MERGE -> context.getString(
                    R.string.update_text_merge, n.deletedMediaTitles.orEmpty().joinToString(", ")
                )
                else -> context.getString(R.string.update_text_deletion)
            }
            val text = headline + (n.reason?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
            UpdateEvent(
                key = "anilist:${n.id}",
                type = type,
                time = n.createdAt.toLong(),
                mediaId = media?.id ?: 0,
                anime = media?.type?.toString()?.equals("MANGA", true) != true,
                title = title,
                text = text,
                cover = media?.coverImage?.large ?: media?.coverImage?.medium,
                banner = media?.bannerImage,
            )
        }
    }

    // ---- Push ----------------------------------------------------------------------------------

    private fun notify(context: Context, fresh: List<UpdateEvent>) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val enabled = PrefManager.getVal<Set<String>>(PrefName.UpdateAlertTypes)
        val myListOnly = PrefManager.getVal<Int>(PrefName.UpdateReleaseScope) == 1
        val toPush = fresh.filter { event ->
            event.type.name in enabled &&
                    // Release alerts can be limited to the viewer's list; additions are always site-wide
                    !(myListOnly && event.type in RELEASE_TYPES && event.listStatus == null)
        }.sortedBy { it.time }
        val manager = NotificationManagerCompat.from(context)
        toPush.takeLast(MAX_PUSHES).forEach { event ->
            manager.notify(Notifications.CHANNEL_UPDATES, event.key.hashCode(), build(context, event))
        }
        val skipped = toPush.size - MAX_PUSHES
        if (skipped > 0) {
            manager.notify(
                Notifications.CHANNEL_UPDATES, Notifications.ID_UPDATES_SUMMARY,
                NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setColor(ContextCompat.getColor(context, R.color.arkhime_red))
                    .setContentTitle(context.getString(R.string.update_more_title, skipped))
                    .setContentText(context.getString(R.string.update_more_text))
                    .setContentIntent(openUpdates(context))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun build(context: Context, event: UpdateEvent): android.app.Notification {
        val id = event.key.hashCode()
        val cover = bitmap(context, event.cover, 256, 360)
        val picture = bitmap(context, event.banner, 1024, 512) ?: cover
        val open = if (event.mediaId > 0) {
            PendingIntent.getActivity(
                context, id,
                Intent(context, MediaDetailsActivity::class.java)
                    .putExtra("mediaId", event.mediaId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else openUpdates(context)

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.notification_icon)
            .setColor(ContextCompat.getColor(context, R.color.arkhime_red))
            .setContentTitle(event.title)
            .setContentText("${context.getString(event.type.label)} · ${event.text.lineSequence().first()}")
            .setSubText(context.getString(event.type.label))
            .setWhen(event.time * 1000)
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP)
        cover?.let { builder.setLargeIcon(it) }
        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(event.text)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(event.text))
        }

        // Quick list actions: new titles -> Planning / Watching, finished -> Completed
        if (event.mediaId > 0) {
            val current = if (event.anime) R.string.update_action_watching else R.string.update_action_reading
            when (event.type) {
                UpdateType.ANIME_ADDED, UpdateType.MANGA_ADDED, UpdateType.RELATED, UpdateType.PREMIERE ->
                    if (event.listStatus == null) {
                        builder.addAction(0, context.getString(R.string.update_action_planning),
                            UpdateActionReceiver.intent(context, event, "PLANNING", id))
                        builder.addAction(0, context.getString(current),
                            UpdateActionReceiver.intent(context, event, "CURRENT", id))
                    }
                UpdateType.FINISHED ->
                    if (event.listStatus != "COMPLETED") {
                        builder.addAction(0, context.getString(R.string.update_action_completed),
                            UpdateActionReceiver.intent(context, event, "COMPLETED", id))
                    }
                else -> {}
            }
        }
        return builder.build()
    }

    private fun bitmap(context: Context, url: String?, width: Int, height: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            Glide.with(context.applicationContext).asBitmap().load(url).centerCrop().submit(width, height).get()
        }.getOrNull()
    }

    // ---- JSON helpers --------------------------------------------------------------------------

    private fun JsonObject.toEvent(key: String, type: UpdateType, time: Long, text: String): UpdateEvent {
        val anime = str("type") != "MANGA"
        return UpdateEvent(
            key = key,
            type = type,
            time = time,
            mediaId = int("id") ?: 0,
            anime = anime,
            title = obj("title")?.str("userPreferred") ?: "?",
            text = text,
            cover = obj("coverImage")?.let { it.str("extraLarge") ?: it.str("large") },
            banner = str("bannerImage"),
            total = if (anime) int("episodes") else int("chapters"),
            listStatus = obj("mediaListEntry")?.str("status"),
        )
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.arr(key: String) = this[key] as? JsonArray
    private fun JsonObject.prim(key: String) = (this[key] as? JsonElement)?.takeIf { it !is JsonObject && it !is JsonArray }?.jsonPrimitive
    private fun JsonObject.str(key: String) = prim(key)?.contentOrNull
    private fun JsonObject.int(key: String) = prim(key)?.intOrNull
    private fun JsonObject.long(key: String) = prim(key)?.longOrNull
    private fun JsonObject.bool(key: String) = prim(key)?.booleanOrNull

    private fun String.pretty() = if (length <= 3) this else
        replace('_', ' ').lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

    companion object {
        private const val DAY = 24 * 3600L
        private const val MAX_AIRING_PAGES = 6
        private const val MAX_PUSHES = 15
        private const val GROUP = "ani.arkhime.com.UPDATES"
        private val RELEASE_TYPES = setOf(UpdateType.EPISODE, UpdateType.PREMIERE, UpdateType.FINISHED)
        private const val MEDIA_FIELDS =
            "id type format status episodes chapters isAdult title{userPreferred} coverImage{large extraLarge} bannerImage startDate{year} mediaListEntry{status}"

        fun openUpdates(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, ani.arkhime.com.profile.notification.NotificationActivity::class.java)
                .putExtra("tab", 2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
