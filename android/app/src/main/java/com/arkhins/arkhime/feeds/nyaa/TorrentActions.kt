package com.arkhins.arkhime.feeds.nyaa

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.arkhins.arkhime.copyToClipboard
import com.arkhins.arkhime.snackString

object TorrentActions {

    /** Hands the magnet link to whichever torrent app is installed. */
    fun openMagnet(context: Context, torrent: NyaaTorrent) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(torrent.magnet)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            copyToClipboard(torrent.magnet, false)
            snackString("No torrent app found. The magnet link is copied to your clipboard.")
        }
    }

    /** Saves the .torrent file to Downloads/Arkhime through the system download manager. */
    fun downloadTorrentFile(context: Context, torrent: NyaaTorrent) {
        val fileName = torrent.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(150) + ".torrent"
        val request = DownloadManager.Request(Uri.parse(torrent.torrentUrl))
            .setTitle(torrent.title)
            .setDescription("nyaa.si torrent file")
            .setMimeType("application/x-bittorrent")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Arkhime/$fileName")
        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            snackString("Downloading $fileName")
        }.onFailure { snackString("Couldn't start the download: ${it.message}") }
    }

    fun copyMagnet(torrent: NyaaTorrent) = copyToClipboard(torrent.magnet, true)

    fun share(context: Context, torrent: NyaaTorrent) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, torrent.title)
            .putExtra(Intent.EXTRA_TEXT, "${torrent.title}\n${torrent.viewUrl}\n\n${torrent.magnet}")
        context.startActivity(Intent.createChooser(intent, torrent.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openPage(context: Context, torrent: NyaaTorrent) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(torrent.viewUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
