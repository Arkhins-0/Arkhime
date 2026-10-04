package ani.arkhime.com.profile.notification

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.databinding.ActivityNotificationBinding
import ani.arkhime.com.initActivity
import ani.arkhime.com.navBarHeight
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.MEDIA
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.ONE
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.NotificationType.USER
import ani.arkhime.com.profile.notification.NotificationFragment.Companion.newInstance
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.statusBarHeight
import ani.arkhime.com.themes.ThemeManager
import nl.joery.animatedbottombar.AnimatedBottomBar

class NotificationActivity : AppCompatActivity() {
    lateinit var binding: ActivityNotificationBinding
    private var selected: Int = 0
    lateinit var navBar: AnimatedBottomBar
    private var userCount: Int = 0
    private var mediaCount: Int = 0
    private var updatesCount: Int = 0
    private var updatesTab: AnimatedBottomBar.Tab? = null
    private var userTab: AnimatedBottomBar.Tab? = null
    private var mediaTab: AnimatedBottomBar.Tab? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.notificationTitle.text = getString(R.string.notifications)
        binding.notificationToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        navBar = binding.notificationNavBar
        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight
        }

        updateCounts()

        val tabs = listOf(
            Pair(R.drawable.ic_round_person_24, "User"),
            Pair(R.drawable.ic_round_movie_filter_24, "Media"),
            Pair(R.drawable.ic_round_new_releases_24, getString(R.string.updates)),
        )

        tabs.forEachIndexed { index, (icon, title) ->
            val tab = navBar.createTab(icon, title)
            when (index) {
                0 -> {
                    userTab = tab
                    if (userCount > 0) tab.badge = AnimatedBottomBar.Badge("$userCount")
                }
                1 -> {
                    mediaTab = tab
                    if (mediaCount > 0) tab.badge = AnimatedBottomBar.Badge("$mediaCount")
                }
                2 -> {
                    updatesTab = tab
                    if (updatesCount > 0) tab.badge = AnimatedBottomBar.Badge("$updatesCount")
                }
            }
            navBar.addTab(tab)
        }

        binding.notificationBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val getOne = intent.getIntExtra("activityId", -1)
        selected = intent.getIntExtra("tab", selected)
        if (getOne != -1) navBar.isVisible = false
        binding.notificationViewPager.isUserInputEnabled = false
        binding.notificationViewPager.adapter =
            ViewPagerAdapter(supportFragmentManager, lifecycle, getOne) { type, reset ->
                if (reset) {
                    when (type) {
                        USER -> {
                            userCount = 0
                            userTab?.badge = null
                        }
                        MEDIA -> {
                            mediaCount = 0
                            mediaTab?.badge = null
                        }
                        ONE -> {}
                    }
                    saveCounts()
                }
            }
        binding.notificationViewPager.registerOnPageChangeCallback(
            object : ViewPager2 .OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    fragments[position]?.onVisible()
                    if (position == 2) {
                        updatesCount = 0
                        updatesTab?.badge = null
                    }
                }
            }
        )
        binding.notificationViewPager.setCurrentItem(selected, false)
        navBar.selectTabAt(selected)
        navBar.setOnTabSelectListener(object : AnimatedBottomBar.OnTabSelectListener {
            override fun onTabSelected(
                lastIndex: Int,
                lastTab: AnimatedBottomBar.Tab?,
                newIndex: Int,
                newTab: AnimatedBottomBar.Tab
            ) {
                selected = newIndex
                binding.notificationViewPager.setCurrentItem(selected, false)
            }
        })
    }

    private fun updateCounts() {
        userCount = PrefManager.getVal(PrefName.UnreadUserNotifications, 0)
        mediaCount = PrefManager.getVal(PrefName.UnreadMediaNotifications, 0)
        updatesCount = PrefManager.getVal(PrefName.UnreadUpdates, 0)
    }

    private fun saveCounts() {
        PrefManager.setVal(PrefName.UnreadUserNotifications, userCount)
        PrefManager.setVal(PrefName.UnreadMediaNotifications, mediaCount)
    }

    override fun onResume() {
        super.onResume()
        if (this::navBar.isInitialized) {
            updateCounts()
            if (userCount > 0) userTab?.badge = AnimatedBottomBar.Badge("$userCount") else userTab?.badge = null
            if (mediaCount > 0) mediaTab?.badge = AnimatedBottomBar.Badge("$mediaCount") else mediaTab?.badge = null
            if (updatesCount > 0 && selected != 2) updatesTab?.badge = AnimatedBottomBar.Badge("$updatesCount") else updatesTab?.badge = null
            navBar.selectTabAt(selected)
        }
    }
    val fragments = mutableMapOf<Int, NotificationFragment>()
    private inner class ViewPagerAdapter(
        fragmentManager: FragmentManager,
        lifecycle: Lifecycle,
        val id: Int = -1,
        private val countResetCallback: (NotificationFragment.Companion.NotificationType, Boolean) -> Unit
    ) : FragmentStateAdapter(fragmentManager, lifecycle) {
        override fun getItemCount(): Int = if (id != -1) 1 else 3

        override fun createFragment(position: Int): Fragment {
            if (position == 2) return UpdatesFragment()
            val fragment = when (position) {
                0 -> newInstance(if (id != -1) ONE else USER, id, countResetCallback)
                else -> newInstance(MEDIA, countResetCallback = countResetCallback)
            }
            fragments[position] = fragment
            return fragment
        }
    }
}