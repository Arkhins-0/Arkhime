package com.arkhins.arkhime.others

import com.arkhins.arkhime.client
import com.arkhins.arkhime.media.anime.Episode
import com.arkhins.arkhime.tryWithSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Jikan {

    val apiUrls = listOf(
        "https://api.jikan.moe/v4/",
        "https://jikanfortheweebs.midnightignite.me/v4/",
        "https://api.tenrai.org/v1/"
    )

    suspend inline fun <reified T : Any> query(endpoint: String): T? {
        val cleanEndpoint = endpoint.removePrefix("/")
        for (base in apiUrls) {
            val res = tryWithSuspend {
                val response = client.get("$base$cleanEndpoint")
                if (response.code in 200..299) {
                    response.parsed<T>()
                } else null
            }
            if (res != null) return res
        }
        return null
    }

    /** Episode titles and filler flags from MyAnimeList (restored from Dantotsu). */
    suspend fun getEpisodes(malId: Int): Map<String, Episode> {
        var hasNextPage = true
        var page = 0
        val eps = mutableMapOf<String, Episode>()
        while (hasNextPage) {
            page++
            val res = query<EpisodeResponse>("anime/$malId/episodes?page=$page") ?: break
            res.data?.forEach {
                val ep = it.malID.toString()
                eps[ep] = Episode(ep, title = it.title, filler = it.filler)
            }
            hasNextPage = res.pagination?.hasNextPage == true
        }
        return eps
    }

    @Serializable
    data class EpisodeResponse(
        val pagination: Pagination? = null,
        val data: List<Datum>? = null
    ) {
        @Serializable
        data class Datum(
            @SerialName("mal_id")
            val malID: Int,
            val title: String? = null,
            val filler: Boolean,
            val aired: String? = null,
            //            val recap: Boolean,
        )

        @Serializable
        data class Pagination(
            @SerialName("has_next_page")
            val hasNextPage: Boolean? = null
        )
    }

}
