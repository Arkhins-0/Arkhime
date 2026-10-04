package ani.arkhime.com.notifications

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Shortcuts to the system screens that decide whether background checks (alerts, update checks)
 * can run: notification permission, autostart (Xiaomi, Oppo, Vivo, Huawei...) and battery limits.
 */
object BackgroundAccess {

    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openNotificationSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        if (!tryStart(activity, intent)) openAppDetails(activity)
    }

    fun batteryUnrestricted(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)

    /** Xiaomi's own "Battery saver → No restrictions" page, else Android's battery optimisation prompt */
    @SuppressLint("BatteryLife")
    fun openBatterySettings(activity: Activity) {
        val pkg = activity.packageName
        if (isXiaomi) {
            val miui = Intent().setComponent(
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            )
                .putExtra("package_name", pkg)
                .putExtra("package_label", activity.applicationInfo.loadLabel(activity.packageManager))
            if (tryStart(activity, miui)) return
        }
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))
        if (!batteryUnrestricted(activity) && tryStart(activity, request)) return
        if (tryStart(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails(activity)
    }

    /** True when this phone's maker has an autostart switch Arkhime knows how to open */
    val hasAutostartScreen: Boolean
        get() = Build.MANUFACTURER.lowercase() in AUTOSTART_MAKERS

    fun openAutostart(activity: Activity) {
        for ((pkg, cls) in AUTOSTART_SCREENS) {
            if (tryStart(activity, Intent().setComponent(ComponentName(pkg, cls)))) return
        }
        openAppDetails(activity)
    }

    private val isXiaomi get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    private val AUTOSTART_MAKERS = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo",
        "huawei", "honor", "asus", "letv", "meizu", "tecno", "infinix", "itel",
    )

    // Known autostart managers, most common first; the first one that opens wins
    private val AUTOSTART_SCREENS = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.entry.FunctionActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
    )

    private fun openAppDetails(activity: Activity) {
        tryStart(activity, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }

    private fun tryStart(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: Exception) {
        // Not on this phone, or not exported to other apps
        false
    }
}
