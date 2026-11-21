package com.arkhins.arkhime.notifications.comment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arkhins.arkhime.util.Logger
import kotlinx.coroutines.runBlocking

class CommentNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Logger.log("CommentNotificationReceiver: onReceive")
        runBlocking {
            CommentNotificationTask().execute(context)
        }
    }
}