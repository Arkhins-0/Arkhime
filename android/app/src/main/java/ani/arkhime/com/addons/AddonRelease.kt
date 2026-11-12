package ani.arkhime.com.addons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A GitHub release of an addon, as returned by the GitHub releases API. */
@Serializable
data class AddonRelease(
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("tag_name") val tagName: String,
    val prerelease: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    val body: String? = null,
    val assets: List<Asset>? = null,
) {
    @Serializable
    data class Asset(
        @SerialName("browser_download_url") val browserDownloadURL: String,
    )

    /** Release time in epoch milliseconds (GitHub timestamps are UTC). */
    fun timeStamp(): Long = dateFormat().parse(createdAt)?.time ?: 0L

    private companion object {
        fun dateFormat() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
