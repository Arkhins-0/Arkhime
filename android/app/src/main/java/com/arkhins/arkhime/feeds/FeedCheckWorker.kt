package com.arkhins.arkhime.feeds

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arkhins.arkhime.R
import com.arkhins.arkhime.feeds.crunchyroll.Crunchyroll
import com.arkhins.arkhime.feeds.nyaa.Nyaa
import com.arkhins.arkhime.feeds.nyaa.NyaaFilter
import com.arkhins.arkhime.util.Logger
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Checks every tracked feed for releases newer than the last one seen and notifies. */
class FeedCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val feeds = FeedStore.all(ctx)
        if (feeds.isEmpty()) return Result.success()
        var failed = false
        val updated = feeds.map { feed ->
            if (!feed.notify) return@map feed
            runCatching {
                when (feed.source) {
                    FeedSource.NYAA, FeedSource.SUKEBEI -> checkNyaa(ctx, feed)
                    FeedSource.CRUNCHYROLL -> checkCrunchyroll(ctx, feed)
                }
            }.getOrElse {
                Logger.log("FeedCheckWorker: ${feed.label} failed: ${it.message}")
                failed = true
                feed
            }.also { delay(1500) }
        }
        // Feeds may have been edited while we were checking, so merge into the latest copy.
        val before = feeds.associateBy { it.id }
        val after = updated.associateBy { it.id }
        FeedStore.save(ctx, FeedStore.all(ctx).map { current ->
            val checked = after[current.id] ?: return@map current
            val found = checked.newCount - (before[current.id]?.newCount ?: 0)
            current.copy(
                lastSeen = maxOf(current.lastSeen, checked.lastSeen),
                newCount = current.newCount + found,
            )
        })
        return if (failed) Result.retry() else Result.success()
    }

    private suspend fun checkNyaa(ctx: Context, feed: TrackedFeed): TrackedFeed {
        val items = Nyaa.search(
            feed.query,
            feed.nyaaCategory,
            NyaaFilter.from(feed.filter),
        ).filter { it.pubDate > feed.lastSeen }
        if (items.isEmpty()) return feed
        notify(
            ctx, feed,
            title = if (items.size == 1) items.first().title else "${feed.label} · ${items.size} new",
            lines = items.take(6).map { it.title },
        )
        return feed.copy(lastSeen = items.maxOf { it.pubDate }, newCount = feed.newCount + items.size)
    }

    private suspend fun checkCrunchyroll(ctx: Context, feed: TrackedFeed): TrackedFeed {
        val now = System.currentTimeMillis() / 1000
        val from = maxOf(feed.lastSeen / 1000, now - 3 * 86400L)
        val items = Crunchyroll.aired(from, now)?.airingSchedules.orEmpty().filter { a ->
            val m = a.media ?: return@filter false
            feed.matches(m.title?.userPreferred, m.title?.romaji, m.title?.english)
        }
        if (items.isEmpty()) return feed
        notify(
            ctx, feed,
            title = if (items.size == 1) "${items.first().media?.name} · Episode ${items.first().episode}"
            else "${feed.label} · ${items.size} new",
            lines = items.take(6).map { "${it.media?.name} · Episode ${it.episode}" },
        )
        return feed.copy(lastSeen = items.maxOf { it.airingAt } * 1000, newCount = feed.newCount + items.size)
    }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, feed: TrackedFeed, title: String, lines: List<String>) {
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(ctx)
        val intent = FeedHostActivity.intent(ctx, feed.source, feed.query, feed.category, feed.filter, feed.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            ctx, feed.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
            .setSummaryText(feed.source.label)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull())
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup("feed_${feed.source.name}")
            .build()
        manager.notify(NOTIFICATION_BASE + (feed.id % 100_000).toInt(), notification)
    }

    companion object {
        private const val WORK_NAME = "com.arkhins.arkhime.feeds.FeedCheckWorker"
        private const val CHANNEL = "tracked_feeds"
        private const val NOTIFICATION_BASE = 74_000_000

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Tracked", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "New matches for your tracked keywords" }
                )
            }
        }

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every hour while at least one feed has notifications on. */
        fun schedule(ctx: Context, enabled: Boolean = FeedStore.all(ctx).any { it.notify }) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FeedCheckWorker>(1, TimeUnit.HOURS).setConstraints(constraints).build()
            )
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "${WORK_NAME}_now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<FeedCheckWorker>().setConstraints(constraints).build()
            )
        }
    }
}
