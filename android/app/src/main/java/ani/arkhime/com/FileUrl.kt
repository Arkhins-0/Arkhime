package ani.arkhime.com

import java.io.Serializable

/**
 * A url, which can also have headers
 * **/
data class FileUrl(
    var url: String,
    var headers: Map<String, String> = mapOf()
) : Serializable {
    companion object {
        operator fun get(url: String?, headers: Map<String, String> = mapOf()): FileUrl? {
            return FileUrl(url ?: return null, headers)
        }

        private const val serialVersionUID = 1L
    }
}
