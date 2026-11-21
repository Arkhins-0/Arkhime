package eu.kanade.tachiyomi.extension.api

import com.arkhins.arkhime.parsers.novel.AvailableNovelSources
import com.arkhins.arkhime.parsers.novel.NovelExtension
import com.arkhins.arkhime.settings.saving.PrefManager
import com.arkhins.arkhime.settings.saving.PrefName
import com.arkhins.arkhime.util.Logger
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AvailableAnimeSources
import eu.kanade.tachiyomi.extension.manga.model.AvailableMangaSources
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Reads extension stores in both formats:
 *
 * - the legacy `index.min.json` list (Tachiyomi, Aniyomi, Dantotsu novel repos)
 * - the Mihon 0.20+ store index, `index.pb` (protobuf, usually gzipped) or `index.json`
 *
 * A legacy URL whose `repo.json` names an `index_v2` is followed to the new index, so a repo
 * saved before the store moved keeps working.
 */
internal class ExtensionGithubApi {
    private val networkService: NetworkHelper by injectLazy()

    private val storeJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val protoBuf = ProtoBuf

    /** One extension from any store format, before it's turned into an anime, manga or novel model. */
    private class RepoEntry(
        val name: String,
        val pkg: String,
        val apkName: String,
        /** Full download URL; only the new store format gives one. */
        val apkUrl: String?,
        val iconUrl: String,
        val lang: String,
        val versionCode: Long,
        val versionName: String,
        val libVersion: Double,
        val isNsfw: Boolean,
        val hasReadme: Boolean,
        val hasChangelog: Boolean,
        val sources: List<RepoSource>,
    )

    private class RepoSource(val id: Long, val lang: String, val name: String, val baseUrl: String)

    // ============================== Anime ===============================

    suspend fun findAnimeExtensions(): List<AnimeExtension.Available> = withIOContext {
        PrefManager.getVal<Set<String>>(PrefName.AnimeExtensionRepos).flatMap { repo ->
            fetchRepoOrEmpty(repo)
                .filter { it.libVersion in ExtensionLoader.ANIME_LIB_VERSION_MIN.toDouble()..ExtensionLoader.ANIME_LIB_VERSION_MAX.toDouble() }
                .map {
                    AnimeExtension.Available(
                        name = it.name.substringAfter("Aniyomi: "),
                        pkgName = it.pkg,
                        versionName = it.versionName,
                        versionCode = it.versionCode,
                        libVersion = it.libVersion,
                        lang = it.lang,
                        isNsfw = it.isNsfw,
                        hasReadme = it.hasReadme,
                        hasChangelog = it.hasChangelog,
                        sources = it.sources.map { s -> AvailableAnimeSources(s.id, s.lang, s.name, s.baseUrl) },
                        apkName = it.apkName,
                        iconUrl = it.iconUrl,
                        repository = repo,
                        apkUrl = it.apkUrl,
                    )
                }
        }
    }

    fun getAnimeApkUrl(extension: AnimeExtension.Available): String {
        return extension.apkUrl
            ?: "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.apkName}"
    }

    // ============================== Manga ===============================

    suspend fun findMangaExtensions(): List<MangaExtension.Available> = withIOContext {
        PrefManager.getVal<Set<String>>(PrefName.MangaExtensionRepos).flatMap { repo ->
            fetchRepoOrEmpty(repo)
                .filter { it.libVersion in ExtensionLoader.MANGA_LIB_VERSION_MIN..ExtensionLoader.MANGA_LIB_VERSION_MAX }
                .map {
                    MangaExtension.Available(
                        name = it.name.substringAfter("Tachiyomi: "),
                        pkgName = it.pkg,
                        versionName = it.versionName,
                        versionCode = it.versionCode,
                        libVersion = it.libVersion,
                        lang = it.lang,
                        isNsfw = it.isNsfw,
                        hasReadme = it.hasReadme,
                        hasChangelog = it.hasChangelog,
                        sources = it.sources.map { s -> AvailableMangaSources(s.id, s.lang, s.name, s.baseUrl) },
                        apkName = it.apkName,
                        iconUrl = it.iconUrl,
                        repository = repo,
                        apkUrl = it.apkUrl,
                    )
                }
        }
    }

    fun getMangaApkUrl(extension: MangaExtension.Available): String {
        return extension.apkUrl
            ?: "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.apkName}"
    }

    // ============================== Novel ===============================

    suspend fun findNovelExtensions(): List<NovelExtension.Available> = withIOContext {
        PrefManager.getVal<Set<String>>(PrefName.NovelExtensionRepos).flatMap { repo ->
            fetchRepoOrEmpty(repo).map {
                NovelExtension.Available(
                    it.name,
                    it.pkg,
                    it.apkName,
                    it.versionCode,
                    repo,
                    it.sources.map { s -> AvailableNovelSources(s.id, s.lang, s.name, s.baseUrl) },
                    it.iconUrl,
                    apkUrl = it.apkUrl,
                )
            }
        }
    }

    fun getNovelApkUrl(extension: NovelExtension.Available): String {
        return extension.apkUrl
            ?: "${extension.repository.removeSuffix("index.min.json")}/apk/${extension.pkgName}.apk"
    }

    // ============================== Fetching ===============================

    private suspend fun fetchRepoOrEmpty(repo: String): List<RepoEntry> {
        return try {
            fetchRepo(repo)
        } catch (e: Throwable) {
            Logger.log("Failed to get extensions from $repo")
            Logger.log(e)
            emptyList()
        }
    }

    private suspend fun fetchRepo(repo: String): List<RepoEntry> {
        val indexUrl = indexUrlFor(repo)
        val bytes = fetchBytesWithFallback(indexUrl)

        return when (bytes.firstOrNull()) {
            '['.code.toByte() -> {
                // A store that moved to the new format still serves a placeholder list here.
                val indexV2 = findIndexV2(indexUrl)
                if (indexV2 != null) {
                    try {
                        return parseStore(fetchBytesWithFallback(indexV2))
                    } catch (e: Throwable) {
                        Logger.log("Failed to read $indexV2, using $indexUrl")
                        Logger.log(e)
                    }
                }
                parseLegacy(bytes, repo)
            }
            else -> parseStore(bytes)
        }
    }

    /** Reads `index.pb` / `index.json` bytes, following `extensionListUrl` if the list lives elsewhere. */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun parseStore(bytes: ByteArray): List<RepoEntry> {
        val store = if (bytes.firstOrNull() == '{'.code.toByte()) {
            storeJson.decodeFromString<NetworkExtensionStore>(bytes.decodeToString())
        } else {
            protoBuf.decodeFromByteArray<NetworkExtensionStore>(bytes)
        }

        val list = store.extensionList ?: store.extensionListUrl?.let { url ->
            val listBytes = fetchBytesWithFallback(url)
            if (listBytes.firstOrNull() == '{'.code.toByte()) {
                storeJson.decodeFromString<NetworkExtensionStore.ExtensionList>(listBytes.decodeToString())
            } else {
                protoBuf.decodeFromByteArray<NetworkExtensionStore.ExtensionList>(listBytes)
            }
        }

        return list?.extensions.orEmpty().map { it.toRepoEntry() }
    }

    private fun NetworkExtensionStore.Extension.toRepoEntry(): RepoEntry {
        val langs = sources.map { it.language }.filter { it.isNotEmpty() }.toSet()
        return RepoEntry(
            name = name,
            pkg = packageName,
            apkName = resources.apkUrl.substringAfterLast('/'),
            apkUrl = resources.apkUrl,
            iconUrl = resources.iconUrl,
            lang = langs.singleOrNull() ?: "all",
            versionCode = versionCode,
            versionName = versionName,
            libVersion = extensionLib.toDoubleOrNull()
                ?: versionName.substringBeforeLast('.').toDoubleOrNull()
                ?: 0.0,
            isNsfw = contentWarning == NetworkExtensionStore.ContentWarning.MIXED ||
                contentWarning == NetworkExtensionStore.ContentWarning.NSFW,
            hasReadme = false,
            hasChangelog = false,
            sources = sources.map { RepoSource(it.id, it.language, it.name, it.homeUrl) },
        )
    }

    private fun parseLegacy(bytes: ByteArray, repo: String): List<RepoEntry> {
        val iconBase = repo.removeSuffix("/index.min.json")
        return storeJson.decodeFromString<List<ExtensionJsonObject>>(bytes.decodeToString())
            .mapNotNull {
                val libVersion = it.version.substringBeforeLast('.').toDoubleOrNull() ?: return@mapNotNull null
                RepoEntry(
                    name = it.name,
                    pkg = it.pkg,
                    apkName = it.apk,
                    apkUrl = null,
                    iconUrl = "$iconBase/icon/${it.pkg}.png",
                    lang = it.lang,
                    versionCode = it.code,
                    versionName = it.version,
                    libVersion = libVersion,
                    isNsfw = it.nsfw == 1,
                    hasReadme = it.hasReadme == 1,
                    hasChangelog = it.hasChangelog == 1,
                    sources = it.sources.orEmpty().map { s -> RepoSource(s.id, s.lang, s.name, s.baseUrl) },
                )
            }
    }

    /** The `index_v2` named by the `repo.json` next to a legacy `index.min.json`, if any. */
    private suspend fun findIndexV2(indexUrl: String): String? {
        if (!indexUrl.endsWith("/index.min.json")) return null
        val repoJsonUrl = indexUrl.removeSuffix("index.min.json") + "repo.json"
        return try {
            val bytes = fetchBytes(repoJsonUrl)
            storeJson.decodeFromString<LegacyRepoMeta>(bytes.decodeToString()).indexV2
                ?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchBytesWithFallback(url: String): ByteArray {
        return try {
            fetchBytes(url)
        } catch (e: Throwable) {
            Logger.log("Failed to get repo: $url")
            Logger.log(e)
            val fallback = jsDelivrUrl(url) ?: throw e
            fetchBytes(fallback)
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray {
        val bytes = networkService.client.newCall(GET(url)).awaitSuccess().use { it.body.bytes() }
        return bytes.gunzipIfNeeded()
    }

    private fun ByteArray.gunzipIfNeeded(): ByteArray {
        val isGzip = size >= 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()
        return if (isGzip) GZIPInputStream(inputStream()).use { it.readBytes() } else this
    }

    /** The URL of the store's index file, adding `index.min.json` to a bare repo URL. */
    private fun indexUrlFor(repo: String): String {
        val trimmed = repo.trim()
        val hasIndexFile = trimmed.endsWith(".json") || trimmed.endsWith(".pb") ||
            trimmed.contains("index.min.json")
        return if (hasIndexFile) trimmed else "${trimmed.removeSuffix("/")}/index.min.json"
    }

    /** The same GitHub file through jsDelivr, for when GitHub is blocked or rate limited. */
    private fun jsDelivrUrl(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val seg = httpUrl.pathSegments.filter { it.isNotEmpty() }
        val (owner, name, branch, path) = when (httpUrl.host) {
            // raw.githubusercontent.com/<owner>/<repo>/<branch>/<path>
            "raw.githubusercontent.com" -> {
                if (seg.size < 4) return null
                listOf(seg[0], seg[1], seg[2], seg.drop(3).joinToString("/"))
            }
            // github.com/<owner>/<repo>/raw/<branch>/<path>
            "github.com" -> {
                if (seg.size < 5 || seg[2] != "raw") return null
                listOf(seg[0], seg[1], seg[3], seg.drop(4).joinToString("/"))
            }
            else -> return null
        }
        return "https://gcore.jsdelivr.net/gh/$owner/$name@$branch/$path"
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)
