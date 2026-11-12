package ani.arkhime.com.addons.download

import ani.arkhime.com.addons.LoadResult

open class DownloadLoadResult : LoadResult() {
    class Success(val extension: DownloadAddon.Installed) : DownloadLoadResult()
}