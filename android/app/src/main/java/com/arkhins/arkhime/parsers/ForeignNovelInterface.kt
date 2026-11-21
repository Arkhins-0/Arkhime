package com.arkhins.arkhime.parsers

import com.arkhins.arkhime.FileUrl
import com.lagradost.nicehttp.Requests
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Runs a Dantotsu novel extension through Arkhime's [NovelInterface].
 *
 * Those extensions bundle their own copy of `ani.dantotsu.parsers` (NovelInterface,
 * ShowResponse, Book) and `ani.dantotsu.FileUrl`, so their objects aren't Arkhime's types and
 * can't be cast. This calls `search` and `loadBook` by reflection and copies the results into
 * Arkhime's classes through their getters.
 *
 * `Requests` (nicehttp) and the Kotlin runtime come from the app's class loader, so those
 * arguments pass through unchanged.
 */
class ForeignNovelInterface private constructor(
    private val target: Any,
    private val searchMethod: Method,
    private val loadBookMethod: Method,
) : NovelInterface {

    override suspend fun search(query: String, client: Requests): List<ShowResponse> {
        val result = callSuspend(searchMethod, query, client) as? List<*> ?: return emptyList()
        return result.mapNotNull { it?.toShowResponse() }
    }

    override suspend fun loadBook(
        link: String,
        extra: Map<String, String>?,
        client: Requests,
    ): Book {
        val book = callSuspend(loadBookMethod, link, extra, client)
            ?: throw IllegalStateException("Novel extension returned no book for $link")
        return Book(
            name = book.get<String>("getName").orEmpty(),
            img = book.get<Any>("getImg")?.toFileUrl() ?: FileUrl(""),
            description = book.get<String>("getDescription"),
            links = book.get<List<*>>("getLinks").orEmpty().mapNotNull { it?.toFileUrl() },
        )
    }

    private suspend fun callSuspend(method: Method, vararg args: Any?): Any? =
        suspendCoroutineUninterceptedOrReturn { cont: Continuation<Any?> ->
            try {
                method.invoke(target, *args, cont)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
        }

    private fun Any.toShowResponse(): ShowResponse {
        @Suppress("UNCHECKED_CAST")
        val extra = get<Map<*, *>>("getExtra")
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()
            ?.toMutableMap()
        return ShowResponse(
            name = get<String>("getName").orEmpty(),
            link = get<String>("getLink").orEmpty(),
            coverUrl = get<Any>("getCoverUrl")?.toFileUrl() ?: FileUrl(""),
            otherNames = get<List<*>>("getOtherNames").orEmpty().filterIsInstance<String>(),
            total = get<Int>("getTotal"),
            extra = extra,
        )
    }

    /** Copies the extension's own `ani.dantotsu.FileUrl` (or a plain string) into Arkhime's. */
    private fun Any.toFileUrl(): FileUrl {
        if (this is FileUrl) return this
        if (this is String) return FileUrl(this)
        val headers = get<Map<*, *>>("getHeaders")
            ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            ?.toMap()
            .orEmpty()
        return FileUrl(get<String>("getUrl").orEmpty(), headers)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.get(getter: String): T? =
        try {
            javaClass.getMethod(getter).invoke(this) as? T
        } catch (_: ReflectiveOperationException) {
            null
        }

    companion object {
        /**
         * Wraps [instance] if it has the novel extension's `search` and `loadBook` methods,
         * or returns null.
         */
        fun wrap(instance: Any): NovelInterface? {
            if (instance is NovelInterface) return instance
            val methods = instance.javaClass.methods
            val search = methods.firstOrNull { it.name == "search" && it.parameterTypes.size == 3 }
            val loadBook = methods.firstOrNull { it.name == "loadBook" && it.parameterTypes.size == 4 }
            if (search == null || loadBook == null) return null
            return ForeignNovelInterface(instance, search, loadBook)
        }
    }
}
