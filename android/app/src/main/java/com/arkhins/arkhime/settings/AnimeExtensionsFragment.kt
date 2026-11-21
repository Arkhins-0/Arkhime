package com.arkhins.arkhime.settings

import com.arkhins.arkhime.settings.paging.StoreItem
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionsFragment : AvailableExtensionsFragment<AnimeExtension.Available>() {
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()

    override val availableExtensions: Flow<List<AnimeExtension.Available>>
        get() = animeExtensionManager.availableExtensionsFlow

    override val installedPackageNames: Flow<List<String>>
        get() = animeExtensionManager.installedExtensionsFlow.map { list -> list.map { it.pkgName } }

    override fun item(extension: AnimeExtension.Available) = StoreItem(
        name = extension.name,
        pkgName = extension.pkgName,
        lang = extension.lang,
        versionName = extension.versionName,
        isNsfw = extension.isNsfw,
        iconUrl = extension.iconUrl,
    )

    override fun install(extension: AnimeExtension.Available): Observable<InstallStep> =
        animeExtensionManager.installExtension(extension)
}
