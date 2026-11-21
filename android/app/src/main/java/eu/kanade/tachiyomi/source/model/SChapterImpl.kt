package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var date_upload: Long = 0

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    // See SMangaImpl: transient because JsonObject isn't java.io.Serializable.
    @Transient
    private var _memo: JsonObject? = null

    override var memo: JsonObject
        get() = _memo ?: EMPTY_MEMO
        set(value) {
            _memo = value
        }

    companion object {
        // Pinned to the value before `memo` was added, so chapters saved with Java
        // serialization still read back.
        private const val serialVersionUID = 1717825616372766207L
    }
}
