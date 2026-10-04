package ani.arkhime.com.media.manga

import ani.arkhime.com.media.Author
import java.io.Serializable

data class Manga(
    var totalChapters: Int? = null,
    var totalVolumes: Int? = null,
    var slug: String? = null,
    var author: Author? = null,
) : Serializable
