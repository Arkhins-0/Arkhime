package com.arkhins.arkhime.widgets.upcoming

import android.content.Intent
import android.widget.RemoteViewsService
import com.arkhins.arkhime.util.Logger

class UpcomingRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        Logger.log("UpcomingRemoteViewsFactory onGetViewFactory")
        return UpcomingRemoteViewsFactory(applicationContext)
    }
}
