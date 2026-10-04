package ani.arkhime.com.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequest
import ani.arkhime.com.notifications.TaskScheduler.TaskType
import ani.arkhime.com.notifications.anilist.AnilistNotificationWorker
class WorkManagerScheduler(private val context: Context) : TaskScheduler {
    override fun scheduleRepeatingTask(taskType: TaskType, interval: Long) {
        if (java.util.concurrent.TimeUnit.MINUTES.toMillis(interval) < PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS) {
            cancelTask(taskType)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> {
                val recurringWork = PeriodicWorkRequest.Builder(
                    AnilistNotificationWorker::class.java,
                    interval,
                    java.util.concurrent.TimeUnit.MINUTES,
                    PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
                    java.util.concurrent.TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    AnilistNotificationWorker.WORK_NAME,
                    androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                    recurringWork
                )
            }
        }
    }

    override fun cancelTask(taskType: TaskType) {
        when (taskType) {
            TaskType.ANILIST_NOTIFICATION -> {
                androidx.work.WorkManager.getInstance(context)
                    .cancelUniqueWork(AnilistNotificationWorker.WORK_NAME)
            }

        }
    }
}
