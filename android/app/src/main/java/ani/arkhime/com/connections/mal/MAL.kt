package ani.arkhime.com.connections.mal

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import ani.arkhime.com.R
import ani.arkhime.com.client
import ani.arkhime.com.currContext
import ani.arkhime.com.openLinkInBrowser
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.tryWithSuspend
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom

object MAL {
    val query: MALQueries = MALQueries()
    val jikan: JikanQueries = JikanQueries()
    const val clientId = "c3f11b4aa669c326f5d28aa9d6a9e0e4"
    var username: String? = null
    var avatar: String? = null
    var token: String? = null
    var userid: Int? = null
    var episodesWatched: Int? = null
    var chaptersRead: Int? = null

    fun loginIntent(context: Context) {
        val codeVerifierBytes = ByteArray(96)
        SecureRandom().nextBytes(codeVerifierBytes)
        val codeChallenge = Base64.encodeToString(codeVerifierBytes, Base64.DEFAULT).trimEnd('=')
            .replace("+", "-")
            .replace("/", "_")
            .replace("\n", "")

        PrefManager.setVal(PrefName.MALCodeChallenge, codeChallenge)
        val request =
            "https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id=$clientId&code_challenge=$codeChallenge"
        try {
            CustomTabsIntent.Builder().build().launchUrl(
                context,
                Uri.parse(request)
            )
        } catch (e: ActivityNotFoundException) {
            openLinkInBrowser(request)
        }
    }

    private suspend fun refreshToken(): ResponseToken? {
        return tryWithSuspend {
            val token = PrefManager.getNullableVal<ResponseToken>(PrefName.MALToken, null)
                ?: throw Exception(currContext()?.getString(R.string.refresh_token_load_failed))
            val res = client.post(
                "https://myanimelist.net/v1/oauth2/token",
                data = mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to token.refreshToken
                )
            ).parsed<ResponseToken>()
            saveResponse(res)
            return@tryWithSuspend res
        }
    }


    suspend fun getSavedToken(): Boolean {
        return tryWithSuspend(false) {
            var res: ResponseToken =
                PrefManager.getNullableVal<ResponseToken>(PrefName.MALToken, null)
                    ?: return@tryWithSuspend false
            if (System.currentTimeMillis() > res.expiresIn)
                res = refreshToken()
                    ?: throw Exception(currContext()?.getString(R.string.refreshing_token_failed))
            token = res.accessToken
            username = PrefManager.getVal(PrefName.MALUserName, null as String?)
            avatar = PrefManager.getVal(PrefName.MALAvatar, null as String?)
            return@tryWithSuspend true
        } ?: false
    }

    fun removeSavedToken() {
        token = null
        username = null
        userid = null
        avatar = null
        episodesWatched = null
        chaptersRead = null
        PrefManager.removeVal(PrefName.MALToken)
    }

    fun saveResponse(res: ResponseToken) {
        res.expiresIn += System.currentTimeMillis()
        PrefManager.setVal(PrefName.MALToken, res)
    }

    @Serializable
    data class ResponseToken(
        @SerialName("token_type") val tokenType: String,
        @SerialName("expires_in") var expiresIn: Long,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

}
