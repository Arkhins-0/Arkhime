package com.arkhins.arkhime.settings

import com.arkhins.arkhime.settings.paging.StoreItem
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionsFragment : AvailableExtensionsFragment<MangaExtension.Available>() {
    private val mangaExtensionManager: MangaExtensionManager = Injekt.get()

    override val availableExtensions: Flow<List<MangaExtension.Available>>
        get() = mangaExtensionManager.availableExtensionsFlow

    override val installedPackageNames: Flow<List<String>>
        get() = mangaExtensionManager.installedExtensionsFlow.map { list -> list.map { it.pkgName } }

    override fun item(extension: MangaExtension.Available) = StoreItem(
        name = extension.name,
        pkgName = extension.pkgName,
        lang = extension.lang,
        versionName = extension.versionName,
        isNsfw = extension.isNsfw,
        iconUrl = extension.iconUrl,
    )

    override fun install(extension: MangaExtension.Available): Observable<InstallStep> =
        mangaExtensionManager.installExtension(extension)
}
