package ani.arkhime.com.others

import ani.arkhime.com.client
import ani.arkhime.com.tryWithSuspend
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
