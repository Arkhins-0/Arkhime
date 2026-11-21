package com.arkhins.arkhime.download.manga

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.arkhins.arkhime.download.DownloadedType
import com.arkhins.arkhime.download.DownloadsManager
import com.arkhins.arkhime.download.DownloadsManager.Companion.getSubDirectory
import com.arkhins.arkhime.media.Media
import com.arkhins.arkhime.media.MediaType
import com.arkhins.arkhime.util.Logger
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SChapterImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Imports CBZ/ZIP files into a manga's downloads, so they read like downloaded chapters.
 *
 * A file may hold one chapter or a whole volume. A volume is split into chapters by, in order:
 * 1. one folder per chapter inside the archive,
 * 2. a chapter number in the image names (`c012`, `Ch.12`, `Chapter 12`, as scanlation volume
 *    packs name them),
 * 3. otherwise the whole file is one entry, named from the file (`Chapter 12` or `Volume 3`).
 *
 * Pages are written as `<downloads>/<title>/<chapter>/<n>.jpg`, the layout the Downloaded source
 * reads, and each chapter is registered with [DownloadsManager].
 */
object CbzImporter {

    const val SCANLATOR = "Imported"

    class Progress(
        val fileName: String,
        val fileIndex: Int,
        val fileCount: Int,
        val chapter: String?,
        val page: Int,
        val pages: Int,
    )

    /** [importedFiles] are the picked files that imported fully, which the user may then delete. */
    class Result(val chapters: List<String>, val failures: List<String>, val importedFiles: List<Uri>)

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "jxl")

    // "c012", "ch.12", "Chapter 12.5", "#12" after a separator; not the "c" in "cover".
    private val CHAPTER_REGEX =
        Regex("""(?i)(?:^|[^a-z0-9])(?:chapter|chap|ch|c)[ ._#-]*(\d{1,4}(?:\.\d{1,2})?)(?![\d])""")
    private val VOLUME_REGEX =
        Regex("""(?i)(?:^|[^a-z0-9])(?:volume|vol|v)[ ._#-]*(\d{1,4}(?:\.\d{1,2})?)(?![\d])""")

    suspend fun import(
        context: Context,
        media: Media,
        downloadsManager: DownloadsManager,
        uris: List<Uri>,
        onProgress: suspend (Progress) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val title = media.mainName()
        val imported = linkedSetOf<String>()
        val failures = mutableListOf<String>()
        val importedFiles = mutableListOf<Uri>()
        // Chapter folders created by this import, and how many pages each already has, so a
        // chapter split across two files is joined rather than overwritten.
        val folders = mutableMapOf<String, DocumentFile>()
        val pageCounts = mutableMapOf<String, Int>()

        uris.forEachIndexed { index, uri ->
            val fileName = displayName(context, uri)
            try {
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext == "cbr" || ext == "rar" || ext == "cb7" || ext == "7z") {
                    throw IllegalArgumentException("$ext files aren't supported yet, only CBZ and ZIP")
                }
                val charset = pickCharset(context, uri)
                val plan = planChapters(fileName, listImages(context, uri, charset))
                if (plan.isEmpty()) throw IllegalArgumentException("no images found")

                // entry name -> (chapter, page number within this file's part of the chapter)
                val pageOf = HashMap<String, Pair<String, Int>>()
                plan.forEach { (chapter, entries) ->
                    entries.forEachIndexed { i, entry -> pageOf[entry] = chapter to i }
                }
                val startPage = plan.keys.associateWith { pageCounts[it] ?: 0 }
                val total = pageOf.size
                var done = 0

                ZipInputStream(context.contentResolver.openInputStream(uri), charset).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val (chapter, i) = pageOf[entry.name] ?: continue
                        val folder = folders.getOrPut(chapter) {
                            getSubDirectory(context, MediaType.MANGA, true, title, chapter)
                                ?: throw IllegalStateException("can't open the downloads folder")
                        }
                        val page = startPage.getValue(chapter) + i + 1
                        val file = folder.createFile("image/jpeg", "$page.jpg")
                            ?: throw IllegalStateException("can't create page $page of $chapter")
                        context.contentResolver.openOutputStream(file.uri)?.use { out ->
                            zip.copyTo(out)
                        } ?: throw IllegalStateException("can't write page $page of $chapter")
                        done++
                        onProgress(Progress(fileName, index, uris.size, chapter, done, total))
                    }
                }

                plan.forEach { (chapter, entries) ->
                    pageCounts[chapter] = (pageCounts[chapter] ?: 0) + entries.size
                    imported += chapter
                }
                importedFiles += uri
            } catch (e: Exception) {
                Logger.log("CBZ import failed for $fileName: $e")
                failures += "$fileName: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        imported.forEach { chapter ->
            val exists = downloadsManager.mangaDownloadedTypes.any {
                it.titleName == DownloadedType(title, chapter, MediaType.MANGA).titleName &&
                    it.chapterName == DownloadedType(title, chapter, MediaType.MANGA).chapterName
            }
            if (!exists) {
                downloadsManager.addDownload(
                    DownloadedType(title, chapter, MediaType.MANGA, scanlator = SCANLATOR)
                )
            }
        }
        if (imported.isNotEmpty()) saveMediaInfo(context, media, title)

        Result(imported.toList(), failures, importedFiles)
    }

    /**
     * Groups the archive's images into chapters, in reading order.
     */
    internal fun planChapters(fileName: String, images: List<String>): LinkedHashMap<String, List<String>> {
        val plan = LinkedHashMap<String, List<String>>()
        if (images.isEmpty()) return plan
        val sorted = images.sortedWith(NaturalOrder)

        // Drop folders every image sits in, like "Komi Vol 1/".
        var paths = sorted.map { it.split('/').filter(String::isNotEmpty) }
        while (paths.all { it.size > 1 } && paths.map { it.first() }.distinct().size == 1) {
            paths = paths.map { it.drop(1) }
        }
        val byPath = sorted.zip(paths)

        // 1. A folder per chapter.
        val folderGroups = byPath.filter { it.second.size > 1 }.groupBy { it.second.first() }
        if (folderGroups.size >= 2) {
            val loose = byPath.filter { it.second.size == 1 }.map { it.first }
            folderGroups.keys.sortedWith(NaturalOrder).forEachIndexed { i, folder ->
                val name = chapterLabel(folder) ?: folder
                val pages = folderGroups.getValue(folder).map { it.first }
                // Loose images (a cover, credits) go with the first chapter.
                plan.merge(name, if (i == 0) loose + pages else pages) { a, b -> a + b }
            }
            return plan
        }

        // 2. Chapter numbers in the image names.
        val numbered = byPath.map { (entry, path) -> entry to chapterLabel(path.last()) }
        val labels = numbered.mapNotNull { it.second }
        if (labels.distinct().size >= 2 && labels.size >= numbered.size * 0.6) {
            var current: String? = null
            val leading = mutableListOf<String>()
            numbered.forEach { (entry, label) ->
                if (label != null) current = label
                val chapter = current
                if (chapter == null) {
                    leading += entry // pages before the first numbered one, like the cover
                } else {
                    plan.merge(chapter, listOf(entry)) { a, b -> a + b }
                }
            }
            if (leading.isNotEmpty()) {
                val first = plan.keys.first()
                val rest = LinkedHashMap(plan)
                plan.clear()
                rest.forEach { (k, v) -> plan[k] = if (k == first) leading + v else v }
            }
            return plan
        }

        // 3. The whole file is one entry.
        val base = fileName.substringBeforeLast('.')
        val name = chapterLabel(base) ?: volumeLabel(base) ?: base.trim().ifEmpty { "Imported" }
        plan[name] = sorted
        return plan
    }

    internal fun chapterLabel(text: String): String? =
        CHAPTER_REGEX.find(text)?.groupValues?.get(1)?.let { "Chapter ${normalise(it)}" }

    internal fun volumeLabel(text: String): String? =
        VOLUME_REGEX.find(text)?.groupValues?.get(1)?.let { "Volume ${normalise(it)}" }

    private fun normalise(number: String): String =
        BigDecimal(number).stripTrailingZeros().toPlainString()

    private fun isImage(name: String): Boolean {
        val leaf = name.substringAfterLast('/')
        if (leaf.startsWith(".") || name.contains("__MACOSX/")) return false
        return leaf.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }

    private fun listImages(context: Context, uri: Uri, charset: Charset): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(context.contentResolver.openInputStream(uri), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && isImage(entry.name)) names += entry.name
            }
        }
        return names
    }

    /** UTF-8, or CP437 for archives made by tools that don't flag their names as UTF-8. */
    private fun pickCharset(context: Context, uri: Uri): Charset {
        return try {
            listImages(context, uri, Charsets.UTF_8)
            Charsets.UTF_8
        } catch (_: IllegalArgumentException) {
            Charset.forName("CP437")
        }
    }

    /** Deletes the picked files; returns how many couldn't be deleted. */
    fun deleteOriginals(context: Context, uris: List<Uri>): Int = uris.count { uri ->
        try {
            !DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Logger.log("CBZ import: couldn't delete $uri: $e")
            true
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file.cbz"
    }

    /** Writes media.json for the offline library, like a normal download, if it isn't there yet. */
    private fun saveMediaInfo(context: Context, media: Media, title: String) {
        try {
            val directory = getSubDirectory(context, MediaType.MANGA, false, title) ?: return
            if (directory.findFile("media.json") != null) return
            val file = directory.createFile("application/json", "media.json") ?: return
            val gson = GsonBuilder()
                .registerTypeAdapter(SChapter::class.java, InstanceCreator<SChapter> { SChapterImpl() })
                .create()
            context.contentResolver.openOutputStream(file.uri)?.use {
                it.write(gson.toJson(media).toByteArray())
            }
        } catch (e: Exception) {
            Logger.log("CBZ import: couldn't save media.json: $e")
        }
    }

    /** Orders "2.jpg" before "10.jpg", comparing runs of digits as numbers. */
    private object NaturalOrder : Comparator<String> {
        private val chunk = Regex("""\d+|\D+""")
        override fun compare(a: String, b: String): Int {
            val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
            val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(x.size, y.size)) {
                val p = x[i]
                val q = y[i]
                val c = if (p[0].isDigit() && q[0].isDigit()) {
                    p.toBigInteger().compareTo(q.toBigInteger())
                } else {
                    p.compareTo(q)
                }
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }
}
