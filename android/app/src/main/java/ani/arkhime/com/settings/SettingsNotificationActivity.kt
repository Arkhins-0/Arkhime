package ani.arkhime.com.settings

import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ani.arkhime.com.R
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.anilist.api.NotificationType
import ani.arkhime.com.databinding.ActivitySettingsNotificationsBinding
import ani.arkhime.com.initActivity
import ani.arkhime.com.media.Media
import ani.arkhime.com.navBarHeight
import ani.arkhime.com.notifications.BackgroundAccess
import ani.arkhime.com.notifications.TaskScheduler
import ani.arkhime.com.notifications.anilist.AnilistNotificationWorker
import ani.arkhime.com.notifications.updates.UpdateType
import ani.arkhime.com.notifications.updates.UpdatesTask
import ani.arkhime.com.openSettings
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.statusBarHeight
import ani.arkhime.com.themes.ThemeManager
import ani.arkhime.com.toast
import ani.arkhime.com.util.customAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SettingsNotificationActivity : AppCompatActivity() {
    private fun onOff(on: Boolean) = getString(if (on) R.string.background_on else R.string.background_off)

    private lateinit var binding: ActivitySettingsNotificationsBinding
    private var isImportingLists = false
    private var isImportingStatuses = false
    /** Rebuilds the list, so the permission rows show their current state after a trip to system settings */
    private var render: (() -> Unit)? = null

    override fun onResume() {
        super.onResume()
        render?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        val context = this
        binding = ActivitySettingsNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            settingsNotificationsLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBarHeight
                bottomMargin = navBarHeight
            }
            notificationSettingsBack.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
            val aTimeNames = AnilistNotificationWorker.checkIntervals.map { it.toInt() }
            val aItems = aTimeNames.map {
                val mins = it % 60
                val hours = it / 60
                if (it > 0) "${if (hours > 0) "$hours hrs " else ""}${if (mins > 0) "$mins mins" else ""}"
                else getString(R.string.do_not_update)
            }
            val highlightKey = intent.getStringExtra(ani.arkhime.com.settings.search.SettingsSearchAdapter.EXTRA_HIGHLIGHT_KEY)
            val scopeNames = listOf(getString(R.string.update_scope_all), getString(R.string.update_scope_mine))
            render = { settingsRecyclerView.adapter = SettingsAdapter(
                arrayListOf(
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.background_notifications,
                            onOff(BackgroundAccess.notificationsAllowed(context))
                        ),
                        desc = getString(R.string.background_notifications_desc),
                        icon = R.drawable.ic_round_notifications_active_24,
                        onClick = { BackgroundAccess.openNotificationSettings(context) }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.background_autostart),
                        desc = getString(
                            if (BackgroundAccess.hasAutostartScreen) R.string.background_autostart_desc
                            else R.string.background_autostart_desc_other
                        ),
                        icon = R.drawable.ic_round_new_releases_24,
                        onClick = { BackgroundAccess.openAutostart(context) }
                    ),
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.background_battery,
                            onOff(BackgroundAccess.batteryUnrestricted(context))
                        ),
                        desc = getString(R.string.background_battery_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = { BackgroundAccess.openBatterySettings(context) }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.update_alerts),
                        desc = getString(R.string.update_alerts_desc),
                        icon = R.drawable.ic_round_new_releases_24,
                        onClick = {
                            val types = UpdateType.entries
                            val enabled = PrefManager.getVal<Set<String>>(PrefName.UpdateAlertTypes).toMutableSet()
                            context.customAlertDialog().apply {
                                setTitle(R.string.update_alerts)
                                multiChoiceItems(
                                    types.map { getString(it.label) }.toTypedArray(),
                                    types.map { it.name in enabled }.toBooleanArray()
                                ) { checked ->
                                    types.forEachIndexed { i, type ->
                                        if (checked[i]) enabled.add(type.name) else enabled.remove(type.name)
                                    }
                                    PrefManager.setVal(PrefName.UpdateAlertTypes, enabled)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.update_scope,
                            scopeNames[PrefManager.getVal<Int>(PrefName.UpdateReleaseScope).coerceIn(0, 1)]
                        ),
                        desc = getString(R.string.update_scope_desc),
                        icon = R.drawable.ic_round_notifications_active_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                setTitle(R.string.update_alerts)
                                singleChoiceItems(
                                    scopeNames.toTypedArray(),
                                    PrefManager.getVal<Int>(PrefName.UpdateReleaseScope)
                                ) { i ->
                                    PrefManager.setVal(PrefName.UpdateReleaseScope, i)
                                    it.settingsTitle.text = getString(R.string.update_scope, scopeNames[i])
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.update_test),
                        desc = getString(R.string.update_test_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = {
                            lifecycleScope.launch {
                                val sent = UpdatesTask().sendTest(applicationContext)
                                toast(getString(R.string.update_test_sent, sent))
                            }
                        }
                    ),
                    Settings(
                        type = 1,
                        name = getString(R.string.anilist_notification_filters),
                        desc = getString(R.string.anilist_notification_filters_desc),
                        icon = R.drawable.ic_anilist,
                        onClick = {
                            val types = NotificationType.entries.map { it.name }
                            val filteredTypes =
                                PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                                    .toMutableSet()
                            val selected = types.map { filteredTypes.contains(it) }.toBooleanArray()
                            context.customAlertDialog().apply {
                                 setTitle(R.string.anilist_notification_filters)
                                 multiChoiceItems(
                                    types.map { name ->
                                        name.replace("_", " ").lowercase().replaceFirstChar {
                                        if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                                    } }.toTypedArray(),
                                    selected
                                ) { updatedSelected ->
                                types.forEachIndexed { index, type ->
                                    if (updatedSelected[index]) {
                                        filteredTypes.add(type)
                                    } else {
                                        filteredTypes.remove(type)
                                    }
                                }
                                    PrefManager.setVal(PrefName.AnilistFilteredTypes, filteredTypes)
                                }
                                show()
                            }
                        }

                    ),
                    Settings(
                        type = 1,
                        name = getString(
                            R.string.anilist_notifications_checking_time,
                            aItems[PrefManager.getVal(PrefName.AnilistNotificationInterval)]
                        ),
                        desc = getString(R.string.anilist_notifications_checking_time_desc),
                        icon = R.drawable.ic_round_notifications_none_24,
                        onClick = {
                            context.customAlertDialog().apply {
                                 setTitle(R.string.subscriptions_checking_time)
                                 singleChoiceItems(
                                    aItems.toTypedArray(),
                                    PrefManager.getVal<Int>(PrefName.AnilistNotificationInterval)
                                ) { i ->
                                    PrefManager.setVal(PrefName.AnilistNotificationInterval, i)
                                    it.settingsTitle.text =
                                        getString(
                                            R.string.anilist_notifications_checking_time,
                                            aItems[i]
                                        )
                                    TaskScheduler.create(
                                        context, PrefManager.getVal(PrefName.UseAlarmManager)
                                    ).scheduleAllTasks(context)
                                }
                                show()
                            }
                        }
                    ),
                    Settings(
                        type = 2,
                        name = getString(R.string.use_alarm_manager_reliable),
                        desc = getString(R.string.use_alarm_manager_reliable_desc),
                        icon = R.drawable.ic_anilist,
                        isChecked = PrefManager.getVal(PrefName.UseAlarmManager),
                        switch = { isChecked, view ->
                            if (isChecked) {
                                context.customAlertDialog().apply {
                                     setTitle(R.string.use_alarm_manager)
                                     setMessage(R.string.use_alarm_manager_confirm)
                                     setPosButton(R.string.use) {
                                        PrefManager.setVal(PrefName.UseAlarmManager, true)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            if (!(getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
                                                val intent =
                                                    Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", android.net.Uri.parse("package:${context.packageName}"))
                                                startActivity(intent)
                                                view.settingsButton.isChecked = true
                                            }
                                        }
                                    }
                                    setNegButton(R.string.cancel) {
                                        view.settingsButton.isChecked = false
                                        PrefManager.setVal(PrefName.UseAlarmManager, false)
                                    }
                                    show()
                                }
                            } else {
                                PrefManager.setVal(PrefName.UseAlarmManager, false)
                                TaskScheduler.create(context, true).cancelAllTasks()
                                TaskScheduler.create(context, false)
                                    .scheduleAllTasks(context)
                            }
                        },
                    ),
                ),
                highlightKey = highlightKey
            ) }
            render?.invoke()
            settingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                setHasFixedSize(true)
            }
        }
    }
}
