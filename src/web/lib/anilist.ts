import { ANILIST_GRAPHQL_URL } from "./config";
import type {
  Activity,
  AiringSchedule,
  AniNotification,
  Favourites,
  FuzzyDate,
  Media,
  MediaDetail,
  MediaListCollection,
  MediaListEntry,
  MediaListStatus,
  MediaPage,
  MediaType,
  PageInfo,
  Review,
  UserStatisticTypes,
  Viewer,
} from "./types";

class AniListError extends Error {
  status: number;
  retryAfter?: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
    this.name = "AniListError";
  }
}

/**
 * How many requests AniList says we have left in the current minute, read
 * from the response headers. The save loop uses it to pace itself instead
 * of sleeping a fixed amount between every write.
 */
let remainingRateLimit = Infinity;

export function rateLimitRemaining(): number {
  return remainingRateLimit;
}

async function gql<T>(
  query: string,
  variables: Record<string, unknown>,
  token: string | null,
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "application/json",
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(ANILIST_GRAPHQL_URL, {
    method: "POST",
    headers,
    body: JSON.stringify({ query, variables }),
  });

  const remaining = res.headers.get("X-RateLimit-Remaining");
  if (remaining !== null) remainingRateLimit = Number(remaining);

  if (res.status === 401) {
    throw new AniListError("Session expired. Please log in again.", res.status);
  }

  if (res.status === 429) {
    const retryAfter = Number(res.headers.get("Retry-After")) || 60;
    const err = new AniListError("Rate limited by AniList — backing off.", 429);
    err.retryAfter = retryAfter;
    throw err;
  }

  const json = await res.json().catch(() => null);

  if (!res.ok || (json && json.errors)) {
    const msg =
      json?.errors?.[0]?.message || `AniList request failed (${res.status})`;
    // A 400 with an auth-ish message means the token is dead; otherwise it is
    // just a bad query/variable and should not log the user out.
    const status =
      res.status === 400 && /token|unauthor|invalid/i.test(msg)
        ? 401
        : res.status;
    throw new AniListError(msg, status);
  }

  return json.data as T;
}

// =====================================================================
// GraphQL fragments
// =====================================================================

/** Minimal media shape used inside relation edges and compact lists. */
const MEDIA_MINI = /* GraphQL */ `
  id
  type
  format
  status
  episodes
  chapters
  volumes
  duration
  averageScore
  meanScore
  popularity
  favourites
  genres
  season
  seasonYear
  isAdult
  countryOfOrigin
  siteUrl
  startDate {
    year
    month
    day
  }
  endDate {
    year
    month
    day
  }
  title {
    romaji
    english
    native
    userPreferred
  }
  coverImage {
    extraLarge
    large
    medium
    color
  }
  nextAiringEpisode {
    airingAt
    timeUntilAiring
    episode
  }
  mediaListEntry {
    id
    status
    score
    progress
    progressVolumes
    repeat
    notes
    private
    hiddenFromStatusLists
    startedAt {
      year
      month
      day
    }
    completedAt {
      year
      month
      day
    }
  }
`;

/** Everything a poster/landscape card in a grid needs. */
const MEDIA_CARD = /* GraphQL */ `
  ${MEDIA_MINI}
  bannerImage
  description(asHtml: false)
  studios(isMain: true) {
    nodes {
      id
      name
      siteUrl
    }
  }
`;

// =====================================================================
// Viewer
// =====================================================================

const VIEWER_QUERY = /* GraphQL */ `
  query {
    Viewer {
      id
      name
      about
      siteUrl
      unreadNotificationCount
      avatar {
        large
        medium
      }
      bannerImage
      options {
        titleLanguage
        displayAdultContent
        profileColor
      }
      mediaListOptions {
        scoreFormat
      }
    }
  }
`;

export async function fetchViewer(token: string): Promise<Viewer> {
  const data = await gql<{ Viewer: Viewer }>(VIEWER_QUERY, {}, token);
  return data.Viewer;
}

// =====================================================================
// The user's list
// =====================================================================

const LIST_QUERY = /* GraphQL */ `
  query ($userId: Int, $type: MediaType) {
    MediaListCollection(userId: $userId, type: $type) {
      lists {
        name
        status
        isCustomList
        entries {
          id
          status
          score
          progress
          progressVolumes
          repeat
          notes
          private
          hiddenFromStatusLists
          startedAt {
            year
            month
            day
          }
          completedAt {
            year
            month
            day
          }
          updatedAt
          createdAt
          media {
            ${MEDIA_CARD}
            relations {
              edges {
                relationType
                node {
                  ${MEDIA_MINI}
                }
              }
            }
          }
        }
      }
    }
  }
`;

export async function fetchMediaListCollection(
  token: string,
  userId: number,
  type: MediaType,
): Promise<MediaListCollection> {
  const data = await gql<{ MediaListCollection: MediaListCollection }>(
    LIST_QUERY,
    { userId, type },
    token,
  );
  return data.MediaListCollection;
}

/** Flatten a MediaListCollection's grouped lists into one deduped array. */
export function flattenCollection(
  collection: MediaListCollection,
): MediaListEntry[] {
  const seen = new Set<number>();
  const flat: MediaListEntry[] = [];
  for (const list of collection.lists ?? []) {
    // Custom lists repeat entries that already appear under a status list.
    for (const e of list.entries ?? []) {
      if (seen.has(e.id)) continue;
      seen.add(e.id);
      flat.push(e);
    }
  }
  return flat;
}

/** Names of the user's custom lists, in the order AniList returns them. */
export function customListNames(collection: MediaListCollection): string[] {
  return (collection.lists ?? [])
    .filter((l) => l.isCustomList)
    .map((l) => l.name);
}

// =====================================================================
// Browse / search
// =====================================================================

export interface BrowseParams {
  page?: number;
  perPage?: number;
  search?: string | null;
  type?: MediaType;
  sort?: string[];
  genres?: string[];
  excludedGenres?: string[];
  tags?: string[];
  season?: string | null;
  seasonYear?: number | null;
  formats?: string[];
  airingStatus?: string[];
  countryOfOrigin?: string | null;
  minScore?: number | null;
  yearFrom?: number | null;
  yearTo?: number | null;
  isAdult?: boolean | null;
  onList?: boolean | null;
  source?: string | null;
}

const BROWSE_QUERY = /* GraphQL */ `
  query (
    $page: Int
    $perPage: Int
    $search: String
    $type: MediaType
    $sort: [MediaSort]
    $genre_in: [String]
    $genre_not_in: [String]
    $tag_in: [String]
    $season: MediaSeason
    $seasonYear: Int
    $format_in: [MediaFormat]
    $status_in: [MediaStatus]
    $countryOfOrigin: CountryCode
    $averageScore_greater: Int
    $startDate_greater: FuzzyDateInt
    $startDate_lesser: FuzzyDateInt
    $isAdult: Boolean
    $onList: Boolean
    $source: MediaSource
  ) {
    Page(page: $page, perPage: $perPage) {
      pageInfo {
        total
        perPage
        currentPage
        lastPage
        hasNextPage
      }
      media(
        search: $search
        type: $type
        sort: $sort
        genre_in: $genre_in
        genre_not_in: $genre_not_in
        tag_in: $tag_in
        season: $season
        seasonYear: $seasonYear
        format_in: $format_in
        status_in: $status_in
        countryOfOrigin: $countryOfOrigin
        averageScore_greater: $averageScore_greater
        startDate_greater: $startDate_greater
        startDate_lesser: $startDate_lesser
        isAdult: $isAdult
        onList: $onList
        source: $source
      ) {
        ${MEDIA_CARD}
      }
    }
  }
`;

/** Turn a year into AniList's FuzzyDateInt (YYYYMMDD as a plain number). */
function fuzzyInt(year: number, month: number, day: number): number {
  return year * 10000 + month * 100 + day;
}

export async function browseMedia(
  params: BrowseParams,
  token: string | null,
): Promise<MediaPage> {
  const vars: Record<string, unknown> = {
    page: params.page ?? 1,
    perPage: params.perPage ?? 30,
    type: params.type ?? "ANIME",
    sort: params.sort ?? ["POPULARITY_DESC"],
  };

  const search = params.search?.trim();
  if (search) vars.search = search;
  if (params.genres?.length) vars.genre_in = params.genres;
  if (params.excludedGenres?.length) vars.genre_not_in = params.excludedGenres;
  if (params.tags?.length) vars.tag_in = params.tags;
  if (params.season) vars.season = params.season;
  if (params.seasonYear) vars.seasonYear = params.seasonYear;
  if (params.formats?.length) vars.format_in = params.formats;
  if (params.airingStatus?.length) vars.status_in = params.airingStatus;
  if (params.countryOfOrigin) vars.countryOfOrigin = params.countryOfOrigin;
  if (params.minScore) vars.averageScore_greater = params.minScore;
  if (params.yearFrom) vars.startDate_greater = fuzzyInt(params.yearFrom, 1, 1);
  if (params.yearTo) vars.startDate_lesser = fuzzyInt(params.yearTo, 12, 31);
  if (params.isAdult !== null && params.isAdult !== undefined)
    vars.isAdult = params.isAdult;
  if (params.onList !== null && params.onList !== undefined)
    vars.onList = params.onList;
  if (params.source) vars.source = params.source;

  const data = await gql<{ Page: MediaPage }>(BROWSE_QUERY, vars, token);
  return data.Page;
}

/** Fast, small query powering the header's instant-search dropdown. */
const QUICK_SEARCH_QUERY = /* GraphQL */ `
  query ($search: String, $perPage: Int) {
    anime: Page(perPage: $perPage) {
      media(search: $search, type: ANIME, sort: [SEARCH_MATCH]) {
        ${MEDIA_MINI}
      }
    }
    manga: Page(perPage: $perPage) {
      media(search: $search, type: MANGA, sort: [SEARCH_MATCH]) {
        ${MEDIA_MINI}
      }
    }
  }
`;

export async function quickSearch(
  search: string,
  token: string | null,
  perPage = 6,
): Promise<{ anime: Media[]; manga: Media[] }> {
  const data = await gql<{
    anime: { media: Media[] };
    manga: { media: Media[] };
  }>(QUICK_SEARCH_QUERY, { search, perPage }, token);
  return { anime: data.anime.media ?? [], manga: data.manga.media ?? [] };
}

// =====================================================================
// Discover (home) — several shelves in one round-trip
// =====================================================================

const DISCOVER_QUERY = /* GraphQL */ `
  query (
    $type: MediaType
    $season: MediaSeason
    $seasonYear: Int
    $nextSeason: MediaSeason
    $nextYear: Int
    $perPage: Int
    $isAdult: Boolean
  ) {
    trending: Page(perPage: $perPage) {
      media(type: $type, sort: [TRENDING_DESC], isAdult: $isAdult) {
        ${MEDIA_CARD}
      }
    }
    season: Page(perPage: $perPage) {
      media(
        type: $type
        season: $season
        seasonYear: $seasonYear
        sort: [POPULARITY_DESC]
        isAdult: $isAdult
      ) {
        ${MEDIA_CARD}
      }
    }
    upcoming: Page(perPage: $perPage) {
      media(
        type: $type
        season: $nextSeason
        seasonYear: $nextYear
        sort: [POPULARITY_DESC]
        isAdult: $isAdult
      ) {
        ${MEDIA_CARD}
      }
    }
    popular: Page(perPage: $perPage) {
      media(type: $type, sort: [POPULARITY_DESC], isAdult: $isAdult) {
        ${MEDIA_CARD}
      }
    }
    top: Page(perPage: $perPage) {
      media(type: $type, sort: [SCORE_DESC], isAdult: $isAdult) {
        ${MEDIA_CARD}
      }
    }
  }
`;

export interface DiscoverShelves {
  trending: Media[];
  season: Media[];
  upcoming: Media[];
  popular: Media[];
  top: Media[];
}

export async function fetchDiscover(
  type: MediaType,
  token: string | null,
  opts: {
    season: string;
    seasonYear: number;
    nextSeason: string;
    nextYear: number;
    perPage?: number;
    isAdult?: boolean | null;
  },
): Promise<DiscoverShelves> {
  const vars: Record<string, unknown> = {
    type,
    season: opts.season,
    seasonYear: opts.seasonYear,
    nextSeason: opts.nextSeason,
    nextYear: opts.nextYear,
    perPage: opts.perPage ?? 16,
  };
  if (opts.isAdult === false) vars.isAdult = false;

  const data = await gql<Record<keyof DiscoverShelves, { media: Media[] }>>(
    DISCOVER_QUERY,
    vars,
    token,
  );
  return {
    trending: data.trending?.media ?? [],
    season: data.season?.media ?? [],
    upcoming: data.upcoming?.media ?? [],
    popular: data.popular?.media ?? [],
    top: data.top?.media ?? [],
  };
}

/** "Because you watched X" — recommendations hanging off one media. */
const RECS_FOR_MEDIA_QUERY = /* GraphQL */ `
  query ($id: Int, $perPage: Int) {
    Media(id: $id) {
      id
      title {
        userPreferred
      }
      recommendations(sort: [RATING_DESC], perPage: $perPage) {
        nodes {
          id
          rating
          mediaRecommendation {
            ${MEDIA_CARD}
          }
        }
      }
    }
  }
`;

export async function fetchRecommendationsFor(
  mediaId: number,
  token: string | null,
  perPage = 16,
): Promise<Media[]> {
  const data = await gql<{
    Media: {
      recommendations: {
        nodes: { mediaRecommendation: Media | null }[];
      } | null;
    };
  }>(RECS_FOR_MEDIA_QUERY, { id: mediaId, perPage }, token);
  return (data.Media?.recommendations?.nodes ?? [])
    .map((n) => n.mediaRecommendation)
    .filter((m): m is Media => !!m);
}

// =====================================================================
// Media detail
// =====================================================================

const MEDIA_DETAIL_QUERY = /* GraphQL */ `
  query ($id: Int) {
    Media(id: $id) {
      ${MEDIA_CARD}
      source
      hashtag
      synonyms
      trending
      isFavourite
      trailer {
        id
        site
        thumbnail
      }
      tags {
        id
        name
        description
        category
        rank
        isMediaSpoiler
        isAdult
      }
      characters(sort: [ROLE, RELEVANCE, ID], perPage: 14) {
        edges {
          id
          role
          node {
            id
            name {
              full
              native
            }
            image {
              large
              medium
            }
            siteUrl
          }
          voiceActors(language: JAPANESE, sort: [RELEVANCE]) {
            id
            name {
              full
            }
            image {
              large
            }
            languageV2
            siteUrl
          }
        }
      }
      staff(sort: [RELEVANCE, ID], perPage: 10) {
        edges {
          id
          role
          node {
            id
            name {
              full
            }
            image {
              large
            }
            siteUrl
          }
        }
      }
      relations {
        edges {
          relationType
          node {
            ${MEDIA_MINI}
          }
        }
      }
      recommendations(sort: [RATING_DESC], perPage: 12) {
        nodes {
          id
          rating
          mediaRecommendation {
            ${MEDIA_MINI}
          }
        }
      }
      externalLinks {
        id
        site
        url
        type
        color
        icon
        language
      }
      streamingEpisodes {
        title
        thumbnail
        url
        site
      }
      rankings {
        id
        rank
        type
        format
        year
        season
        allTime
        context
      }
      stats {
        scoreDistribution {
          score
          amount
        }
        statusDistribution {
          status
          amount
        }
      }
    }
  }
`;

export async function fetchMediaDetail(
  id: number,
  token: string | null,
): Promise<MediaDetail> {
  const data = await gql<{ Media: MediaDetail }>(
    MEDIA_DETAIL_QUERY,
    { id },
    token,
  );
  return data.Media;
}

// =====================================================================
// Airing schedule
// =====================================================================

const SCHEDULE_QUERY = /* GraphQL */ `
  query ($start: Int, $end: Int, $page: Int) {
    Page(page: $page, perPage: 50) {
      pageInfo {
        total
        perPage
        currentPage
        lastPage
        hasNextPage
      }
      airingSchedules(
        airingAt_greater: $start
        airingAt_lesser: $end
        sort: [TIME]
      ) {
        id
        airingAt
        timeUntilAiring
        episode
        media {
          ${MEDIA_MINI}
        }
      }
    }
  }
`;

/** Every episode airing in [start, end) — paged through transparently. */
export async function fetchAiringSchedule(
  start: number,
  end: number,
  token: string | null,
): Promise<AiringSchedule[]> {
  const all: AiringSchedule[] = [];
  for (let page = 1; page <= 8; page++) {
    const data = await gql<{
      Page: { pageInfo: PageInfo; airingSchedules: AiringSchedule[] };
    }>(SCHEDULE_QUERY, { start, end, page }, token);
    all.push(...(data.Page.airingSchedules ?? []));
    if (!data.Page.pageInfo?.hasNextPage) break;
  }
  return all;
}

// =====================================================================
// Statistics
// =====================================================================

const STATS_FIELDS = /* GraphQL */ `
  count
  meanScore
  standardDeviation
  minutesWatched
  episodesWatched
  chaptersRead
  volumesRead
  genres(sort: [COUNT_DESC], limit: 16) {
    genre
    count
    meanScore
    minutesWatched
    chaptersRead
  }
  tags(sort: [COUNT_DESC], limit: 16) {
    tag {
      id
      name
    }
    count
    meanScore
  }
  formats(sort: [COUNT_DESC]) {
    format
    count
    meanScore
  }
  statuses(sort: [COUNT_DESC]) {
    status
    count
    meanScore
  }
  scores(sort: [MEAN_SCORE]) {
    score
    count
  }
  releaseYears(sort: [MEAN_SCORE]) {
    releaseYear
    count
    meanScore
  }
  countries(sort: [COUNT_DESC]) {
    country
    count
  }
  studios(sort: [COUNT_DESC], limit: 12) {
    studio {
      id
      name
    }
    count
    meanScore
  }
  voiceActors(sort: [COUNT_DESC], limit: 12) {
    voiceActor {
      id
      name {
        full
      }
    }
    count
  }
  staff(sort: [COUNT_DESC], limit: 12) {
    staff {
      id
      name {
        full
      }
    }
    count
  }
`;

const STATS_QUERY = /* GraphQL */ `
  query ($userId: Int) {
    User(id: $userId) {
      id
      statistics {
        anime {
          ${STATS_FIELDS}
        }
        manga {
          ${STATS_FIELDS}
        }
      }
    }
  }
`;

export async function fetchUserStatistics(
  userId: number,
  token: string | null,
): Promise<UserStatisticTypes> {
  const data = await gql<{ User: { statistics: UserStatisticTypes } }>(
    STATS_QUERY,
    { userId },
    token,
  );
  return data.User.statistics;
}

// =====================================================================
// Favourites
// =====================================================================

const FAVOURITES_QUERY = /* GraphQL */ `
  query ($userId: Int, $page: Int) {
    User(id: $userId) {
      id
      favourites {
        anime(page: $page, perPage: 30) {
          nodes {
            ${MEDIA_MINI}
          }
        }
        manga(page: $page, perPage: 30) {
          nodes {
            ${MEDIA_MINI}
          }
        }
        characters(page: $page, perPage: 30) {
          nodes {
            id
            name {
              full
              native
            }
            image {
              large
            }
            siteUrl
            favourites
          }
        }
        staff(page: $page, perPage: 30) {
          nodes {
            id
            name {
              full
            }
            image {
              large
            }
            siteUrl
            primaryOccupations
            favourites
          }
        }
        studios(page: $page, perPage: 30) {
          nodes {
            id
            name
            siteUrl
            favourites
          }
        }
      }
    }
  }
`;

export async function fetchFavourites(
  userId: number,
  token: string | null,
  page = 1,
): Promise<Favourites> {
  const data = await gql<{ User: { favourites: Favourites } }>(
    FAVOURITES_QUERY,
    { userId, page },
    token,
  );
  return data.User.favourites;
}

const TOGGLE_FAVOURITE = /* GraphQL */ `
  mutation (
    $animeId: Int
    $mangaId: Int
    $characterId: Int
    $staffId: Int
    $studioId: Int
  ) {
    ToggleFavourite(
      animeId: $animeId
      mangaId: $mangaId
      characterId: $characterId
      staffId: $staffId
      studioId: $studioId
    ) {
      anime {
        pageInfo {
          total
        }
      }
    }
  }
`;

export async function toggleFavourite(
  token: string,
  target:
    | { animeId: number }
    | { mangaId: number }
    | { characterId: number }
    | { staffId: number }
    | { studioId: number },
): Promise<void> {
  await gql(TOGGLE_FAVOURITE, target, token);
}

// =====================================================================
// Activity feed
// =====================================================================

const ACTIVITY_QUERY = /* GraphQL */ `
  query ($userId: Int, $isFollowing: Boolean, $page: Int) {
    Page(page: $page, perPage: 25) {
      pageInfo {
        total
        perPage
        currentPage
        lastPage
        hasNextPage
      }
      activities(
        userId: $userId
        isFollowing: $isFollowing
        sort: [ID_DESC]
        type_in: [ANIME_LIST, MANGA_LIST, TEXT]
      ) {
        ... on ListActivity {
          id
          type
          status
          progress
          createdAt
          siteUrl
          likeCount
          replyCount
          isLiked
          user {
            id
            name
            avatar {
              large
            }
            siteUrl
          }
          media {
            ${MEDIA_MINI}
          }
        }
        ... on TextActivity {
          id
          type
          text
          createdAt
          siteUrl
          likeCount
          replyCount
          isLiked
          user {
            id
            name
            avatar {
              large
            }
            siteUrl
          }
        }
      }
    }
  }
`;

export async function fetchActivities(
  token: string,
  opts: { userId?: number; isFollowing?: boolean; page?: number },
): Promise<{ pageInfo: PageInfo; activities: Activity[] }> {
  const vars: Record<string, unknown> = { page: opts.page ?? 1 };
  if (opts.userId) vars.userId = opts.userId;
  if (opts.isFollowing) vars.isFollowing = true;
  const data = await gql<{
    Page: { pageInfo: PageInfo; activities: (Activity | null)[] };
  }>(ACTIVITY_QUERY, vars, token);
  return {
    pageInfo: data.Page.pageInfo,
    activities: (data.Page.activities ?? []).filter((a): a is Activity => !!a),
  };
}

const TOGGLE_LIKE = /* GraphQL */ `
  mutation ($id: Int) {
    ToggleLikeV2(id: $id, type: ACTIVITY) {
      ... on ListActivity {
        id
        isLiked
        likeCount
      }
      ... on TextActivity {
        id
        isLiked
        likeCount
      }
    }
  }
`;

export async function toggleActivityLike(
  token: string,
  id: number,
): Promise<{ isLiked: boolean; likeCount: number } | null> {
  const data = await gql<{
    ToggleLikeV2: { isLiked: boolean; likeCount: number } | null;
  }>(TOGGLE_LIKE, { id }, token);
  return data.ToggleLikeV2;
}

// =====================================================================
// Notifications
// =====================================================================

const NOTIFICATIONS_QUERY = /* GraphQL */ `
  query ($page: Int, $reset: Boolean) {
    Page(page: $page, perPage: 25) {
      notifications(resetNotificationCount: $reset) {
        ... on AiringNotification {
          id
          type
          episode
          contexts
          createdAt
          media {
            ${MEDIA_MINI}
          }
        }
        ... on RelatedMediaAdditionNotification {
          id
          type
          context
          createdAt
          media {
            ${MEDIA_MINI}
          }
        }
        ... on MediaDataChangeNotification {
          id
          type
          context
          reason
          createdAt
          media {
            ${MEDIA_MINI}
          }
        }
        ... on FollowingNotification {
          id
          type
          context
          createdAt
          user {
            id
            name
            avatar {
              large
            }
            siteUrl
          }
        }
        ... on ActivityLikeNotification {
          id
          type
          context
          createdAt
          user {
            id
            name
            avatar {
              large
            }
            siteUrl
          }
        }
        ... on ActivityReplyNotification {
          id
          type
          context
          createdAt
          user {
            id
            name
            avatar {
              large
            }
            siteUrl
          }
        }
      }
    }
  }
`;

export async function fetchNotifications(
  token: string,
  reset = true,
  page = 1,
): Promise<AniNotification[]> {
  const data = await gql<{
    Page: { notifications: (AniNotification | null)[] };
  }>(NOTIFICATIONS_QUERY, { page, reset }, token);
  return (data.Page.notifications ?? []).filter(
    (n): n is AniNotification => !!n,
  );
}

// =====================================================================
// Reviews
// =====================================================================

const REVIEWS_QUERY = /* GraphQL */ `
  query ($mediaId: Int, $perPage: Int) {
    Page(perPage: $perPage) {
      reviews(mediaId: $mediaId, sort: [RATING_DESC]) {
        id
        summary
        score
        rating
        ratingAmount
        createdAt
        siteUrl
        user {
          id
          name
          avatar {
            large
          }
          siteUrl
        }
      }
    }
  }
`;

export async function fetchReviews(
  mediaId: number,
  token: string | null,
  perPage = 6,
): Promise<Review[]> {
  const data = await gql<{ Page: { reviews: Review[] } }>(
    REVIEWS_QUERY,
    { mediaId, perPage },
    token,
  );
  return data.Page.reviews ?? [];
}

// =====================================================================
// Filter vocabulary (genres + tags)
// =====================================================================

const FILTER_OPTIONS_QUERY = /* GraphQL */ `
  query {
    GenreCollection
    MediaTagCollection {
      id
      name
      category
      isAdult
    }
  }
`;

export async function fetchFilterOptions(token: string | null): Promise<{
  genres: string[];
  tags: {
    id: number;
    name: string;
    category: string | null;
    isAdult: boolean | null;
  }[];
}> {
  const data = await gql<{
    GenreCollection: string[];
    MediaTagCollection: {
      id: number;
      name: string;
      category: string | null;
      isAdult: boolean | null;
    }[];
  }>(FILTER_OPTIONS_QUERY, {}, token);
  return {
    genres: data.GenreCollection ?? [],
    tags: data.MediaTagCollection ?? [],
  };
}

// =====================================================================
// Mutations — writing back to the user's list
// =====================================================================

export interface SaveInput {
  mediaId: number;
  status: MediaListStatus;
  score: number;
  progress: number;
  progressVolumes?: number | null;
  repeat?: number | null;
  notes?: string | null;
  private?: boolean | null;
  hiddenFromStatusLists?: boolean | null;
  startedAt?: FuzzyDate | null;
  completedAt?: FuzzyDate | null;
}

const SAVE_MUTATION = /* GraphQL */ `
  mutation (
    $mediaId: Int
    $status: MediaListStatus
    $score: Float
    $progress: Int
    $progressVolumes: Int
    $repeat: Int
    $notes: String
    $private: Boolean
    $hiddenFromStatusLists: Boolean
    $startedAt: FuzzyDateInput
    $completedAt: FuzzyDateInput
  ) {
    SaveMediaListEntry(
      mediaId: $mediaId
      status: $status
      score: $score
      progress: $progress
      progressVolumes: $progressVolumes
      repeat: $repeat
      notes: $notes
      private: $private
      hiddenFromStatusLists: $hiddenFromStatusLists
      startedAt: $startedAt
      completedAt: $completedAt
    ) {
      id
      status
      score
      progress
    }
  }
`;

/** Strip undefined keys so we never send `null` for untouched fields. */
function saveVariables(input: SaveInput): Record<string, unknown> {
  const vars: Record<string, unknown> = {
    mediaId: input.mediaId,
    status: input.status,
    score: input.score,
    progress: input.progress,
  };
  if (input.progressVolumes != null)
    vars.progressVolumes = input.progressVolumes;
  if (input.repeat != null) vars.repeat = input.repeat;
  if (input.notes != null) vars.notes = input.notes;
  if (input.private != null) vars.private = input.private;
  if (input.hiddenFromStatusLists != null)
    vars.hiddenFromStatusLists = input.hiddenFromStatusLists;
  if (input.startedAt !== undefined)
    vars.startedAt = input.startedAt ?? {
      year: null,
      month: null,
      day: null,
    };
  if (input.completedAt !== undefined)
    vars.completedAt = input.completedAt ?? {
      year: null,
      month: null,
      day: null,
    };
  return vars;
}

export async function saveMediaListEntry(
  token: string,
  input: SaveInput,
): Promise<void> {
  await gql(SAVE_MUTATION, saveVariables(input), token);
}

const DELETE_MUTATION = /* GraphQL */ `
  mutation ($id: Int) {
    DeleteMediaListEntry(id: $id) {
      deleted
    }
  }
`;

export async function deleteMediaListEntry(
  token: string,
  entryId: number,
): Promise<boolean> {
  const data = await gql<{ DeleteMediaListEntry: { deleted: boolean } }>(
    DELETE_MUTATION,
    { id: entryId },
    token,
  );
  return !!data.DeleteMediaListEntry?.deleted;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export interface SaveResult {
  saved: number;
  failed: { mediaId: number; message: string; status?: number }[];
}

/**
 * Save many entries sequentially with adaptive spacing so we stay under
 * AniList's rate limit (90 req/min). We read X-RateLimit-Remaining from the
 * previous response and only slow down as we approach the ceiling; on a 429
 * we honour Retry-After and retry the same item.
 */
export async function saveAll(
  token: string,
  inputs: SaveInput[],
  onProgress?: (done: number, total: number) => void,
): Promise<SaveResult> {
  const result: SaveResult = { saved: 0, failed: [] };

  for (let i = 0; i < inputs.length; i++) {
    let attempts = 0;
    while (true) {
      try {
        await saveMediaListEntry(token, inputs[i]);
        result.saved += 1;
        break;
      } catch (e) {
        if (e instanceof AniListError && e.status === 429 && attempts < 6) {
          attempts += 1;
          await sleep(((e.retryAfter ?? 60) + 1) * 1000);
          continue;
        }
        result.failed.push({
          mediaId: inputs[i].mediaId,
          message: e instanceof Error ? e.message : "Failed to save",
          status: e instanceof AniListError ? e.status : undefined,
        });
        break;
      }
    }
    onProgress?.(i + 1, inputs.length);

    if (i < inputs.length - 1) {
      const left = rateLimitRemaining();
      // Plenty of headroom: go fast. Getting close: ease off.
      const gap = left > 45 ? 180 : left > 20 ? 500 : left > 5 ? 1200 : 2500;
      await sleep(gap);
    }
  }

  return result;
}

export { AniListError };
