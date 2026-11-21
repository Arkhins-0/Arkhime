package com.arkhins.arkhime.settings

import androidx.fragment.app.Fragment
import com.arkhins.arkhime.parsers.MangaSources
import com.arkhins.arkhime.settings.extensionprefs.MangaSourcePreferencesFragment
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.flow.StateFlow
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class InstalledMangaExtensionsFragment : InstalledExtensionsFragment<MangaExtension.Installed>() {
    private val mangaExtensionManager: MangaExtensionManager = Injekt.get()

    override val installedExtensions: StateFlow<List<MangaExtension.Installed>>
        get() = mangaExtensionManager.installedExtensionsFlow

    override fun info(extension: MangaExtension.Installed) = Info(
        name = extension.name,
        pkgName = extension.pkgName,
        versionName = extension.versionName,
        lang = extension.lang,
        isNsfw = extension.isNsfw,
        hasUpdate = extension.hasUpdate,
        icon = extension.icon,
    )

    override fun configurableSources(extension: MangaExtension.Installed) =
        extension.sources.filterIsInstance<ConfigurableSource>().map { Configurable(it.id, it.lang) }

    override fun settingsFragment(sourceId: Long, onClose: () -> Unit): Fragment =
        MangaSourcePreferencesFragment().getInstance(sourceId, onClose)

    override fun uninstall(extension: MangaExtension.Installed) =
        mangaExtensionManager.uninstallExtension(extension.pkgName)

    override fun update(extension: MangaExtension.Installed): Observable<*> =
        mangaExtensionManager.updateExtension(extension)

    override val pinnedSources: List<String>
        get() = MangaSources.pinnedMangaSources

    override fun saveOrder(names: List<String>) {
        PrefManager.setVal(PrefName.MangaSourcesOrder, names)
        MangaSources.pinnedMangaSources = names
        MangaSources.performReorderMangaSources()
    }
}
