package ani.arkhime.com.addons.torrent

import ani.arkhime.com.addons.LoadResult

open class TorrentLoadResult : LoadResult() {
    class Success(val extension: TorrentAddon.Installed) : TorrentLoadResult()
}