package ani.arkhime.com.notifications.anilist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.arkhime.com.notifications.AlarmManagerScheduler
import ani.arkhime.com.notifications.TaskScheduler
import ani.arkhime.com.notifications.updates.UpdatesTask
import ani.arkhime.com.others.AppUpdater
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnilistNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log("AnilistNotificationReceiver: onReceive")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AnilistNotificationTask().execute(context)
                UpdatesTask().execute(context)
                runCatching { AppUpdater.checkInBackground(context) }
                val anilistInterval =
                    AnilistNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
                AlarmManagerScheduler(context).scheduleRepeatingTask(
                    TaskScheduler.TaskType.ANILIST_NOTIFICATION,
                    anilistInterval
                )
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
