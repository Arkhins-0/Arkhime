package ani.arkhime.com.aniyomi.anime.custom

import android.app.Application
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import ani.arkhime.com.addons.download.DownloadAddonManager
import ani.arkhime.com.addons.torrent.TorrentAddonManager
import ani.arkhime.com.download.DownloadsManager
import ani.arkhime.com.media.manga.MangaCache
import ani.arkhime.com.parsers.novel.NovelExtensionManager
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Services for the features restored from Dantotsu (extensions, sources, addons, downloads).
 *
 * Based on Dantotsu's AppModule and PreferenceModule, trimmed: the application, NetworkHelper,
 * its OkHttp client, Json, PreferenceStore and BasePreferences already come from Arkhime's Metro
 * graph through MetroInteropModule, so registering them again here would create second copies.
 */
class FeatureModule(val app: Application) : InjektModule {
    @OptIn(UnstableApi::class)
    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { DownloadsManager(app) }

        addSingletonFactory { AnimeExtensionManager(app) }
        addSingletonFactory { MangaExtensionManager(app) }
        addSingletonFactory { NovelExtensionManager(app) }
        addSingletonFactory { TorrentAddonManager(app) }
        addSingletonFactory { DownloadAddonManager(app) }

        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get()) }
        addSingletonFactory<MangaSourceManager> { AndroidMangaSourceManager(app, get()) }

        addSingletonFactory { SourcePreferences(get()) }
        addSingletonFactory { StandaloneDatabaseProvider(app) }
        addSingletonFactory { MangaCache() }

        ContextCompat.getMainExecutor(app).execute {
            get<AnimeSourceManager>()
            get<MangaSourceManager>()
        }
    }
}
