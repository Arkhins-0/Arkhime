package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject

class SMangaImpl : SManga {

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    override var initialized: Boolean = false

    // JsonObject isn't java.io.Serializable, and a deserialized object skips initializers,
    // so the field is transient and nullable behind a non-null property.
    @Transient
    private var _memo: JsonObject? = null

    override var memo: JsonObject
        get() = _memo ?: EMPTY_MEMO
        set(value) {
            _memo = value
        }

    companion object {
        // Pinned to the value before `memo` was added, so manga saved with Java
        // serialization still reads back.
        private const val serialVersionUID = -5322048558613655698L
    }
}

internal val EMPTY_MEMO = JsonObject(emptyMap())
