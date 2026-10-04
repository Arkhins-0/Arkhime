package ani.arkhime.com.notifications.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ani.arkhime.com.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: lets a developer fire the test alerts over adb with
 * `adb shell am broadcast -n ani.arkhime.com.beta/ani.arkhime.com.notifications.updates.UpdateTestReceiver`
 */
class UpdateTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Logger.log("UpdateTestReceiver: sent ${UpdatesTask().sendTest(context.applicationContext)} test alerts")
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                pending.finish()
            }
        }
    }
}
