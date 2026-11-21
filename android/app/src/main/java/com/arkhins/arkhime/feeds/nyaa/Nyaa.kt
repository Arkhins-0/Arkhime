package com.arkhins.arkhime.feeds.nyaa

import com.arkhins.arkhime.client
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/** nyaa.si and its adult sister site sukebei.nyaa.si: same software, same RSS, different categories. */
enum class NyaaSite(val base: String, val label: String, val trackers: List<String>) {
    NYAA(
        "https://nyaa.si", "nyaa.si",
        listOf("http://nyaa.tracker.wf:7777/announce") + Nyaa.openTrackers,
    ),
    SUKEBEI(
        "https://sukebei.nyaa.si", "sukebei.nyaa.si",
        listOf("http://sukebei.tracker.wf:8888/announce") + Nyaa.openTrackers,
    );

    companion object {
        fun ofUrl(url: String) = if (url.contains("sukebei.")) SUKEBEI else NYAA
    }
}

/** One torrent as listed in a nyaa RSS feed. */
@Serializable
data class NyaaTorrent(
    val id: Long,
    val title: String,
    val torrentUrl: String,
    val viewUrl: String,
    val pubDate: Long,
    val seeders: Int,
    val leechers: Int,
    val downloads: Int,
    val infoHash: String,
    val categoryId: String,
    val category: String,
    val size: String,
    val comments: Int,
    val trusted: Boolean,
    val remake: Boolean,
) : java.io.Serializable {
    val magnet: String
        get() = buildString {
            append("magnet:?xt=urn:btih:").append(infoHash)
            append("&dn=").append(URLEncoder.encode(title, "UTF-8"))
            NyaaSite.ofUrl(viewUrl).trackers.forEach { append("&tr=").append(URLEncoder.encode(it, "UTF-8")) }
        }
}

data class NyaaComment(val user: String, val avatar: String?, val date: String, val text: String)

data class NyaaDetails(
    val submitter: String?,
    val information: String?,
    val description: String?,
    val files: List<String>,
    val comments: List<NyaaComment>,
)

/**
 * Which category a search runs in. The site's own codes overlap between nyaa and sukebei, so
 * categories are stored by enum name ([key]); [from] still accepts a bare nyaa.si code.
 */
enum class NyaaCategory(val site: NyaaSite, val code: String, val label: String) {
    ALL(NyaaSite.NYAA, "0_0", "All"),
    ANIME(NyaaSite.NYAA, "1_0", "Anime"),
    ANIME_ENGLISH(NyaaSite.NYAA, "1_2", "English"),
    ANIME_RAW(NyaaSite.NYAA, "1_4", "Raw"),
    MANGA(NyaaSite.NYAA, "3_0", "Manga"),
    MANGA_ENGLISH(NyaaSite.NYAA, "3_1", "Manga English"),
    MANGA_RAW(NyaaSite.NYAA, "3_3", "Manga Raw"),

    SUKEBEI_ALL(NyaaSite.SUKEBEI, "0_0", "All"),
    SUKEBEI_ART(NyaaSite.SUKEBEI, "1_0", "Art"),
    SUKEBEI_ANIME(NyaaSite.SUKEBEI, "1_1", "Anime"),
    SUKEBEI_DOUJINSHI(NyaaSite.SUKEBEI, "1_2", "Doujinshi"),
    SUKEBEI_GAMES(NyaaSite.SUKEBEI, "1_3", "Games"),
    SUKEBEI_MANGA(NyaaSite.SUKEBEI, "1_4", "Manga"),
    SUKEBEI_PICTURES(NyaaSite.SUKEBEI, "1_5", "Pictures"),
    SUKEBEI_REAL_LIFE(NyaaSite.SUKEBEI, "2_0", "Real Life");

    val key get() = name

    companion object {
        fun from(key: String?): NyaaCategory = entries.firstOrNull { it.name == key }
            ?: entries.firstOrNull { it.site == NyaaSite.NYAA && it.code == key }
            ?: ANIME

        fun of(site: NyaaSite) = entries.filter { it.site == site }

        fun defaultFor(site: NyaaSite) = if (site == NyaaSite.SUKEBEI) SUKEBEI_ALL else ANIME
    }
}

/** nyaa.si's `f` parameter. */
enum class NyaaFilter(val code: Int, val label: String) {
    NONE(0, "All"), NO_REMAKES(1, "No remakes"), TRUSTED(2, "Trusted");

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code } ?: NONE
    }
}

enum class NyaaSort(val code: String, val label: String) {
    NEWEST("id", "Newest"),
    SEEDERS("seeders", "Seeders"),
    DOWNLOADS("downloads", "Downloads"),
    SIZE("size", "Size");
}

object Nyaa {
    /** Public trackers both sites add to their magnet links, after their own tracker. */
    val openTrackers = listOf(
        "udp://open.stealth.si:80/announce",
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
    )

    /** "Anime · Trusted": the category plus the trusted / remake mark, for the small label above a title. */
    fun label(context: android.content.Context, t: NyaaTorrent): String = listOfNotNull(
        t.category.substringBefore(" - ").ifBlank { null },
        if (t.trusted) context.getString(com.arkhins.arkhime.R.string.nyaa_trusted) else null,
        if (t.remake) context.getString(com.arkhins.arkhime.R.string.nyaa_remake) else null,
    ).joinToString(" · ")

    private val rssDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun rssUrl(
        query: String,
        category: NyaaCategory = NyaaCategory.ANIME,
        filter: NyaaFilter = NyaaFilter.NONE,
        sort: NyaaSort = NyaaSort.NEWEST,
        page: Int = 1,
    ): String {
        val q = URLEncoder.encode(query.replace(',', ' ').trim(), "UTF-8")
        return "${category.site.base}/?page=rss&q=$q&c=${category.code}&f=${filter.code}&s=${sort.code}&o=desc&p=$page"
    }

    suspend fun search(
        query: String,
        category: NyaaCategory = NyaaCategory.ANIME,
        filter: NyaaFilter = NyaaFilter.NONE,
        sort: NyaaSort = NyaaSort.NEWEST,
        page: Int = 1,
    ): List<NyaaTorrent> {
        val res = client.get(rssUrl(query, category, filter, sort, page))
        if (res.code !in 200..299) throw Exception("${category.site.label} returned ${res.code}")
        return parseRss(res.text)
    }

    fun parseRss(xml: String): List<NyaaTorrent> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.getElementsByTag("item").mapNotNull { item ->
            runCatching {
                val view = item.tagText("guid")
                NyaaTorrent(
                    id = view.substringAfterLast('/').toLong(),
                    title = item.tagText("title"),
                    torrentUrl = item.tagText("link"),
                    viewUrl = view,
                    pubDate = runCatching {
                        synchronized(rssDate) { rssDate.parse(item.tagText("pubDate"))!!.time }
                    }.getOrDefault(0L),
                    seeders = item.tagText("nyaa:seeders").toIntOrNull() ?: 0,
                    leechers = item.tagText("nyaa:leechers").toIntOrNull() ?: 0,
                    downloads = item.tagText("nyaa:downloads").toIntOrNull() ?: 0,
                    infoHash = item.tagText("nyaa:infoHash"),
                    categoryId = item.tagText("nyaa:categoryId"),
                    category = item.tagText("nyaa:category"),
                    size = item.tagText("nyaa:size"),
                    comments = item.tagText("nyaa:comments").toIntOrNull() ?: 0,
                    trusted = item.tagText("nyaa:trusted").equals("Yes", true),
                    remake = item.tagText("nyaa:remake").equals("Yes", true),
                )
            }.getOrNull()
        }
    }

    /** Reads the torrent's public page for its description, file list and comments. */
    suspend fun details(torrent: NyaaTorrent): NyaaDetails {
        val doc = client.get(torrent.viewUrl).document
        fun row(label: String): String? = doc.select("div.row > div.col-md-1")
            .firstOrNull { it.text().trim() == label }
            ?.nextElementSibling()?.text()?.trim()?.takeIf { it.isNotBlank() && it != "No information." }

        // Only the file entries themselves: a folder's <li> also contains its files, so it is skipped
        val files = doc.select("div.torrent-file-list li")
            .filter { li -> li.children().any { it.tagName() == "i" && it.hasClass("fa-file") } }
            .map { li ->
                val size = li.children().firstOrNull { it.hasClass("file-size") }?.text()
                listOfNotNull(li.ownText().trim().ifBlank { null }, size).joinToString("  ")
            }
        val comments = doc.select("div.comment-panel").map { panel ->
            NyaaComment(
                user = panel.selectFirst("div.col-md-2 p a")?.text()?.trim() ?: "Anonymous",
                avatar = panel.selectFirst("img.avatar")?.absUrl("src")?.takeIf { it.isNotBlank() },
                date = panel.selectFirst("small[data-timestamp]")?.text()?.trim() ?: "",
                text = panel.selectFirst("div.comment-content")?.text()?.trim() ?: "",
            )
        }
        return NyaaDetails(
            submitter = row("Submitter:"),
            information = row("Information:"),
            description = doc.selectFirst("#torrent-description")?.wholeText()?.trim()
                ?.takeIf { it.isNotBlank() && it != "#### No description." },
            files = files,
            comments = comments,
        )
    }

    private fun Element.tagText(tag: String) = getElementsByTag(tag).firstOrNull()?.text()?.trim() ?: ""
}
