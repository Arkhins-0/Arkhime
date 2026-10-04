package ani.arkhime.com.notifications.anilist

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ani.arkhime.com.notifications.updates.UpdatesTask
import ani.arkhime.com.others.AppUpdater
import ani.arkhime.com.util.Logger

class AnilistNotificationWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Logger.log("AnilistNotificationWorker: doWork")
        if (System.currentTimeMillis() - lastCheck < 60000) {
            Logger.log("AnilistNotificationWorker: doWork skipped")
            return Result.success()
        }
        lastCheck = System.currentTimeMillis()
        val updates = UpdatesTask().execute(applicationContext)
        runCatching { AppUpdater.checkInBackground(applicationContext) }
        return if (AnilistNotificationTask().execute(applicationContext) && updates) {
            Result.success()
        } else {
            Logger.log("AnilistNotificationWorker: doWork failed")
            Result.retry()
        }
    }

    companion object {
        val checkIntervals = arrayOf(0L, 30, 60, 120, 240, 360, 720, 1440)
        const val WORK_NAME = "ani.arkhime.com.notifications.anilist.AnilistNotificationWorker"
        private var lastCheck = 0L
    }
}