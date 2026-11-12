package ani.arkhime.com.notifications.subscription

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.arkhime.com.notifications.AlarmManagerScheduler
import ani.arkhime.com.notifications.TaskScheduler
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.util.Logger
import kotlinx.coroutines.runBlocking

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log("SubscriptionNotificationReceiver: onReceive")
        runBlocking {
            SubscriptionNotificationTask().execute(context)
        }
        val subscriptionInterval =
            SubscriptionNotificationWorker.checkIntervals[PrefManager.getVal(PrefName.SubscriptionNotificationInterval)]
        AlarmManagerScheduler(context).scheduleRepeatingTask(
            TaskScheduler.TaskType.SUBSCRIPTION_NOTIFICATION,
            subscriptionInterval
        )
    }
}