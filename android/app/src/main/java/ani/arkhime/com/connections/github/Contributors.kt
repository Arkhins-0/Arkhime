package ani.arkhime.com.connections.github

import ani.arkhime.com.client
import ani.arkhime.com.settings.Developer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class Contributors {

    suspend fun getContributors(): Array<Developer> {
        val developers = mutableListOf<Developer>()

        try {
            val res = client.get("https://api.github.com/repos/$REPO/contributors?per_page=100")
            val json = Json { ignoreUnknownKeys = true }
            val users: List<GithubContributor> = json.decodeFromString(res.text)

            users.filterNot { it.type == "Bot" || it.login.endsWith("[bot]") }.forEach { user ->
                developers.add(
                    Developer(
                        name = user.login,
                        pfp = user.avatarUrl,
                        role = if (user.login.equals(OWNER, ignoreCase = true)) "Owner & Maintainer" else "Contributor",
                        url = user.htmlUrl
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Repo unreachable or not public yet: still show the maintainer
        if (developers.none { it.name.equals(OWNER, ignoreCase = true) }) {
            developers.add(
                0,
                Developer(
                    OWNER,
                    "https://github.com/$OWNER.png",
                    "Owner & Maintainer",
                    "https://github.com/$OWNER"
                )
            )
        }

        return developers.toTypedArray()
    }

    @Serializable
    data class GithubContributor(
        val login: String,
        @SerialName("avatar_url")
        val avatarUrl: String,
        @SerialName("html_url")
        val htmlUrl: String,
        val type: String? = null,
    )

    companion object {
        private const val OWNER = "Arkhins-0"
        private const val REPO = "$OWNER/Arkhime"
    }
}
