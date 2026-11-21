package eu.kanade.tachiyomi.source.model

/**
 * What [eu.kanade.tachiyomi.source.MangaSource.getMangaUpdate] returns: the manga's details and
 * its chapters, either of which may be the values passed in unchanged.
 *
 * @since tachiyomix 1.6
 */
class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
