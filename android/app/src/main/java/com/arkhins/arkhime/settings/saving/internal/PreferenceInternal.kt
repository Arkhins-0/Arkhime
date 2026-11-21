package com.arkhins.arkhime.settings.saving.internal

import kotlin.reflect.KClass

data class Pref(
    val prefLocation: Location,
    val type: KClass<*>,
    val default: Any
)

enum class Location(val location: String, val exportable: Boolean) {
    General("com.arkhins.arkhime.general", true),
    UI("com.arkhins.arkhime.ui", true),
    Player("com.arkhins.arkhime.player", true),
    Reader("com.arkhins.arkhime.reader", true),
    NovelReader("com.arkhins.arkhime.novelReader", true),
    Irrelevant("com.arkhins.arkhime.irrelevant", false),
    AnimeDownloads("animeDownloads", false),  //different for legacy reasons
    Protected("com.arkhins.arkhime.protected", true),
    ExtensionSettings("com.arkhins.arkhime.extensionSettings", true);
}
