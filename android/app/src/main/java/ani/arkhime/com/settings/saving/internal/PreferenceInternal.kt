package ani.arkhime.com.settings.saving.internal

import kotlin.reflect.KClass

data class Pref(
    val prefLocation: Location,
    val type: KClass<*>,
    val default: Any
)

enum class Location(val location: String, val exportable: Boolean) {
    General("ani.arkhime.com.general", true),
    UI("ani.arkhime.com.ui", true),
    Player("ani.arkhime.com.player", true),
    Reader("ani.arkhime.com.reader", true),
    NovelReader("ani.arkhime.com.novelReader", true),
    Irrelevant("ani.arkhime.com.irrelevant", false),
    AnimeDownloads("animeDownloads", false),  //different for legacy reasons
    Protected("ani.arkhime.com.protected", true),
    ExtensionSettings("ani.arkhime.com.extensionSettings", true);
}
