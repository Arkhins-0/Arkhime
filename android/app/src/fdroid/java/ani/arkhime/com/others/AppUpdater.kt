package ani.arkhime.com.others

import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

object AppUpdater {
    suspend fun check(activity: FragmentActivity, post: Boolean = false) {
        // no-op: F-Droid delivers updates itself
    }

    suspend fun checkInBackground(context: android.content.Context) {
        // no-op: F-Droid delivers updates itself
    }

    @Serializable
    data class GithubResponse(
        @SerialName("html_url")
        val htmlUrl: String,
        @SerialName("tag_name")
        val tagName: String,
        val prerelease: Boolean,
        @SerialName("created_at")
        val createdAt: String,
        val body: String? = null,
        val assets: List<Asset>? = null
    ) {
        @Serializable
        data class Asset(
            @SerialName("browser_download_url")
            val browserDownloadURL: String
        )

        fun timeStamp(): Long {
            return dateFormat.parse(createdAt)!!.time
        }

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        }
    }
}
