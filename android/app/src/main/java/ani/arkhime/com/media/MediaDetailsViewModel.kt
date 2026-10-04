package ani.arkhime.com.media

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.arkhime.com.R
import ani.arkhime.com.client
import ani.arkhime.com.connections.anilist.Anilist
import ani.arkhime.com.connections.mal.MAL
import ani.arkhime.com.currContext
import ani.arkhime.com.media.mangaupdates.MangaAnimeUtil
import ani.arkhime.com.others.AniSkip
import ani.arkhime.com.others.AniskipCsvFallback
import ani.arkhime.com.others.IdMappers
import ani.arkhime.com.others.IntroDbService
import ani.arkhime.com.others.Jikan
import ani.arkhime.com.others.TmdbService
import ani.arkhime.com.settings.saving.PrefManager
import ani.arkhime.com.settings.saving.PrefName
import ani.arkhime.com.snackString
import ani.arkhime.com.tryWithSuspend
import ani.arkhime.com.util.Logger
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

class MediaDetailsViewModel : ViewModel() {
    val scrolledToTop = MutableLiveData(true)

    fun saveSelected(id: Int, data: Selected) {
        PrefManager.setCustomVal("Selected-$id", data)
    }


    fun loadSelected(media: Media): Selected {
        return PrefManager.getNullableCustomVal("Selected-${media.id}", null, Selected::class.java)
            ?: Selected().also { saveSelected(media.id, it) }
    }

    var continueMedia: Boolean? = null
    var loading = false

    private val media: MutableLiveData<Media> = MutableLiveData<Media>(null)
    fun getMedia(): LiveData<Media> = media
    fun loadMedia(m: Media) {
        if (!loading) {
            loading = true
            val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (m.id == 0 && m.format?.startsWith("LOCAL") == true) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)

                    val mapKeyStr = m.folderName ?: m.name
                    var mappedId = PrefManager.getCustomVal<Int>("local_mapping_$mapKeyStr", 0)
                    if (mappedId == 0) {
                        try {
                            val searchType = if (m.manga != null) "MANGA" else "ANIME"
                            val searchFormat = if (m.format == "LOCAL_NOVEL") "NOVEL" else null
                            var results = Anilist.query.searchAniManga(searchType, search = m.name, format = searchFormat)
                            if (results == null || results.results.isEmpty()) {
                                if (m.folderName != null && m.folderName != m.name) {
                                    results = Anilist.query.searchAniManga(searchType, search = m.folderName!!, format = searchFormat)
                                }
                            }
                            if (results != null && results.results.isNotEmpty()) {
                                mappedId = results.results[0].id
                                PrefManager.setCustomVal("local_mapping_$mapKeyStr", mappedId)
                            }
                        } catch (e: Exception) {
                            ani.arkhime.com.util.Logger.log(e)
                        }
                    }

                    if (mappedId != 0) {
                        val newMedia = m.copy(id = mappedId)
                        val fetchedMedia = Anilist.query.mediaDetails(newMedia)
                        fetchedMedia?.format = m.format 
                        
                        // Cache
                        fetchedMedia?.cover?.let { ani.arkhime.com.settings.saving.PrefManager.setCustomVal("local_cover_$mapKeyStr", it) }
                        fetchedMedia?.banner?.let { ani.arkhime.com.settings.saving.PrefManager.setCustomVal("local_banner_$mapKeyStr", it) }

                        fetchedMedia?.folderName = m.folderName ?: m.name
                        fetchedMedia?.selected = m.selected
                        media.postValue(fetchedMedia)
                    }
                } else if (m.id == 0) {
                    m.folderName = m.folderName ?: m.name
                    media.postValue(m)
                } else if (rescueMode && m.idMAL != null) {
                    tryWithSuspend {
                        val isAnime = m.anime != null
                        val malId = m.idMAL!!
                        val malNode = if (isAnime)
                            MAL.query.getAnimeDetails(malId)
                        else
                            MAL.query.getMangaDetails(malId)
                        if (malNode != null) {
                            val detailed = Media(malNode, isAnime)
                            detailed.userProgress = m.userProgress ?: detailed.userProgress
                            detailed.userStatus = m.userStatus ?: detailed.userStatus
                            detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                            detailed.isListPrivate = m.isListPrivate
                            detailed.userListId = m.userListId
                            detailed.userRepeat = m.userRepeat
                            detailed.userUpdatedAt = m.userUpdatedAt ?: detailed.userUpdatedAt
                            detailed.userCompletedAt = m.userCompletedAt
                            detailed.userStartedAt = m.userStartedAt
                            detailed.cameFromContinue = m.cameFromContinue
                            detailed.selected = m.selected
                            detailed.isFav = m.isFav
                            detailed.shareLink = "https://myanimelist.net/${if (isAnime) "anime" else "manga"}/$malId"
                            enrichRescueModeDetails(detailed)
                            media.postValue(detailed)
                            launchBackgroundEnrichment(detailed)
                        } else {
                            val jikanData = if (isAnime)
                                MAL.jikan.getAnimeById(malId)
                            else
                                MAL.jikan.getMangaById(malId)
                            if (jikanData != null) {
                                val detailed = Media(jikanData, isAnime)
                                detailed.userProgress = m.userProgress ?: detailed.userProgress
                                detailed.userStatus = m.userStatus ?: detailed.userStatus
                                detailed.userScore = if (m.userScore != 0) m.userScore else detailed.userScore
                                detailed.isListPrivate = m.isListPrivate
                                detailed.userListId = m.userListId
                                detailed.userRepeat = m.userRepeat
                                detailed.userUpdatedAt = m.userUpdatedAt
                                detailed.userCompletedAt = m.userCompletedAt
                                detailed.userStartedAt = m.userStartedAt
                                detailed.cameFromContinue = m.cameFromContinue
                                detailed.selected = m.selected
                                detailed.isFav = m.isFav
                                detailed.shareLink = "https://myanimelist.net/${if (isAnime) "anime" else "manga"}/$malId"
                                enrichRescueModeDetails(detailed)
                                media.postValue(detailed)
                                launchBackgroundEnrichment(detailed)
                            } else {
                                media.postValue(m)
                            }
                        }
                    }
                } else if (rescueMode) {
                    media.postValue(m)
                } else {
                    media.postValue(Anilist.query.mediaDetails(m))
                }
                loading = false
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (m.idIMDB == null) {
                    m.idIMDB = if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                        m.idMAL?.let { ani.arkhime.com.others.IdMappers.getImdbIdFromMal(it) }
                    } else {
                        ani.arkhime.com.others.IdMappers.getImdbId(m.id)
                    }
                }
            } catch (e: Exception) {
                ani.arkhime.com.util.Logger.log(e)
            }
        }
    }

    private suspend fun enrichRescueModeDetails(media: Media) {
        val malId = media.idMAL ?: return
        supervisorScope {
            val isAnime = media.anime != null
            val fullDeferred = async {
                if (isAnime) MAL.jikan.getAnimeById(malId) else MAL.jikan.getMangaById(malId)
            }
            val charactersDeferred = async {
                if (isAnime) MAL.jikan.getAnimeCharacters(malId) else MAL.jikan.getMangaCharacters(malId)
            }
            val staffDeferred = async {
                if (isAnime) MAL.jikan.getAnimeStaff(malId) else emptyList()
            }
            val reviewsDeferred = async {
                if (isAnime) MAL.jikan.getAnimeReviews(malId) else MAL.jikan.getMangaReviews(malId)
            }
            val recommendationsDeferred = async {
                MAL.jikan.getRecommendations(isAnime, malId)
            }

            val fullData = fullDeferred.await()
            if (fullData != null) {
                val fullMapped = Media(fullData, isAnime)
                if (media.description.isNullOrBlank() && !fullMapped.description.isNullOrBlank()) {
                    media.description = fullMapped.description
                }
                if (media.synonyms.isEmpty() && fullMapped.synonyms.isNotEmpty()) media.synonyms = fullMapped.synonyms
                if (media.genres.isEmpty() && fullMapped.genres.isNotEmpty()) media.genres = fullMapped.genres
                if (media.externalLinks.isNullOrEmpty() && !fullMapped.externalLinks.isNullOrEmpty()) media.externalLinks = fullMapped.externalLinks
                if ((media.meanScore == null || media.meanScore == 0) && fullMapped.meanScore != null) {
                    media.meanScore = fullMapped.meanScore
                }
                if (media.source.isNullOrBlank() && !fullMapped.source.isNullOrBlank()) {
                    media.source = fullMapped.source
                }
                if (!fullMapped.relations.isNullOrEmpty()) {
                    if (media.relations.isNullOrEmpty() || (fullMapped.relations?.size ?: 0) > (media.relations?.size ?: 0)) {
                        media.relations = fullMapped.relations
                    }
                    if (media.prequel == null) media.prequel = fullMapped.prequel
                    if (media.sequel == null) media.sequel = fullMapped.sequel
                }
                if (!fullMapped.staff.isNullOrEmpty()) {
                    media.staff = ArrayList(
                        ((media.staff ?: arrayListOf()) + fullMapped.staff!!).distinctBy { it.id }
                    )
                }
                if (!fullMapped.recommendations.isNullOrEmpty() &&
                    (fullMapped.recommendations?.size ?: 0) > (media.recommendations?.size ?: 0)) {
                    media.recommendations = fullMapped.recommendations
                }
                if (media.trailer.isNullOrBlank() && !fullMapped.trailer.isNullOrBlank()) media.trailer = fullMapped.trailer
                if (isAnime) {
                    fullMapped.anime?.let { anime ->
                        if (anime.op.isNotEmpty()) media.anime?.op = anime.op
                        if (anime.ed.isNotEmpty()) media.anime?.ed = anime.ed
                        if (media.anime?.mainStudio == null) {
                            anime.mainStudio?.let { media.anime?.mainStudio = it }
                        }
                        if (media.anime?.producers.isNullOrEmpty() && !anime.producers.isNullOrEmpty()) {
                            media.anime?.producers = anime.producers
                        }
                        if (media.anime?.season.isNullOrBlank()) anime.season?.let { media.anime?.season = it }
                        if (media.anime?.seasonYear == null) anime.seasonYear?.let { media.anime?.seasonYear = it }
                        if (media.anime?.nextAiringEpisodeTime == null && anime.nextAiringEpisodeTime != null) {
                            media.anime?.nextAiringEpisodeTime = anime.nextAiringEpisodeTime
                        }
                        val estimated = anime.nextAiringEpisode
                        val watched = media.userProgress ?: 0
                        val nextAiring = if (estimated != null) {
                            if (watched > 0 && watched >= (estimated + 1)) watched else estimated
                        } else {
                            if (watched > 0) watched else null
                        }
                        if (nextAiring != null) {
                            media.anime?.nextAiringEpisode = nextAiring
                        }
                    }
                } else {
                    fullMapped.manga?.author?.let { media.manga?.author = it }
                }
            }

            val jRecommendations = try { recommendationsDeferred.await() } catch (_: Exception) { null }
            val mappedRecommendations = jRecommendations
                ?.mapNotNull { it.entry }
                ?.map {
                    Media(
                        id = it.malId,
                        idMAL = it.malId,
                        name = it.title,
                        nameRomaji = it.title ?: "",
                        userPreferredName = it.title ?: "",
                        cover = it.images?.jpg?.largeImageUrl ?: it.images?.jpg?.imageUrl,
                        banner = it.images?.jpg?.largeImageUrl,
                        isAdult = false,
                        status = null,
                        meanScore = null,
                        popularity = null,
                        format = null,
                    )
                }
                ?.distinctBy { it.id }
                ?.let { ArrayList(it) }
            
            if (!mappedRecommendations.isNullOrEmpty()) {
                if (media.recommendations.isNullOrEmpty() || mappedRecommendations.size > (media.recommendations?.size ?: 0)) {
                    media.recommendations = mappedRecommendations
                }
            }

            val mappedCharacters = charactersDeferred.await()
                .mapNotNull { jChar ->
                    val character = jChar.character ?: return@mapNotNull null
                    Character(
                        id = character.malId,
                        name = character.name,
                        image = character.images?.jpg?.largeImageUrl ?: character.images?.jpg?.imageUrl,
                        banner = media.banner ?: media.cover,
                        role = jChar.role ?: "",
                        isFav = false,
                        voiceActor = jChar.voiceActors
                            ?.mapNotNull { va ->
                                va.person?.let { person ->
                                    Author(
                                        id = person.malId,
                                        name = person.name,
                                        image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                                        role = va.language
                                    )
                                }
                            }
                            ?.let { ArrayList(it) }
                    )
                }
            if (mappedCharacters.isNotEmpty()) {
                media.characters = ArrayList(mappedCharacters.distinctBy { it.id })
            }

            val mappedStaff = staffDeferred.await()
                .mapNotNull { staff ->
                    val person = staff.person ?: return@mapNotNull null
                    Author(
                        id = person.malId,
                        name = person.name,
                        image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                        role = staff.positions?.joinToString(", ")
                    )
                }

            val mangaAuthors = if (!isAnime && fullData != null) {
                fullData.authors?.mapNotNull { author ->
                    val person = author.person ?: return@mapNotNull null
                    Author(
                        id = person.malId,
                        name = person.name,
                        image = person.images?.jpg?.largeImageUrl ?: person.images?.jpg?.imageUrl,
                        role = author.position
                    )
                } ?: emptyList()
            } else emptyList()

            val allStaff = (mappedStaff + mangaAuthors).distinctBy { it.id }
            if (allStaff.isNotEmpty()) {
                media.staff = ArrayList(
                    ((media.staff ?: arrayListOf()) + allStaff).distinctBy { it.id }
                )
            }

            mapJikanReviews(media, reviewsDeferred.await(), isAnime, malId)
        }
    }

    private fun launchBackgroundEnrichment(media: Media) {
        val malId = media.idMAL ?: return
        val isAnime = media.anime != null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchRelationCovers(media, isAnime)

                enrichRecommendationDetails(media, isAnime)

                this@MediaDetailsViewModel.media.postValue(media)
            } catch (_: Exception) {}
        }
    }

    private suspend fun enrichRecommendationDetails(media: Media, isAnime: Boolean) {
        val recs = media.recommendations?.take(15) ?: return
        val recsToEnrich = recs.filter { rec ->
            rec.meanScore == null || rec.meanScore == 0 ||
            (rec.anime != null && rec.anime?.totalEpisodes == null) ||
            (rec.manga != null && rec.manga?.totalChapters == null)
        }
        if (recsToEnrich.isEmpty()) return
        kotlinx.coroutines.supervisorScope {
            val deferreds = recsToEnrich.map { rec ->
                async {
                    try {
                        val recMalId = rec.idMAL ?: return@async
                        val isRecAnime = rec.anime != null
                        
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?
                        val chaptersCount: Int?

                        val node = if (isRecAnime) MAL.query.getAnimeDetails(recMalId) else MAL.query.getMangaDetails(recMalId)
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                            chaptersCount = node.numChapters
                        } else {
                            val jikanNode = if (isRecAnime) MAL.jikan.getAnimeById(recMalId) else MAL.jikan.getMangaById(recMalId)
                            if (jikanNode != null) {
                                coverUrl = jikanNode.images?.jpg?.largeImageUrl ?: jikanNode.images?.jpg?.imageUrl
                                score = ((jikanNode.score ?: 0f) * 10f).toInt()
                                statusStr = jikanNode.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                                episodesCount = jikanNode.episodes
                                chaptersCount = jikanNode.chapters
                            } else {
                                coverUrl = null
                                score = null
                                statusStr = null
                                episodesCount = null
                                chaptersCount = null
                            }
                        }

                        if (coverUrl != null || score != null || statusStr != null) {
                            if (coverUrl != null) {
                                rec.cover = rec.cover ?: coverUrl
                            }
                            if (score != null) {
                                rec.meanScore = score
                            }
                            if (statusStr != null) {
                                rec.status = statusStr
                            }
                            if (isRecAnime) {
                                if (episodesCount != null) {
                                    rec.anime?.totalEpisodes = episodesCount
                                }
                            } else {
                                if (chaptersCount != null) {
                                    rec.manga?.totalChapters = chaptersCount
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
        media.recommendations = ArrayList(recs)
    }


    private fun mapJikanReviews(
        media: Media,
        jikanReviews: List<ani.arkhime.com.connections.mal.JikanReview>,
        isAnime: Boolean,
        malId: Int
    ) {
        if (jikanReviews.isEmpty()) return
        val mapped = jikanReviews.mapNotNull { review ->
            val reviewText = review.review ?: return@mapNotNull null
            val summary = if (reviewText.length > 200) reviewText.take(200) + "…" else reviewText
            val userName = review.user?.username ?: "Anonymous"
            val userAvatar = review.user?.images?.jpg?.imageUrl
            val createdAt = try {
                java.time.Instant.parse(review.date ?: "").epochSecond.toInt()
            } catch (_: Exception) { 0 }
            ani.arkhime.com.connections.anilist.api.Query.Review(
                id = review.malId,
                mediaId = malId,
                mediaType = if (isAnime) "ANIME" else "MANGA",
                summary = summary,
                body = reviewText,
                rating = review.reactions?.overall ?: 0,
                ratingAmount = review.reactions?.overall ?: 0,
                userRating = "NO_VOTE",
                score = (review.score ?: 0) * 10,
                private = false,
                siteUrl = review.url ?: "https://myanimelist.net",
                createdAt = createdAt,
                updatedAt = null,
                user = ani.arkhime.com.connections.anilist.api.User(
                    id = review.malId,
                    name = userName,
                    avatar = ani.arkhime.com.connections.anilist.api.UserAvatar(
                        large = userAvatar,
                        medium = userAvatar
                    ),
                    bannerImage = null,
                    isFollowing = null,
                    isFollower = null,
                    options = null,
                    mediaListOptions = null,
                    favourites = null,
                    statistics = null,
                    unreadNotificationCount = null,
                )
            )
        }
        if (mapped.isNotEmpty()) {
            media.review = ArrayList(mapped.take(5))
        }
    }

    private suspend fun fetchRelationCovers(media: Media, isAnime: Boolean) {
        val relationsToFetch = mutableListOf<Media>()
        media.prequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.sequel?.let { if (it.cover == null) relationsToFetch.add(it) }
        media.relations?.forEach { rel ->
            if (rel.cover == null && !relationsToFetch.contains(rel)) relationsToFetch.add(rel)
        }
        if (relationsToFetch.isEmpty()) return

        kotlinx.coroutines.supervisorScope {
            val deferreds = relationsToFetch.take(8).map { rel ->
                async {
                    val relMalId = rel.idMAL ?: return@async
                    val relIsAnime = rel.anime != null || rel.relation?.contains("ANIME", true) == true
                            || (rel.manga == null)
                    try {
                        val coverUrl: String?
                        val score: Int?
                        val statusStr: String?
                        val episodesCount: Int?
                        val chaptersCount: Int?
                        var resolvedFmt: String? = null

                        val node = if (relIsAnime) MAL.query.getAnimeDetails(relMalId) else MAL.query.getMangaDetails(relMalId)
                        if (node != null) {
                            coverUrl = node.mainPicture?.large ?: node.mainPicture?.medium
                            score = ((node.mean ?: 0f) * 10f).toInt()
                            statusStr = node.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                            episodesCount = node.numEpisodes
                            chaptersCount = node.numChapters
                            resolvedFmt = node.mediaType?.uppercase(java.util.Locale.US)
                        } else {
                            val jikanNode = if (relIsAnime) MAL.jikan.getAnimeById(relMalId) else MAL.jikan.getMangaById(relMalId)
                            if (jikanNode != null) {
                                coverUrl = jikanNode.images?.jpg?.largeImageUrl ?: jikanNode.images?.jpg?.imageUrl
                                score = ((jikanNode.score ?: 0f) * 10f).toInt()
                                statusStr = jikanNode.status?.replace("_", " ")?.uppercase(java.util.Locale.US)
                                episodesCount = jikanNode.episodes
                                chaptersCount = jikanNode.chapters
                                val typeStr = jikanNode.type?.uppercase(java.util.Locale.US)
                                resolvedFmt = when (typeStr) {
                                    "LIGHT NOVEL", "NOVEL" -> "NOVEL"
                                    "ONE-SHOT" -> "ONE_SHOT"
                                    "DOUJINSHI" -> "DOUJINSHI"
                                    "MANHWA" -> "MANHWA"
                                    "MANHUA" -> "MANHUA"
                                    else -> typeStr ?: if (relIsAnime) "TV" else "MANGA"
                                }
                            } else {
                                coverUrl = null
                                score = null
                                statusStr = null
                                episodesCount = null
                                chaptersCount = null
                            }
                        }

                        if (coverUrl != null || score != null || statusStr != null) {
                            if (coverUrl != null) {
                                rel.cover = coverUrl
                                rel.banner = coverUrl
                            }
                            if (score != null && rel.meanScore == null) {
                                rel.meanScore = score
                            }
                            if (statusStr != null && rel.status == null) {
                                rel.status = statusStr
                            }
                            if (relIsAnime) {
                                if (episodesCount != null) {
                                    rel.anime?.totalEpisodes = episodesCount
                                }
                            } else {
                                if (chaptersCount != null) {
                                    rel.manga?.totalChapters = chaptersCount
                                }
                            }
                            if (resolvedFmt != null) {
                                rel.format = resolvedFmt
                                val rawRelation = rel.relation?.substringBefore("\n") ?: ""
                                if (rawRelation.isNotEmpty()) {
                                    rel.relation = "$rawRelation\n$resolvedFmt"
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            deferreds.forEach { it.await() }
        }
    }

    fun setMedia(m: Media) {
        media.postValue(m)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (m.idIMDB == null) {
                    m.idIMDB = if (PrefManager.getVal<Boolean>(PrefName.RescueMode)) {
                        m.idMAL?.let { ani.arkhime.com.others.IdMappers.getImdbIdFromMal(it) }
                    } else {
                        ani.arkhime.com.others.IdMappers.getImdbId(m.id)
                    }
                }
            } catch (e: Exception) {
                ani.arkhime.com.util.Logger.log(e)
            }
        }
    }

    val adaptation = MutableLiveData<MangaAnimeUtil.AnimeAdaptation?>()
    val nextRelease = MutableLiveData<MangaAnimeUtil.NextRelease?>()
    fun loadMangaExtras(media: Media) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val seriesDeferred = async {
                    MangaAnimeUtil.getSeriesFromMedia(media)
                }

                val adaptationDeferred = async {
                    MangaAnimeUtil.getAnimeAdaptation(seriesDeferred.await())
                }

                val nextReleaseDeferred = async {
                    MangaAnimeUtil.getNextChapterPrediction(
                        media,
                        seriesDeferred.await()
                    )
                }

                adaptation.postValue(adaptationDeferred.await())
                nextRelease.postValue(nextReleaseDeferred.await())

            } catch (e: Exception) {
                Logger.log("MangaExtras error: $e")
            }
        }
    }

    // Watch Order and News integration
    data class WatchOrderItem(
        val id: String,
        val anilistId: String,
        val image: String,
        val name: String,
        val relationType: String,
        val isCurrent: Boolean = false,
        val airDate: String = "",
        val mediaType: String = "",
        val episodes: String = "",
        val rating: String = ""
    ) : java.io.Serializable

    data class NewsItem(
        val title: String,
        val url: String,
        val date: java.util.Date?
    ) : java.io.Serializable

    suspend fun getWatchOrder(media: Media): List<WatchOrderItem> {
        return tryWithSuspend {
            val headers = mapOf(
                "Referer" to "https://chiaki.site/?/tools/watch_order",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "X-Requested-With" to "XMLHttpRequest"
            )

            val malId = media.idMAL ?: ani.arkhime.com.others.IdMappers.getMalId(media.id)
            if (media.idMAL == null && malId != null) {
                media.idMAL = malId
            }
            var targetId = malId?.toString()

            if (targetId == null) {
                val searchName = media.userPreferredName.ifEmpty { media.mainName() }
                val encodedTerm = java.net.URLEncoder.encode(searchName, "UTF-8")
                val searchUrl = "https://chiaki.site/?/tools/autocomplete_series&term=$encodedTerm"
                val res = client.get(searchUrl, headers = headers).text
                val json = org.json.JSONArray(res)
                if (json.length() > 0) {
                    targetId = json.getJSONObject(0).opt("id")?.toString()
                }
            }

            if (targetId == null) return@tryWithSuspend emptyList()

            val orderUrl = "https://chiaki.site/?/tools/watch_order/id/$targetId"
            val html = client.get(orderUrl, headers = headers).text
            val doc = org.jsoup.Jsoup.parse(html)

            val rows = doc.select("tr[data-id]")
            val relationsMap = media.relations?.associateBy { it.id.toString() } ?: emptyMap()

            // Parse relations relative to targetId from data-related JSON attribute
            val targetRow = rows.find { it.attr("data-id") == targetId }
            val targetRelatedJson = targetRow?.attr("data-related") ?: "{}"
            val relatedMap = try {
                val jsonObj = org.json.JSONObject(targetRelatedJson)
                val map = mutableMapOf<String, String>()
                jsonObj.keys().forEach { key ->
                    map[key] = jsonObj.getString(key)
                }
                map
            } catch (_: Exception) {
                emptyMap<String, String>()
            }

            rows.mapIndexedNotNull { index, row ->
                val title = row.select("span.wo_title").text().trim()
                    .ifEmpty { row.select(".wo_title").text().trim() }
                if (title.isEmpty() || title.equals("Unknown title", ignoreCase = true)) return@mapIndexedNotNull null

                val id = row.attr("data-id").ifEmpty { targetId }
                val anilistId = row.attr("data-anilist-id")

                // 1. Cover image from style background-image
                val style = row.select("div.wo_avatar_big").attr("style")
                val imgMatch = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)
                var imageUrl = imgMatch?.groupValues?.get(1)?.let { path ->
                    when {
                        path.startsWith("http") -> path
                        path.startsWith("/") -> "https://chiaki.site$path"
                        else -> "https://chiaki.site/$path"
                    }
                } ?: ""

                if (imageUrl.isEmpty()) {
                    if (anilistId == media.id.toString()) {
                        imageUrl = media.cover ?: ""
                    } else if (relationsMap.containsKey(anilistId)) {
                        imageUrl = relationsMap[anilistId]?.cover ?: ""
                    }
                }

                // 2. Metadata text: Air Date | Media Type | Episodes | Rating
                val metaText = row.select("span.uk-text-muted.uk-text-small").text().trim()
                    .ifEmpty { row.select(".wo_meta").text().trim() }
                val parts = metaText.split('|').map { it.trim() }
                val airDate = if (parts.isNotEmpty()) parts[0] else ""
                val format = if (parts.size > 1) parts[1] else ""
                val eps = if (parts.size > 2) parts[2].substringBefore("★").substringBefore("(").trim() else ""
                val rating = ""

                // 3. Is current anime
                val cleanId = id.trim()
                val cleanAnilistId = anilistId.trim()
                val currentMalId = media.idMAL ?: malId
                val isCurrent = (cleanAnilistId.isNotEmpty() && cleanAnilistId == media.id.toString()) ||
                        (cleanId.isNotEmpty() && currentMalId != null && cleanId == currentMalId.toString()) ||
                        (cleanId.isNotEmpty() && targetId != null && cleanId == targetId.trim() && (currentMalId == null || currentMalId.toString() == targetId.trim())) ||
                        (title.isNotEmpty() && (title.equals(media.userPreferredName, ignoreCase = true) ||
                                title.equals(media.mainName(), ignoreCase = true) ||
                                title.equals(media.name, ignoreCase = true) ||
                                title.equals(media.nameRomaji, ignoreCase = true)))

                // 4. Relation type
                val relationType = when {
                    isCurrent -> "Selected"
                    relatedMap.containsKey(id) -> relatedMap[id]!!
                    relationsMap.containsKey(anilistId) -> {
                        relationsMap[anilistId]?.relation?.replace("_", " ")?.lowercase()
                            ?.split(" ")?.joinToString(" ") { it.replaceFirstChar(Char::titlecase) } ?: ""
                    }
                    index == 0 -> "Main Story"
                    format.equals("TV", ignoreCase = true) -> "Sequel"
                    format.equals("OVA", ignoreCase = true) || format.equals("Special", ignoreCase = true) -> "Side Story"
                    format.equals("Movie", ignoreCase = true) -> "Movie"
                    else -> format.ifEmpty { "Entry" }
                }

                WatchOrderItem(
                    id = id,
                    anilistId = anilistId,
                    image = imageUrl,
                    name = title,
                    relationType = relationType,
                    isCurrent = isCurrent,
                    airDate = airDate,
                    mediaType = format,
                    episodes = eps,
                    rating = rating
                )
            }
        } ?: emptyList()
    }


    suspend fun getAnimeNews(media: Media): List<NewsItem> {
        return tryWithSuspend {
            var malId = media.idMAL
            if (malId == null || malId == 0) {
                malId = ani.arkhime.com.others.IdMappers.getMalId(media.id)
            }
            if (malId == null || malId == 0) {
                malId = runCatching {
                    Anilist.query.getMedia(media.id)?.idMAL
                }.getOrNull()
                if (malId != null && malId != 0) {
                    media.idMAL = malId
                }
            }
            val targetId = malId?.takeIf { it != 0 } ?: return@tryWithSuspend emptyList()

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Accept" to "application/json, text/plain, */*"
            )

            suspend fun fetchKuroiruNews(id: Int): List<NewsItem> {
                val jsonStr = runCatching {
                    val res = client.get("https://kuroiru.co/api/anime/$id", headers = headers)
                    if (res.isSuccessful) res.text else null
                }.getOrNull() ?: return emptyList()

                val root = runCatching {
                    Json.parseToJsonElement(jsonStr).jsonObject
                }.getOrNull() ?: return emptyList()
                val newsArr = root["news"]?.jsonArray ?: return emptyList()
                val list = mutableListOf<NewsItem>()
                for (item in newsArr) {
                    val itemObj = item.jsonObject
                    val timeMs = itemObj["time"]?.jsonPrimitive?.longOrNull ?: 0L
                    val rawTitle = itemObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val title = org.jsoup.parser.Parser.unescapeEntities(rawTitle, false)
                    val rawLink = itemObj["link"]?.jsonPrimitive?.contentOrNull ?: ""
                    val link = normalizeKuroiruLink(rawLink)
                    if (title.isNotEmpty() && link.isNotEmpty()) {
                        list.add(
                            NewsItem(
                                title = title,
                                url = link,
                                date = if (timeMs > 0) java.util.Date(timeMs * 1000L) else null
                            )
                        )
                    }
                }
                return list
            }

            var result = fetchKuroiruNews(targetId)
            if (result.isEmpty()) {
                val candidateIds = mutableListOf<Int>()
                media.prequel?.idMAL?.let { candidateIds.add(it) }
                media.relations?.forEach { rel ->
                    rel.idMAL?.let { candidateIds.add(it) }
                }
                for (candId in candidateIds) {
                    if (candId != targetId && candId != 0) {
                        result = fetchKuroiruNews(candId)
                        if (result.isNotEmpty()) break
                    }
                }
            }

            result.sortedByDescending { it.date }
        } ?: emptyList()
    }

    private fun normalizeKuroiruLink(rawLink: String): String {
        val link = rawLink.trim()
        if (link.isEmpty()) return ""
        if (link.startsWith("http://", ignoreCase = true) ||
            link.startsWith("https://", ignoreCase = true)
        ) return link
       
        val digitId = link.removePrefix(".")
        if (digitId.isNotEmpty() && digitId.all { it.isDigit() }) {
            return "https://www.animenewsnetwork.com/news/.$digitId"
        }
        return link
    }

    suspend fun getMangaNovelNews(media: Media): List<NewsItem> {
        return tryWithSuspend {
            val bakaBase = "https://api.mangabaka.org/v1"
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Accept" to "application/json, text/plain, */*"
            )
            suspend fun getBakaId(): Int? {
                if (media.id != 0) {
                    val id = runCatching {
                        val res = client.get("$bakaBase/source/anilist/${media.id}", headers = headers)
                        if (!res.isSuccessful) return@runCatching null
                        val root = Json.parseToJsonElement(res.text).jsonObject
                        root["data"]?.jsonObject?.get("series")?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull?.takeIf { it != 0 }
                    }.getOrNull()
                    if (id != null && id != 0) return id
                }

                if (media.idMAL != null && media.idMAL != 0) {
                    val id = runCatching {
                        val res = client.get("$bakaBase/source/my-anime-list/${media.idMAL}", headers = headers)
                        if (!res.isSuccessful) return@runCatching null
                        val root = Json.parseToJsonElement(res.text).jsonObject
                        root["data"]?.jsonObject?.get("series")?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull?.takeIf { it != 0 }
                    }.getOrNull()
                    if (id != null && id != 0) return id
                }

                val titleToSearch = media.userPreferredName.ifEmpty { media.mainName() }
                if (titleToSearch.isNotEmpty()) {
                    val encodedTitle = java.net.URLEncoder.encode(titleToSearch, "UTF-8")
                    val id = runCatching {
                        val res = client.get("$bakaBase/series/search?q=$encodedTitle", headers = headers)
                        if (!res.isSuccessful) return@runCatching null
                        val root = Json.parseToJsonElement(res.text).jsonObject
                        root["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull?.takeIf { it != 0 }
                    }.getOrNull()
                    if (id != null && id != 0) return id
                }

                return null
            }

            val bakaId = getBakaId() ?: return@tryWithSuspend emptyList()

            val jsonStr = runCatching {
                val res = client.get("$bakaBase/series/$bakaId/news", headers = headers)
                if (res.isSuccessful) res.text else null
            }.getOrNull() ?: return@tryWithSuspend emptyList()

            val root = runCatching { Json.parseToJsonElement(jsonStr).jsonObject }.getOrNull()
                ?: return@tryWithSuspend emptyList()
            val newsArr = root["data"]?.jsonArray ?: return@tryWithSuspend emptyList()
            val result = mutableListOf<NewsItem>()

            fun parseDate(dateStr: String): java.util.Date? {
                if (dateStr.isEmpty()) return null
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val instantDate = runCatching {
                        java.util.Date(java.time.Instant.parse(dateStr).toEpochMilli())
                    }.getOrNull()
                    if (instantDate != null) return instantDate
                }
                val formats = listOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd"
                )
                for (fmt in formats) {
                    val parsed = runCatching {
                        val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        sdf.parse(dateStr)
                    }.getOrNull()
                    if (parsed != null) return parsed
                }
                return null
            }

            for (item in newsArr) {
                val itemObj = item.jsonObject
                val pubAt = itemObj["published_at"]?.jsonPrimitive?.contentOrNull ?: ""
                val date = parseDate(pubAt)
                val rawTitle = itemObj["title"]?.jsonPrimitive?.contentOrNull ?: ""
                val title = org.jsoup.parser.Parser.unescapeEntities(rawTitle, false)
                val url = itemObj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    result.add(
                        NewsItem(
                            title = title,
                            url = url,
                            date = date
                        )
                    )
                }
            }
            result.sortByDescending { it.date }
            result
        } ?: emptyList()
    }
}
