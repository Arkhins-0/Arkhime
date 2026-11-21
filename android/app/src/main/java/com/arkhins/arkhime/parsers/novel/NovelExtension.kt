package com.arkhins.arkhime.parsers.novel

import android.graphics.drawable.Drawable
import com.arkhins.arkhime.parsers.NovelInterface

sealed class NovelExtension {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        val sources: List<NovelInterface>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isUnofficial: Boolean = false,
    ) : NovelExtension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        var repository: String,
        val sources: List<AvailableNovelSources>,
        val iconUrl: String,
        /** Full download URL from a Mihon-style store index; null for legacy repos. */
        val apkUrl: String? = null,
    ) : NovelExtension()
}

data class AvailableNovelSources(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
) {
    fun toNovelSourceData(): NovelSourceData {
        return NovelSourceData(
            id = this.id,
            lang = this.lang,
            name = this.name,
        )
    }
}

data class NovelSourceData(
    val id: Long,
    val lang: String,
    val name: String,
) {

    val isMissingInfo: Boolean = name.isBlank() || lang.isBlank()
}
