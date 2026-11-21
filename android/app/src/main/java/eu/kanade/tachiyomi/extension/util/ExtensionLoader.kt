package eu.kanade.tachiyomi.extension.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.arkhins.arkhime.connections.crashlytics.CrashlyticsInterface
import com.arkhins.arkhime.media.MediaType
import com.arkhins.arkhime.parsers.ForeignNovelInterface
import com.arkhins.arkhime.parsers.novel.NovelExtension
import com.arkhins.arkhime.parsers.novel.NovelLoadResult
import com.arkhins.arkhime.util.Logger
import dalvik.system.PathClassLoader
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.system.getApplicationIcon
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Locale

/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
internal object ExtensionLoader {

    private val preferences: SourcePreferences by injectLazy()
    private val loadNsfwSource by lazy {
        preferences.showNsfwSource().get()
    }

    private const val ANIME_PACKAGE = "tachiyomi.animeextension"
    private const val MANGA_PACKAGE = "tachiyomi.extension"

    private const val XX_METADATA_SOURCE_CLASS = ".class"
    private const val XX_METADATA_SOURCE_FACTORY = ".factory"
    private const val XX_METADATA_NSFW = "n.nsfw"
    private const val XX_METADATA_HAS_README = ".hasReadme"
    private const val XX_METADATA_HAS_CHANGELOG = ".hasChangelog"
    const val ANIME_LIB_VERSION_MIN = 12
    const val ANIME_LIB_VERSION_MAX = 15

    const val MANGA_LIB_VERSION_MIN = 1.2
    // 1.6 is the lib Keiyoushi builds against (Mihon 0.20+): suspend API, getMangaUpdate, memo.
    const val MANGA_LIB_VERSION_MAX = 1.6

    val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                PackageManager.GET_SIGNING_CERTIFICATES else 0)

    // ============================== All installed ==============================

    /**
     * Return a list of all the installed extensions initialized concurrently.
     *
     * @param context The application context.
     */
    fun loadAnimeExtensions(context: Context): List<AnimeLoadResult> =
        loadAll(context, MediaType.ANIME) { loadAnimeExtension(context, it.packageName, it) }

    fun loadMangaExtensions(context: Context): List<MangaLoadResult> =
        loadAll(context, MediaType.MANGA) { loadMangaExtension(context, it.packageName, it) }

    fun loadNovelExtensions(context: Context): List<NovelLoadResult> =
        loadAll(context, MediaType.NOVEL) { loadNovelExtension(context, it.packageName, it) }

    private fun <R> loadAll(context: Context, type: MediaType, load: (PackageInfo) -> R): List<R> {
        val pkgManager = context.packageManager
        val installedPkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }

        val extPkgs = installedPkgs.filter { isPackageAnExtension(type, it) }
        if (extPkgs.isEmpty()) return emptyList()

        // Load each extension concurrently and wait for completion
        return runBlocking {
            extPkgs.map { async { load(it) } }.map { it.await() }
        }
    }

    // ============================== One package ==============================

    /**
     * Attempts to load an extension from the given package name. It checks if the extension
     * contains the required feature flag before trying to load it.
     */
    fun loadAnimeExtensionFromPkgName(context: Context, pkgName: String): AnimeLoadResult =
        extensionPackage(context, pkgName, MediaType.ANIME).getOrNull()
            ?.let { loadAnimeExtension(context, pkgName, it) }
            ?: AnimeLoadResult.Error

    fun loadMangaExtensionFromPkgName(context: Context, pkgName: String): MangaLoadResult =
        extensionPackage(context, pkgName, MediaType.MANGA).getOrNull()
            ?.let { loadMangaExtension(context, pkgName, it) }
            ?: MangaLoadResult.Error

    fun loadNovelExtensionFromPkgName(context: Context, pkgName: String): NovelLoadResult =
        extensionPackage(context, pkgName, MediaType.NOVEL).fold(
            onSuccess = { loadNovelExtension(context, pkgName, it) },
            onFailure = { NovelLoadResult.Error(it as? Exception ?: Exception(it)) },
        )

    /** The package's info, or a failure if it's gone or isn't an extension of [type]. */
    private fun extensionPackage(context: Context, pkgName: String, type: MediaType): Result<PackageInfo> {
        val pkgInfo = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS)
        } catch (error: PackageManager.NameNotFoundException) {
            // Unlikely, but the package may have been uninstalled at this point
            Logger.log(error)
            return Result.failure(error)
        }
        if (!isPackageAnExtension(type, pkgInfo)) {
            val message = "Tried to load a package that wasn't a extension ($pkgName)"
            Logger.log(message)
            return Result.failure(Exception(message))
        }
        return Result.success(pkgInfo)
    }

    // ============================== Anime and manga ==============================

    private fun loadAnimeExtension(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo
    ): AnimeLoadResult {
        val pkg = loadSourcePackage(
            context, pkgName, pkgInfo,
            feature = ANIME_PACKAGE,
            labelPrefix = "Aniyomi: ",
            minLib = ANIME_LIB_VERSION_MIN.toDouble(),
            maxLib = ANIME_LIB_VERSION_MAX.toDouble(),
        ) { obj ->
            when (obj) {
                is AnimeSource -> listOf(obj)
                is AnimeSourceFactory -> obj.createSources()
                else -> null
            }
        } ?: return AnimeLoadResult.Error

        val sources = pkg.sources.map { it as AnimeSource }
        val extension = AnimeExtension.Installed(
            name = pkg.name,
            pkgName = pkgName,
            versionName = pkg.versionName,
            versionCode = pkg.versionCode,
            libVersion = pkg.libVersion,
            lang = langOf(sources.filterIsInstance<AnimeCatalogueSource>().map { it.lang }),
            isNsfw = pkg.isNsfw,
            hasReadme = pkg.hasReadme,
            hasChangelog = pkg.hasChangelog,
            sources = sources,
            pkgFactory = pkg.pkgFactory,
            isUnofficial = true,
            icon = context.getApplicationIcon(pkgName),
        )
        return AnimeLoadResult.Success(extension)
    }

    private fun loadMangaExtension(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo
    ): MangaLoadResult {
        val pkg = loadSourcePackage(
            context, pkgName, pkgInfo,
            feature = MANGA_PACKAGE,
            labelPrefix = "Tachiyomi: ",
            minLib = MANGA_LIB_VERSION_MIN,
            maxLib = MANGA_LIB_VERSION_MAX,
        ) { obj ->
            when (obj) {
                is MangaSource -> listOf(obj)
                is SourceFactory -> obj.createSources()
                else -> null
            }
        } ?: return MangaLoadResult.Error

        val sources = pkg.sources.map { it as MangaSource }
        val extension = MangaExtension.Installed(
            name = pkg.name,
            pkgName = pkgName,
            versionName = pkg.versionName,
            versionCode = pkg.versionCode,
            libVersion = pkg.libVersion,
            lang = langOf(sources.filterIsInstance<CatalogueSource>().map { it.lang }),
            isNsfw = pkg.isNsfw,
            hasReadme = pkg.hasReadme,
            hasChangelog = pkg.hasChangelog,
            sources = sources,
            pkgFactory = pkg.pkgFactory,
            isUnofficial = true,
            icon = context.getApplicationIcon(pkgName),
        )
        return MangaLoadResult.Success(extension)
    }

    /** What an anime or manga extension package declares, once it has passed the checks. */
    private class SourcePackage(
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val libVersion: Double,
        val isNsfw: Boolean,
        val hasReadme: Boolean,
        val hasChangelog: Boolean,
        val pkgFactory: String?,
        val sources: List<Any>,
    )

    /**
     * Reads an anime or manga extension package and instantiates its sources. Returns null, after
     * logging why, if the package is gone, has no version, is outside [minLib]..[maxLib], is NSFW
     * while NSFW sources are hidden, or a source class can't be created.
     *
     * @param feature the uses-feature name and metadata prefix, like "tachiyomi.extension".
     * @param labelPrefix the prefix stripped from the app label, like "Tachiyomi: ".
     * @param createSources turns an instantiated class into its sources, or null if it's neither
     *   a source nor a source factory.
     */
    private fun loadSourcePackage(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo,
        feature: String,
        labelPrefix: String,
        minLib: Double,
        maxLib: Double,
        createSources: (Any) -> List<Any>?,
    ): SourcePackage? {
        val pkgManager = context.packageManager

        val appInfo = try {
            pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        } catch (error: PackageManager.NameNotFoundException) {
            // Unlikely, but the package may have been uninstalled at this point
            Logger.log(error)
            return null
        }

        val extName = pkgManager.getApplicationLabel(appInfo).toString().substringAfter(labelPrefix)
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Logger.log("Missing versionName for extension $extName")
            return null
        }

        // Validate lib version
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
        if (libVersion == null || libVersion < minLib || libVersion > maxLib) {
            Logger.log(
                "Lib version is $libVersion, while only versions " +
                        "${minLib.trimmed()} to ${maxLib.trimmed()} are allowed"
            )
            return null
        }

        val isNsfw = appInfo.metaData.getInt("$feature$XX_METADATA_NSFW") == 1
        if (!loadNsfwSource && isNsfw) {
            Logger.log("NSFW extension $pkgName not allowed")
            return null
        }

        val hasReadme = appInfo.metaData.getInt("$feature$XX_METADATA_HAS_README", 0) == 1
        val hasChangelog = appInfo.metaData.getInt("$feature$XX_METADATA_HAS_CHANGELOG", 0) == 1

        val classLoader = try {
            PathClassLoader(appInfo.sourceDir, null, context.classLoader)
        } catch (e: Throwable) {
            Logger.log("Extension load error: $extName")
            Injekt.get<CrashlyticsInterface>().logException(e)
            return null
        }

        val sources = appInfo.metaData.getString("$feature$XX_METADATA_SOURCE_CLASS")!!
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    pkgInfo.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .flatMap {
                try {
                    val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()
                    createSources(obj) ?: throw Exception("Unknown source class type! ${obj.javaClass}")
                } catch (e: Throwable) {
                    Logger.log("Extension load error: $extName ($it)")
                    return null
                }
            }

        return SourcePackage(
            name = extName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            isNsfw = isNsfw,
            hasReadme = hasReadme,
            hasChangelog = hasChangelog,
            pkgFactory = appInfo.metaData.getString("$feature$XX_METADATA_SOURCE_FACTORY"),
            sources = sources,
        )
    }

    /** "12" for 12.0, so the log reads as it did when the anime limits were Ints. */
    private fun Double.trimmed(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun langOf(langs: List<String>): String {
        val distinct = langs.toSet()
        return when (distinct.size) {
            0 -> ""
            1 -> distinct.first()
            else -> "all"
        }
    }

    // ============================== Novel ==============================

    private fun loadNovelExtension(
        context: Context,
        pkgName: String,
        pkgInfo: PackageInfo
    ): NovelLoadResult {
        val pkgManager = context.packageManager

        val appInfo = try {
            pkgManager.getApplicationInfo(pkgName, PackageManager.GET_META_DATA)
        } catch (error: PackageManager.NameNotFoundException) {
            // Unlikely, but the package may have been uninstalled at this point
            Logger.log(error)
            return NovelLoadResult.Error(error)
        }

        val extName =
            pkgManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)

        if (versionName.isNullOrEmpty()) {
            Logger.log("Missing versionName for extension $extName")
            return NovelLoadResult.Error(Exception("Missing versionName for extension $extName"))
        }

        val classLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
        val novelInterfaceInstance = try {
            val className = appInfo.loadLabel(context.packageManager).toString()
            // Older repos name the class some.random.novelextensions.<label>.<Label>;
            // Dantotsu-Novels puts it at <package>.<Label>.
            val candidates = listOf(
                "some.random.novelextensions.${className.lowercase(Locale.getDefault())}.$className",
                "$pkgName.$className",
            )
            val loadedClass = candidates.firstNotNullOfOrNull {
                try {
                    classLoader.loadClass(it)
                } catch (_: ClassNotFoundException) {
                    null
                }
            } ?: throw ClassNotFoundException("No novel class in $pkgName (tried $candidates)")
            val instance = loadedClass.getDeclaredConstructor().newInstance()
            // These extensions bundle their own copy of the parser types, so they are wrapped
            // rather than cast.
            ForeignNovelInterface.wrap(instance)
        } catch (e: Throwable) {
            Logger.log("Extension load error: $extName")
            Logger.log(e)
            return NovelLoadResult.Error(e as? Exception ?: Exception(e))
        }

        val extension = NovelExtension.Installed(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            sources = listOfNotNull(novelInterfaceInstance),
            isUnofficial = true,
            icon = context.getApplicationIcon(pkgName),
        )
        return NovelLoadResult.Success(extension)
    }

    /**
     * Returns true if the given package is an extension.
     *
     * @param pkgInfo The package info of the application.
     */
    private fun isPackageAnExtension(type: MediaType, pkgInfo: PackageInfo): Boolean {

        return if (type == MediaType.NOVEL) {
            pkgInfo.packageName.startsWith("some.random") ||
                pkgInfo.packageName.startsWith("ani.dantotsu.extension.novel.")
        } else {
            pkgInfo.reqFeatures.orEmpty().any {
                it.name == when (type) {
                    MediaType.ANIME -> ANIME_PACKAGE
                    MediaType.MANGA -> MANGA_PACKAGE
                    else -> ""
                }
            }
        }
    }
}
