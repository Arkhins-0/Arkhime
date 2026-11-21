package com.arkhins.arkhime.feeds.crunchyroll

import com.arkhins.arkhime.connections.anilist.Anilist
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Crunchyroll data, read from AniList: which titles stream on Crunchyroll (externalLinks),
 * their Crunchyroll episode pages (streamingEpisodes) and when episodes air (airingSchedules).
 * Crunchyroll's own API needs a login and isn't public, so nothing here talks to it directly;
 * playback opens the Crunchyroll app or site.
 */
object Crunchyroll {
    private const val MEDIA_FIELDS =
        "id type format status episodes season seasonYear bannerImage " +
            "title{userPreferred romaji english} coverImage{large color} " +
            "nextAiringEpisode{episode airingAt} externalLinks{site url} " +
            "streamingEpisodes{title thumbnail url site}"

    private fun gqlString(s: String) = Json.encodeToString(s)

    suspend fun search(query: String): List<CrMedia> {
        val gql = "{Page(perPage:40){media(search:${gqlString(query)},type:ANIME,sort:SEARCH_MATCH){$MEDIA_FIELDS}}}"
        return Anilist.executeQuery<CrResponse>(gql, force = true, useToken = false)
            ?.data?.page?.media.orEmpty().filter { it.onCrunchyroll }
    }

    /** Episodes that aired on Crunchyroll titles between [fromSec] and [toSec], newest first. */
    suspend fun aired(fromSec: Long, toSec: Long, page: Int = 1): CrPage? {
        val gql = "{Page(page:$page,perPage:50){pageInfo{hasNextPage}airingSchedules(airingAt_greater:$fromSec,airingAt_lesser:$toSec,sort:TIME_DESC){id episode airingAt media{$MEDIA_FIELDS}}}}"
        return Anilist.executeQuery<CrResponse>(gql, force = true, useToken = false)?.data?.page
            ?.let { p -> p.copy(airingSchedules = p.airingSchedules?.filter { it.media?.onCrunchyroll == true }) }
    }

    /** Episodes coming up on Crunchyroll titles in the next [days] days, soonest first. */
    suspend fun upcoming(days: Int = 7, page: Int = 1): CrPage? {
        val now = System.currentTimeMillis() / 1000
        val gql = "{Page(page:$page,perPage:50){pageInfo{hasNextPage}airingSchedules(airingAt_greater:$now,airingAt_lesser:${now + days * 86400L},sort:TIME){id episode airingAt media{$MEDIA_FIELDS}}}}"
        return Anilist.executeQuery<CrResponse>(gql, force = true, useToken = false)?.data?.page
            ?.let { p -> p.copy(airingSchedules = p.airingSchedules?.filter { it.media?.onCrunchyroll == true }) }
    }
}

@Serializable
data class CrResponse(@SerialName("data") val data: Data? = null) {
    @Serializable
    data class Data(@SerialName("Page") val page: CrPage? = null)
}

@Serializable
data class CrPage(
    @SerialName("pageInfo") val pageInfo: CrPageInfo? = null,
    @SerialName("media") val media: List<CrMedia>? = null,
    @SerialName("airingSchedules") val airingSchedules: List<CrAiring>? = null,
)

@Serializable
data class CrPageInfo(@SerialName("hasNextPage") val hasNextPage: Boolean? = null)

@Serializable
data class CrAiring(
    @SerialName("id") val id: Int = 0,
    @SerialName("episode") val episode: Int = 0,
    @SerialName("airingAt") val airingAt: Long = 0,
    @SerialName("media") val media: CrMedia? = null,
)

@Serializable
data class CrMedia(
    @SerialName("id") val id: Int = 0,
    @SerialName("type") val type: String? = null,
    @SerialName("format") val format: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("episodes") val episodes: Int? = null,
    @SerialName("season") val season: String? = null,
    @SerialName("seasonYear") val seasonYear: Int? = null,
    @SerialName("bannerImage") val bannerImage: String? = null,
    @SerialName("title") val title: CrTitle? = null,
    @SerialName("coverImage") val coverImage: CrCover? = null,
    @SerialName("nextAiringEpisode") val nextAiringEpisode: CrNextEpisode? = null,
    @SerialName("externalLinks") val externalLinks: List<CrLink>? = null,
    @SerialName("streamingEpisodes") val streamingEpisodes: List<CrStreamingEpisode>? = null,
) {
    val name get() = title?.userPreferred ?: title?.english ?: title?.romaji ?: ""
    val crunchyrollUrl get() = externalLinks?.firstOrNull { it.site.equals("Crunchyroll", true) }?.url
    val crunchyrollEpisodes get() = streamingEpisodes.orEmpty().filter { it.site.equals("Crunchyroll", true) }
    val onCrunchyroll get() = crunchyrollUrl != null || crunchyrollEpisodes.isNotEmpty()
}

@Serializable
data class CrTitle(
    @SerialName("userPreferred") val userPreferred: String? = null,
    @SerialName("romaji") val romaji: String? = null,
    @SerialName("english") val english: String? = null,
)

@Serializable
data class CrCover(
    @SerialName("large") val large: String? = null,
    @SerialName("color") val color: String? = null,
)

@Serializable
data class CrNextEpisode(
    @SerialName("episode") val episode: Int = 0,
    @SerialName("airingAt") val airingAt: Long = 0,
)

@Serializable
data class CrLink(
    @SerialName("site") val site: String? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class CrStreamingEpisode(
    @SerialName("title") val title: String? = null,
    @SerialName("thumbnail") val thumbnail: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("site") val site: String? = null,
)
