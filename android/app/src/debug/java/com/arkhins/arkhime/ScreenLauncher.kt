package com.arkhins.arkhime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.arkhins.arkhime.media.CalendarActivity
import com.arkhins.arkhime.media.MediaDetailsActivity
import com.arkhins.arkhime.media.SearchActivity
import com.arkhins.arkhime.media.user.ListActivity
import com.arkhins.arkhime.profile.ProfileActivity
import com.arkhins.arkhime.profile.notification.NotificationActivity
import com.arkhins.arkhime.parsers.ParserTestActivity
import com.arkhins.arkhime.settings.ExtensionsActivity
import com.arkhins.arkhime.settings.PlayerSettingsActivity
import com.arkhins.arkhime.settings.ReaderSettingsActivity
import com.arkhins.arkhime.settings.SettingsActivity
import com.arkhins.arkhime.settings.SettingsAddonActivity
import com.arkhins.arkhime.settings.SettingsExtensionsActivity
import com.arkhins.arkhime.settings.SettingsNotificationActivity
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName

/**
 * Debug builds only: opens a screen by name from adb, for screenshots and testing on phones that
 * block simulated taps.
 *
 *   adb shell am start -n com.arkhins.arkhime.beta/com.arkhins.arkhime.ScreenLauncher --es screen list --ez group true
 *
 * screen: home, list, manga-list, media (--ei id), updates, notifications, notification-settings,
 *         settings, profile, search (--es type ANIME|MANGA), calendar,
 *         nyaa / sukebei / crunchyroll (--es query), tracked (--es source NYAA|SUKEBEI|CRUNCHYROLL), forum,
 *         extensions, extension-settings, addons, player-settings, reader-settings, parser-test
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
            "nyaa" -> com.arkhins.arkhime.feeds.FeedHostActivity.intent(
                this, com.arkhins.arkhime.feeds.FeedSource.NYAA, intent.getStringExtra("query").orEmpty()
            )
            "sukebei" -> com.arkhins.arkhime.feeds.FeedHostActivity.intent(
                this, com.arkhins.arkhime.feeds.FeedSource.SUKEBEI, intent.getStringExtra("query").orEmpty()
            )
            "crunchyroll" -> com.arkhins.arkhime.feeds.FeedHostActivity.intent(
                this, com.arkhins.arkhime.feeds.FeedSource.CRUNCHYROLL, intent.getStringExtra("query").orEmpty()
            )
            "tracked" -> com.arkhins.arkhime.feeds.TrackedFeedsActivity.intent(
                this, com.arkhins.arkhime.feeds.FeedSource.valueOf(intent.getStringExtra("source") ?: "NYAA")
            )
            "forum" -> Intent(this, com.arkhins.arkhime.forum.ForumActivity::class.java)
            "extensions" -> Intent(this, ExtensionsActivity::class.java)
            "extension-settings" -> Intent(this, SettingsExtensionsActivity::class.java)
            "addons" -> Intent(this, SettingsAddonActivity::class.java)
            "player-settings" -> Intent(this, PlayerSettingsActivity::class.java)
            "reader-settings" -> Intent(this, ReaderSettingsActivity::class.java)
            "parser-test" -> Intent(this, ParserTestActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
