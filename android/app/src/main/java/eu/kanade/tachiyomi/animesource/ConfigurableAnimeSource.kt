package eu.kanade.tachiyomi.animesource

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.PreferenceScreen
import eu.kanade.tachiyomi.source.anime.getPreferenceKey
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

interface ConfigurableAnimeSource : AnimeSource {

    /**
     * The source's own preferences, the same file the app's source settings screen edits.
     *
     * Part of the Aniyomi extensions-lib API (lib 14): extensions call it to read their settings.
     */
    fun getSourcePreferences(): SharedPreferences =
        Injekt.get<Application>().getSharedPreferences(getPreferenceKey(), Context.MODE_PRIVATE)

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
