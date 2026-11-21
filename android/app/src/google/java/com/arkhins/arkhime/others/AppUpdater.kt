package com.arkhins.arkhime.others

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.arkhins.arkhime.BuildConfig
import com.arkhins.arkhime.MainActivity
import com.arkhins.arkhime.R
import com.arkhins.arkhime.buildMarkwon
import com.arkhins.arkhime.okHttpClient
import com.arkhins.arkhime.openLinkInBrowser
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.snackString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Updates from GitHub releases (Arkhins-0/Arkhime): a popup with the changelog, the APK for this
 * phone downloaded here in the app, then Android's own installer. Android always asks the user to
 * confirm, and refuses an APK that isn't signed with the same key as the installed app.
 *
 * Each app follows only its own releases (BuildConfig.CHANNEL, set in build.gradle.kts):
 *  - release: full releases (tag v1.2.0), never pre-releases
 *  - beta: only beta pre-releases (tag beta-v1.2.0), which carry the com.arkhins.arkhime.beta APKs
 *  - alpha: never; it is built and installed from the PC
 */
object AppUpdater {
    private const val REPO = "Arkhins-0/Arkhime"
    private val json = Json { ignoreUnknownKeys = true }

    data class Release(val version: String, val notes: String, val apkUrl: String?, val pageUrl: String)

    private val channel get() = BuildConfig.CHANNEL

    /** Shows the update popup when GitHub has a newer release. [post] also reports "no update". */
    suspend fun check(activity: FragmentActivity, post: Boolean = false) {
        if (post) snackString(activity.getString(R.string.checking_for_update))
        val release = withContext(Dispatchers.IO) { runCatching { latest() }.getOrNull() }
        val newer = release != null && isNewer(release.version, BuildConfig.VERSION_NAME)
        val skipped = release != null && !post && PrefManager.getCustomVal(skipKey(release.version), false)
        if (!newer || skipped) {
            if (post) snackString(activity.getString(if (release == null) R.string.update_check_failed else R.string.no_update_found))
            return
        }
        withContext(Dispatchers.Main) {
            if (!activity.isFinishing && !activity.isDestroyed) UpdateDialog(activity, release!!).show()
        }
    }

    /** From the background check: one notification per new version; tapping it opens the app, which shows the popup. */
    suspend fun checkInBackground(context: Context) = withContext(Dispatchers.IO) {
        val release = runCatching { latest() }.getOrNull() ?: return@withContext
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return@withContext
        val notifiedKey = "update_notified_${release.version}"
        if (PrefManager.getCustomVal(notifiedKey, false)) return@withContext
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return@withContext
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationManagerCompat.from(context).notify(
            Notifications.CHANNEL_APP_GLOBAL, Notifications.ID_APP_UPDATE_PROMPT,
            NotificationCompat.Builder(context, Notifications.CHANNEL_APP_GLOBAL)
                .setSmallIcon(R.drawable.notification_icon)
                .setColor(ContextCompat.getColor(context, R.color.arkhime_red))
                .setContentTitle(context.getString(R.string.update_available_title, release.version))
                .setContentText(context.getString(R.string.update_available_text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
        PrefManager.setCustomVal(notifiedKey, true)
    }

    // ---- GitHub --------------------------------------------------------------------------------

    @Serializable
    private data class GithubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    ) {
        @Serializable
        data class Asset(val name: String, @SerialName("browser_download_url") val url: String)
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Cache-Control", "no-cache")
            .build()
        okHttpClient.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            return it.body.string()
        }
    }

    /** A release's version: "beta-v1.2.0" and "v1.2.0" are both 1.2.0 */
    private fun versionOf(tag: String) = tag.removePrefix("beta-").removePrefix("v")

    /** Is this GitHub release one this app follows? */
    private fun follows(r: GithubRelease) = !r.draft && !r.tagName.contains("fdroid") && when (channel) {
        "beta" -> r.prerelease && r.tagName.startsWith("beta-v")
        "release" -> !r.prerelease && !r.tagName.startsWith("beta-")
        else -> false
    }

    /** Newest release this build follows, straight from GitHub (callers treat a failure as "no update") */
    private fun latest(): Release? {
        // Alpha (this PC's builds) never updates itself: it is rebuilt and installed from the PC
        if (channel == "alpha") return null
        return json.decodeFromString<List<GithubRelease>>(get("https://api.github.com/repos/$REPO/releases?per_page=30"))
            .filter(::follows)
            .maxWithOrNull { a, b ->
                val va = versionOf(a.tagName)
                val vb = versionOf(b.tagName)
                if (isNewer(va, vb)) 1 else if (isNewer(vb, va)) -1 else 0
            }
            ?.let { Release(versionOf(it.tagName), it.body.orEmpty(), pickApk(it.assets), it.htmlUrl) }
    }

    /** The APK built for this phone's CPU, else the universal one */
    private fun pickApk(assets: List<GithubRelease.Asset>): String? {
        val apks = assets.filter { it.name.endsWith(".apk") && !it.name.contains("fdroid", true) }
        android.os.Build.SUPPORTED_ABIS.orEmpty().forEach { abi ->
            // Exact suffix, so "x86" doesn't pick the x86_64 APK
            apks.firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }?.let { return it.url }
        }
        return (apks.firstOrNull { it.name.contains("universal", true) } ?: apks.firstOrNull())?.url
    }

    /** Semantic comparison of "1.2.3", "v1.2.3", "1.2.3+hash" or "1.2.3-beta01" */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore("+").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun skipKey(version: String) = "dont_ask_for_update_$version"

    // ---- Popup ---------------------------------------------------------------------------------

    private class UpdateDialog(val activity: FragmentActivity, val release: Release) {
        private val status = TextView(activity)
        private val progress = LinearProgressIndicator(activity).apply { isVisible = false; max = 100 }
        private lateinit var dialog: AlertDialog
        private var apk: File? = null
        private var busy = false

        fun show() {
            val pad = (20 * activity.resources.displayMetrics.density).toInt()
            val notes = TextView(activity).apply {
                runCatching { buildMarkwon(activity, false).setMarkdown(this, release.notes.ifBlank { "-" }) }
                    .onFailure { text = release.notes }
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad / 2, pad, 0)
                addView(status)
                addView(progress)
                addView(NestedScrollView(activity).apply {
                    addView(notes)
                    setPadding(0, pad / 2, 0, 0)
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (220 * activity.resources.displayMetrics.density).toInt()))
            }
            status.text = activity.getString(R.string.update_ready, release.version)
            dialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_dialog)
                .setView(content)
                .setPositiveButton(if (release.apkUrl != null) R.string.update_now else R.string.update_open_page, null)
                .setNegativeButton(R.string.update_later, null)
                .setNeutralButton(R.string.update_skip, null)
                .setCancelable(false)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onPositive() }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dialog.dismiss() }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    PrefManager.setCustomVal(skipKey(release.version), true)
                    dialog.dismiss()
                }
            }
            dialog.show()
        }

        private fun onPositive() {
            if (busy) return
            val url = release.apkUrl
            if (url == null) {
                openLinkInBrowser(release.pageUrl)
                dialog.dismiss()
                return
            }
            val file = apk
            if (file != null) install(file) else download(url)
        }

        private fun download(url: String) {
            busy = true
            setButtonsEnabled(false)
            progress.isVisible = true
            progress.isIndeterminate = true
            status.text = activity.getString(R.string.downloading_update, release.version)
            activity.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { fetch(url) { percent -> activity.runOnUiThread { showProgress(percent) } } }
                }
                busy = false
                setButtonsEnabled(true)
                progress.isVisible = false
                result.onSuccess {
                    apk = it
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.update_install)
                    install(it)
                }.onFailure {
                    status.text = activity.getString(R.string.update_download_failed, it.message ?: "")
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.update_retry)
                }
            }
        }

        private fun showProgress(percent: Int) {
            if (percent < 0) return
            progress.isIndeterminate = false
            progress.setProgressCompat(percent, true)
            status.text = activity.getString(R.string.update_downloading_percent, release.version, percent)
        }

        private fun install(file: File) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                // Android's one-time "install unknown apps" switch; tapping Install again carries on
                status.text = activity.getString(R.string.update_needs_permission)
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    )
                }
                return
            }
            status.text = activity.getString(R.string.update_confirm_install)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        private fun setButtonsEnabled(enabled: Boolean) {
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL)
                .forEach { dialog.getButton(it)?.isEnabled = enabled }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setText(if (enabled) R.string.update_later else R.string.update_hide)
        }

        /** Downloads to cache/updates/update.apk, reporting whole percents (-1 when the size is unknown) */
        private fun fetch(url: String, onProgress: (Int) -> Unit): File {
            val target = File(File(activity.cacheDir, "updates").apply { mkdirs() }, "update.apk")
            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body
                val total = body.contentLength()
                var reported = -2
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            val percent = if (total > 0) (written * 100 / total).toInt() else -1
                            if (percent != reported) {
                                reported = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            if (target.length() == 0L) throw IOException("empty file")
            return target
        }
    }
}
