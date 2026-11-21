package com.arkhins.arkhime.addons.torrent

import com.arkhins.arkhime.addons.LoadResult

open class TorrentLoadResult : LoadResult() {
    class Success(val extension: TorrentAddon.Installed) : TorrentLoadResult()
}