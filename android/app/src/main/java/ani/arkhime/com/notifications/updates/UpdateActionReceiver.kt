package ani.arkhime.com.notifications.updates

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.FuzzyDate
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.util.Logger
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the list buttons on update notifications (Planning / Watching / Completed). */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mediaId = intent.getIntExtra(EXTRA_MEDIA, 0)
        val status = intent.getStringExtra(EXTRA_STATUS) ?: return
        val total = intent.getIntExtra(EXTRA_TOTAL, -1).takeIf { it > 0 }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        if (mediaId <= 0) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val message = try {
                PrefManager.init(context)
                if (!Anilist.getSavedToken()) {
                    context.getString(R.string.update_action_login)
                } else {
                    val today = FuzzyDate().getToday()
                    val saved = Anilist.mutation.saveListEntry(
                        mediaId = mediaId,
                        status = status,
                        progress = total.takeIf { status == "COMPLETED" },
                        scoreRaw = null,
                        startedAt = today.takeIf { status == "CURRENT" },
                        completedAt = today.takeIf { status == "COMPLETED" },
                    )
                    if (saved) {
                        Anilist.query.invalidateUserStatusCache()
                        context.getString(R.string.update_action_done, title, label(context, status, intent))
                    } else {
                        context.getString(R.string.update_action_failed, Anilist.lastError ?: "")
                    }
                }
            } catch (e: Exception) {
                Logger.log(e)
                context.getString(R.string.update_action_failed, e.message ?: "")
            }
            // Replace the alert with a short confirmation that clears itself
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context).notify(
                    Notifications.CHANNEL_UPDATES, notificationId,
                    NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATES)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setColor(ContextCompat.getColor(context, R.color.arkhime_red))
                        .setContentTitle(message)
                        .setSilent(true)
                        .setAutoCancel(true)
                        .setTimeoutAfter(5000)
                        .build()
                )
            }
            pending.finish()
        }
    }

    private fun label(context: Context, status: String, intent: Intent): String = context.getString(
        when (status) {
            "PLANNING" -> R.string.update_action_planning
            "COMPLETED" -> R.string.update_action_completed
            else -> if (intent.getBooleanExtra(EXTRA_ANIME, true)) R.string.update_action_watching
            else R.string.update_action_reading
        }
    )

    companion object {
        private const val EXTRA_MEDIA = "mediaId"
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_TOTAL = "total"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ANIME = "anime"
        private const val EXTRA_NOTIFICATION = "notificationId"

        fun intent(context: Context, event: UpdateEvent, status: String, notificationId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                // One request code per (notification, action) so the buttons don't overwrite each other
                31 * notificationId + status.hashCode(),
                Intent(context, UpdateActionReceiver::class.java)
                    .putExtra(EXTRA_MEDIA, event.mediaId)
                    .putExtra(EXTRA_STATUS, status)
                    .putExtra(EXTRA_TOTAL, event.total ?: -1)
                    .putExtra(EXTRA_TITLE, event.title)
                    .putExtra(EXTRA_ANIME, event.anime)
                    .putExtra(EXTRA_NOTIFICATION, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
