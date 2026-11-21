package com.arkhins.arkhime.notifications.anilist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.arkhime.notifications.AlarmManagerScheduler
import com.arkhins.arkhime.notifications.TaskScheduler
import com.arkhins.arkhime.notifications.updates.UpdatesTask
import com.arkhins.arkhime.others.AppUpdater
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.util.Logger
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
