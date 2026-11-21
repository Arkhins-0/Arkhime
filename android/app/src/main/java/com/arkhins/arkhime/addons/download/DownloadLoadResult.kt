package com.arkhins.arkhime.addons.download

import com.arkhins.arkhime.addons.LoadResult

open class DownloadLoadResult : LoadResult() {
    class Success(val extension: DownloadAddon.Installed) : DownloadLoadResult()
}