export type MediaType = "ANIME" | "MANGA";

export type MediaListStatus =
  | "CURRENT"
  | "PLANNING"
  | "COMPLETED"
  | "DROPPED"
  | "PAUSED"
  | "REPEATING";

export type ScoreFormat =
  | "POINT_100"
  | "POINT_10_DECIMAL"
  | "POINT_10"
  | "POINT_5"
  | "POINT_3";

export type MediaSeason = "WINTER" | "SPRING" | "SUMMER" | "FALL";

export interface MediaTitle {
  romaji: string | null;
  english: string | null;
  native: string | null;
  userPreferred: string | null;
}

export interface CoverImage {
  extraLarge: string | null;
  large: string | null;
  medium: string | null;
  color: string | null;
}

export interface FuzzyDate {
  year: number | null;
  month: number | null;
  day: number | null;
}

export interface Viewer {
  id: number;
  name: string;
  about: string | null;
  avatar: { large: string | null; medium: string | null } | null;
  bannerImage: string | null;
  siteUrl: string | null;
  unreadNotificationCount: number | null;
  options: {
    titleLanguage: string | null;
    displayAdultContent: boolean | null;
    profileColor: string | null;
  } | null;
  mediaListOptions: { scoreFormat: ScoreFormat } | null;
}

export interface Studio {
  id: number;
  name: string;
  siteUrl?: string | null;
  isAnimationStudio?: boolean | null;
}

export interface NextAiringEpisode {
  id?: number;
  airingAt: number;
  timeUntilAiring: number;
  episode: number;
}

/** The viewer's own list entry as returned nested inside a Media. */
export interface MediaListEntryLite {
  id: number | null;
  status: MediaListStatus | null;
  score: number | null;
  progress: number | null;
  progressVolumes?: number | null;
  repeat?: number | null;
  notes?: string | null;
  private?: boolean | null;
  hiddenFromStatusLists?: boolean | null;
  startedAt?: FuzzyDate | null;
  completedAt?: FuzzyDate | null;
}

export interface Media {
  id: number;
  type: MediaType;
  format: string | null;
  status: string | null;
  episodes: number | null;
  chapters: number | null;
  volumes?: number | null;
  duration: number | null;
  averageScore: number | null;
  meanScore?: number | null;
  popularity?: number | null;
  favourites?: number | null;
  genres: string[];
  synonyms?: string[];
  season: string | null;
  seasonYear: number | null;
  startDate: FuzzyDate | null;
  endDate: FuzzyDate | null;
  siteUrl: string | null;
  title: MediaTitle;
  coverImage: CoverImage | null;
  bannerImage: string | null;
  description?: string | null;
  isAdult?: boolean | null;
  countryOfOrigin?: string | null;
  source?: string | null;
  hashtag?: string | null;
  nextAiringEpisode?: NextAiringEpisode | null;
  studios?: { nodes: Studio[] } | null;
  mediaListEntry?: MediaListEntryLite | null;
  relations?: { edges: RelationEdge[] } | null;
}

/** A media node as it appears inside a relation edge (a lighter Media). */
export type RelatedMedia = Media;

export interface RelationEdge {
  relationType: string | null;
  node: RelatedMedia;
}

export interface MediaTag {
  id: number;
  name: string;
  description?: string | null;
  category?: string | null;
  rank: number | null;
  isMediaSpoiler: boolean | null;
  isAdult?: boolean | null;
}

export interface CharacterEdge {
  id: number | null;
  role: string | null;
  node: {
    id: number;
    name: { full: string | null; native: string | null } | null;
    image: { large: string | null; medium: string | null } | null;
    siteUrl: string | null;
  };
  voiceActors?: {
    id: number;
    name: { full: string | null } | null;
    image: { large: string | null } | null;
    languageV2: string | null;
    siteUrl: string | null;
  }[];
}

export interface StaffEdge {
  id: number | null;
  role: string | null;
  node: {
    id: number;
    name: { full: string | null } | null;
    image: { large: string | null } | null;
    siteUrl: string | null;
  };
}

export interface ExternalLink {
  id: number;
  site: string;
  url: string | null;
  type: string | null;
  color: string | null;
  icon: string | null;
  language: string | null;
}

export interface StreamingEpisode {
  title: string | null;
  thumbnail: string | null;
  url: string | null;
  site: string | null;
}

export interface MediaRanking {
  id: number;
  rank: number;
  type: string;
  format: string | null;
  year: number | null;
  season: string | null;
  allTime: boolean | null;
  context: string | null;
}

export interface MediaStats {
  scoreDistribution: { score: number; amount: number }[] | null;
  statusDistribution: { status: MediaListStatus; amount: number }[] | null;
}

/** Everything the detail modal shows, on top of the base Media fields. */
export interface MediaDetail extends Media {
  tags: MediaTag[] | null;
  characters: { edges: CharacterEdge[] } | null;
  staff: { edges: StaffEdge[] } | null;
  recommendations: {
    nodes: {
      id: number;
      rating: number | null;
      mediaRecommendation: Media | null;
    }[];
  } | null;
  externalLinks: ExternalLink[] | null;
  streamingEpisodes: StreamingEpisode[] | null;
  trailer: {
    id: string | null;
    site: string | null;
    thumbnail: string | null;
  } | null;
  rankings: MediaRanking[] | null;
  stats: MediaStats | null;
  isFavourite: boolean | null;
  trending?: number | null;
}

export interface MediaListEntry {
  id: number;
  status: MediaListStatus;
  score: number;
  progress: number;
  progressVolumes: number | null;
  repeat: number;
  notes: string | null;
  private: boolean | null;
  hiddenFromStatusLists: boolean | null;
  startedAt: FuzzyDate | null;
  completedAt: FuzzyDate | null;
  updatedAt: number | null;
  createdAt: number | null;
  media: Media;
}

/**
 * One card in a franchise row — either a real list entry, or a related
 * title discovered via relations that the user has not added to their list
 * yet (`status: null`, `onList: false`).
 */
export interface ListSlot {
  media: Media;
  entryId: number | null;
  status: MediaListStatus | null;
  score: number;
  progress: number;
  progressVolumes: number;
  repeat: number;
  notes: string;
  private: boolean;
  hiddenFromStatusLists: boolean;
  startedAt: FuzzyDate | null;
  completedAt: FuzzyDate | null;
  updatedAt: number | null;
  onList: boolean;
}

export interface MediaListGroup {
  name: string;
  status: MediaListStatus | null;
  isCustomList: boolean | null;
  entries: MediaListEntry[];
}

export interface MediaListCollection {
  lists: MediaListGroup[];
}

// --- Paging -----------------------------------------------------------

export interface PageInfo {
  total: number | null;
  perPage: number | null;
  currentPage: number | null;
  lastPage: number | null;
  hasNextPage: boolean | null;
}

export interface MediaPage {
  pageInfo: PageInfo;
  media: Media[];
}

// --- Airing schedule --------------------------------------------------

export interface AiringSchedule {
  id: number;
  airingAt: number;
  timeUntilAiring: number;
  episode: number;
  media: Media;
}

// --- Statistics -------------------------------------------------------

export interface StatBucket {
  count: number;
  meanScore: number | null;
  minutesWatched?: number | null;
  chaptersRead?: number | null;
}

export interface GenreStat extends StatBucket {
  genre: string;
}
export interface TagStat extends StatBucket {
  tag: { id: number; name: string } | null;
}
export interface StudioStat extends StatBucket {
  studio: { id: number; name: string } | null;
}
export interface FormatStat extends StatBucket {
  format: string | null;
}
export interface StatusStat extends StatBucket {
  status: MediaListStatus | null;
}
export interface ScoreStat extends StatBucket {
  score: number | null;
}
export interface YearStat extends StatBucket {
  releaseYear: number | null;
}
export interface CountryStat extends StatBucket {
  country: string | null;
}
export interface VoiceActorStat extends StatBucket {
  voiceActor: { id: number; name: { full: string | null } | null } | null;
}
export interface StaffStat extends StatBucket {
  staff: { id: number; name: { full: string | null } | null } | null;
}

export interface UserStatistics {
  count: number;
  meanScore: number | null;
  standardDeviation: number | null;
  minutesWatched: number | null;
  episodesWatched: number | null;
  chaptersRead: number | null;
  volumesRead: number | null;
  genres: GenreStat[] | null;
  tags: TagStat[] | null;
  formats: FormatStat[] | null;
  statuses: StatusStat[] | null;
  scores: ScoreStat[] | null;
  releaseYears: YearStat[] | null;
  countries: CountryStat[] | null;
  studios: StudioStat[] | null;
  voiceActors: VoiceActorStat[] | null;
  staff: StaffStat[] | null;
}

export interface UserStatisticTypes {
  anime: UserStatistics | null;
  manga: UserStatistics | null;
}

// --- Favourites -------------------------------------------------------

export interface FavouriteCharacter {
  id: number;
  name: { full: string | null; native: string | null } | null;
  image: { large: string | null } | null;
  siteUrl: string | null;
  favourites: number | null;
}

export interface FavouriteStaff {
  id: number;
  name: { full: string | null } | null;
  image: { large: string | null } | null;
  siteUrl: string | null;
  primaryOccupations: string[] | null;
  favourites: number | null;
}

export interface FavouriteStudio {
  id: number;
  name: string;
  siteUrl: string | null;
  favourites: number | null;
  media: { nodes: Media[] } | null;
}

export interface Favourites {
  anime: { nodes: Media[] } | null;
  manga: { nodes: Media[] } | null;
  characters: { nodes: FavouriteCharacter[] } | null;
  staff: { nodes: FavouriteStaff[] } | null;
  studios: { nodes: FavouriteStudio[] } | null;
}

// --- Activity feed ----------------------------------------------------

export interface ActivityUser {
  id: number;
  name: string;
  avatar: { large: string | null } | null;
  siteUrl: string | null;
}

export interface Activity {
  id: number;
  type: string | null;
  createdAt: number;
  siteUrl: string | null;
  likeCount: number | null;
  replyCount: number | null;
  isLiked: boolean | null;
  /** ListActivity only */
  status?: string | null;
  progress?: string | null;
  media?: Media | null;
  /** TextActivity / MessageActivity only */
  text?: string | null;
  message?: string | null;
  user?: ActivityUser | null;
  messenger?: ActivityUser | null;
  recipient?: ActivityUser | null;
}

// --- Notifications ----------------------------------------------------

export interface AniNotification {
  id: number;
  type: string | null;
  createdAt: number;
  episode?: number | null;
  contexts?: string[] | null;
  context?: string | null;
  reason?: string | null;
  media?: Media | null;
  user?: ActivityUser | null;
}

// --- Reviews ----------------------------------------------------------

export interface Review {
  id: number;
  summary: string | null;
  score: number | null;
  rating: number | null;
  ratingAmount: number | null;
  createdAt: number;
  siteUrl: string | null;
  user: ActivityUser | null;
  media: Media | null;
}
