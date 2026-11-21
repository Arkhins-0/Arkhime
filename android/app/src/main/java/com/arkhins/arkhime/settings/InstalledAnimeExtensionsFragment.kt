package com.arkhins.arkhime.settings

import androidx.fragment.app.Fragment
import com.arkhins.arkhime.parsers.AnimeSources
import com.arkhins.arkhime.settings.extensionprefs.AnimeSourcePreferencesFragment
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.flow.StateFlow
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class InstalledAnimeExtensionsFragment : InstalledExtensionsFragment<AnimeExtension.Installed>() {
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()

    override val installedExtensions: StateFlow<List<AnimeExtension.Installed>>
        get() = animeExtensionManager.installedExtensionsFlow

    override fun info(extension: AnimeExtension.Installed) = Info(
        name = extension.name,
        pkgName = extension.pkgName,
        versionName = extension.versionName,
        lang = extension.lang,
        isNsfw = extension.isNsfw,
        hasUpdate = extension.hasUpdate,
        icon = extension.icon,
    )

    override fun configurableSources(extension: AnimeExtension.Installed) =
        extension.sources.filterIsInstance<ConfigurableAnimeSource>().map { Configurable(it.id, it.lang) }

    override fun settingsFragment(sourceId: Long, onClose: () -> Unit): Fragment =
        AnimeSourcePreferencesFragment().getInstance(sourceId, onClose)

    override fun uninstall(extension: AnimeExtension.Installed) =
        animeExtensionManager.uninstallExtension(extension.pkgName)

    override fun update(extension: AnimeExtension.Installed): Observable<*> =
        animeExtensionManager.updateExtension(extension)

    override val pinnedSources: List<String>
        get() = AnimeSources.pinnedAnimeSources

    override fun saveOrder(names: List<String>) {
        PrefManager.setVal(PrefName.AnimeSourcesOrder, names)
        AnimeSources.pinnedAnimeSources = names
        AnimeSources.performReorderAnimeSources()
    }
}
