package ani.arkhime.com

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ani.arkhime.com.media.CalendarActivity
import ani.arkhime.com.media.MediaDetailsActivity
import ani.arkhime.com.media.SearchActivity
import ani.arkhime.com.media.user.ListActivity
import ani.arkhime.com.profile.ProfileActivity
import ani.arkhime.com.profile.notification.NotificationActivity
import ani.arkhime.com.settings.SettingsActivity
import ani.arkhime.com.settings.SettingsNotificationActivity
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName

/**
 * Debug builds only: opens a screen by name from adb, for screenshots and testing on phones that
 * block simulated taps.
 *
 *   adb shell am start -n ani.arkhime.com.beta/ani.arkhime.com.ScreenLauncher --es screen list --ez group true
 *
 * screen: home, list, manga-list, media (--ei id), updates, notifications, notification-settings,
 *         settings, profile, search (--es type ANIME|MANGA), calendar
 */
class ScreenLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefManager.init(applicationContext)
        val userId = PrefManager.getVal<String>(PrefName.AnilistUserId).toIntOrNull() ?: 0
        val username = intent.getStringExtra("username") ?: ""
        if (intent.hasExtra("group")) {
            PrefManager.setVal(PrefName.ListGroupBySeries, intent.getBooleanExtra("group", false))
        }

        val target = when (intent.getStringExtra("screen")) {
            "list", "manga-list" -> Intent(this, ListActivity::class.java)
                .putExtra("anime", intent.getStringExtra("screen") == "list")
                .putExtra("userId", userId)
                .putExtra("username", username)
            "media" -> Intent(this, MediaDetailsActivity::class.java)
                .putExtra("mediaId", intent.getIntExtra("id", 154587))
            "updates" -> Intent(this, NotificationActivity::class.java).putExtra("tab", 2)
            "notifications" -> Intent(this, NotificationActivity::class.java).putExtra("tab", 1)
            "notification-settings" -> Intent(this, SettingsNotificationActivity::class.java)
            "settings" -> Intent(this, SettingsActivity::class.java)
            "profile" -> Intent(this, ProfileActivity::class.java).putExtra("userId", userId)
            "search" -> Intent(this, SearchActivity::class.java)
                .putExtra("type", intent.getStringExtra("type") ?: "ANIME")
            "calendar" -> Intent(this, CalendarActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
