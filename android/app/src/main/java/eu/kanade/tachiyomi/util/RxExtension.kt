package eu.kanade.tachiyomi.util

import rx.Observable
import eu.kanade.tachiyomi.util.lang.awaitSingle

suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()
