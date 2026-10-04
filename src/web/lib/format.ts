import type {
  FuzzyDate,
  MediaListStatus,
  MediaType,
  MediaTitle,
  NextAiringEpisode,
  ScoreFormat,
} from "./types";

interface StatusMeta {
  color: string;
  label: (type: MediaType) => string;
}

/**
 * "Deep Ocean Blue" — the nine flat stops the whole app draws from,
 * ordered crimson -> teal. Every colour used anywhere comes from here as a
 * solid fill; the app has no gradients.
 */
export const PALETTE = [
  "#5a4a4e", // crimson
  "#6f5a5f", // raspberry
  "#8a6f74", // berry
  "#c77dba", // plum
  "#a98bd9", // violet
  "#8b96a8", // indigo
  "#e6a23c", // steel
  "#f2884b", // ocean
  "#ff4f6d", // teal
] as const;

/** Teal lifted a little, for primary interactive surfaces. */
export const ACCENT = "#ff6a4d";

// Statuses spread across the ramp: the red end reads as abandoned, the
// teal end as active/finished.
export const STATUS_META: Record<MediaListStatus, StatusMeta> = {
  CURRENT: {
    color: "#ff6a4d",
    label: (t) => (t === "ANIME" ? "Watching" : "Reading"),
  },
  COMPLETED: {
    color: "#e6a23c",
    label: () => "Completed",
  },
  PLANNING: {
    color: "#8b96a8",
    label: () => "Planning",
  },
  REPEATING: {
    color: "#a98bd9",
    label: (t) => (t === "ANIME" ? "Rewatching" : "Rereading"),
  },
  PAUSED: {
    color: "#8a6f74",
    label: () => "Paused",
  },
  DROPPED: {
    color: "#5a4a4e",
    label: () => "Dropped",
  },
};

// Order used when grouping/sorting the lists, matching AniList's list view.
export const STATUS_ORDER: MediaListStatus[] = [
  "CURRENT",
  "REPEATING",
  "COMPLETED",
  "PAUSED",
  "DROPPED",
  "PLANNING",
];

export function statusColor(
  status: MediaListStatus | null | undefined,
): string {
  if (!status) return "#55474a";
  return STATUS_META[status]?.color ?? "#55474a";
}

export function statusLabel(
  status: MediaListStatus | null | undefined,
  type: MediaType,
): string {
  if (!status) return "";
  return STATUS_META[status]?.label(type) ?? status;
}

export function preferredTitle(title: MediaTitle | null | undefined): string {
  if (!title) return "Unknown";
  return (
    title.userPreferred ||
    title.english ||
    title.romaji ||
    title.native ||
    "Unknown"
  );
}

/**
 * Format a raw AniList score into the user's chosen display format.
 * AniList stores/returns the score already in the user's format, so this
 * mostly decides how to render it (stars, /10, faces, etc.).
 */
export function formatScore(
  score: number,
  format: ScoreFormat | null | undefined,
): string | null {
  if (!score || score <= 0) return null;

  switch (format) {
    case "POINT_5":
      return "★".repeat(Math.max(1, Math.round(score)));
    case "POINT_3":
      // 1 = :(  2 = :|  3 = :)
      return score >= 3 ? "🙂" : score === 2 ? "😐" : "🙁";
    case "POINT_10":
      return `${Math.round(score)}`;
    case "POINT_10_DECIMAL":
      return `${score.toFixed(1)}`;
    case "POINT_100":
    default:
      return `${Math.round(score)}`;
  }
}

/** Min/max/step for a score <input> given the user's score format. */
export function scoreInputProps(format: ScoreFormat | null | undefined): {
  min: number;
  max: number;
  step: number;
} {
  switch (format) {
    case "POINT_5":
      return { min: 0, max: 5, step: 1 };
    case "POINT_3":
      return { min: 0, max: 3, step: 1 };
    case "POINT_10":
      return { min: 0, max: 10, step: 1 };
    case "POINT_10_DECIMAL":
      return { min: 0, max: 10, step: 0.5 };
    case "POINT_100":
    default:
      return { min: 0, max: 100, step: 1 };
  }
}

/** "TV" / "Movie" / "OVA" … from a raw media format enum. */
export function prettyFormat(format: string | null | undefined): string {
  if (!format) return "";
  const upper = ["TV", "OVA", "ONA"];
  if (upper.includes(format)) return format;
  if (format === "TV_SHORT") return "TV Short";
  return format
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/** Generic ENUM_CASE -> "Enum Case". */
export function titleCaseEnum(value: string | null | undefined): string {
  if (!value) return "";
  return value
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

const MEDIA_STATUS_META: Record<string, { label: string; color: string }> = {
  RELEASING: { label: "Releasing", color: "#ff6a4d" },
  FINISHED: { label: "Finished", color: "#6e5d61" },
  NOT_YET_RELEASED: { label: "Not Yet Released", color: "#a98bd9" },
  HIATUS: { label: "Hiatus", color: "#8a6f74" },
  CANCELLED: { label: "Cancelled", color: "#5a4a4e" },
};

/** "Releasing" / "Finished" / "Not Yet Released" … from AniList's airing status enum. */
export function mediaStatusLabel(status: string | null | undefined): string {
  if (!status) return "";
  return MEDIA_STATUS_META[status]?.label ?? titleCaseEnum(status);
}

/** Color for the airing-status indicator (distinct palette from list-status dots). */
export function mediaStatusColor(status: string | null | undefined): string {
  if (!status) return "#a59a9d";
  return MEDIA_STATUS_META[status]?.color ?? "#a59a9d";
}

const SHORT_MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

export function formatFuzzyDate(
  d: FuzzyDate | null | undefined,
): string | null {
  if (!d?.year) return null;
  if (d.month && d.day)
    return `${SHORT_MONTHS[d.month - 1]} ${d.day}, ${d.year}`;
  return d.month ? `${SHORT_MONTHS[d.month - 1]} ${d.year}` : `${d.year}`;
}

function shortFuzzy(d: FuzzyDate | null | undefined): string | null {
  if (!d?.year) return null;
  return d.month ? `${SHORT_MONTHS[d.month - 1]} ${d.year}` : `${d.year}`;
}

/**
 * A human date range for a card, e.g. "Apr 2013 – Sep 2013", "Apr 2013 –
 * Present" while still airing, or "TBA" for an unannounced release.
 */
export function dateRangeLabel(
  startDate: FuzzyDate | null | undefined,
  endDate: FuzzyDate | null | undefined,
  airingStatus: string | null | undefined,
): string | null {
  const start = shortFuzzy(startDate);
  if (!start) return airingStatus === "NOT_YET_RELEASED" ? "TBA" : null;
  const end = shortFuzzy(endDate);
  if (end && end !== start) return `${start} – ${end}`;
  if (airingStatus === "RELEASING") return `${start} – Present`;
  return start;
}

// --- Fuzzy date <-> <input type="date"> -------------------------------

export function fuzzyToInputValue(d: FuzzyDate | null | undefined): string {
  if (!d?.year || !d.month || !d.day) return "";
  const mm = String(d.month).padStart(2, "0");
  const dd = String(d.day).padStart(2, "0");
  return `${d.year}-${mm}-${dd}`;
}

export function inputValueToFuzzy(value: string): FuzzyDate | null {
  if (!value) return null;
  const [y, m, d] = value.split("-").map(Number);
  if (!y || !m || !d) return null;
  return { year: y, month: m, day: d };
}

export function fuzzyEquals(
  a: FuzzyDate | null | undefined,
  b: FuzzyDate | null | undefined,
): boolean {
  const norm = (d: FuzzyDate | null | undefined) =>
    d?.year ? `${d.year}-${d.month ?? 0}-${d.day ?? 0}` : "";
  return norm(a) === norm(b);
}

export function todayFuzzy(): FuzzyDate {
  const now = new Date();
  return {
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    day: now.getDate(),
  };
}

// --- Seasons ----------------------------------------------------------

export const SEASONS = ["WINTER", "SPRING", "SUMMER", "FALL"] as const;

/** AniList's season for a given date (Dec–Feb = Winter, etc.). */
export function currentSeason(date = new Date()): {
  season: string;
  year: number;
} {
  const m = date.getMonth(); // 0-based
  const y = date.getFullYear();
  if (m === 11) return { season: "WINTER", year: y + 1 }; // Dec rolls forward
  if (m <= 1) return { season: "WINTER", year: y };
  if (m <= 4) return { season: "SPRING", year: y };
  if (m <= 7) return { season: "SUMMER", year: y };
  return { season: "FALL", year: y };
}

export function nextSeason(
  season: string,
  year: number,
): {
  season: string;
  year: number;
} {
  const i = SEASONS.indexOf(season as (typeof SEASONS)[number]);
  const next = (i + 1) % 4;
  return { season: SEASONS[next], year: next === 0 ? year + 1 : year };
}

export function seasonLabel(
  season: string | null | undefined,
  year: number | null | undefined,
): string {
  if (!season && !year) return "";
  if (!season) return `${year}`;
  return `${titleCaseEnum(season)}${year ? ` ${year}` : ""}`;
}

// --- Time -------------------------------------------------------------

/** "2d 4h" / "3h 12m" / "48m" from a duration in seconds. */
export function durationShort(seconds: number): string {
  if (seconds <= 0) return "now";
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

/** "Ep 12 in 2d 4h" for a releasing show, or null. */
export function airingCountdown(
  next: NextAiringEpisode | null | undefined,
): string | null {
  if (!next) return null;
  return `Ep ${next.episode} in ${durationShort(next.timeUntilAiring)}`;
}

/** "3h ago" / "2d ago" / "Jan 4, 2024" from a unix timestamp. */
export function relativeTime(unix: number | null | undefined): string {
  if (!unix) return "";
  const diff = Math.floor(Date.now() / 1000) - unix;
  if (diff < 60) return "just now";
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  if (diff < 604800) return `${Math.floor(diff / 86400)}d ago`;
  if (diff < 2592000) return `${Math.floor(diff / 604800)}w ago`;
  const d = new Date(unix * 1000);
  return `${SHORT_MONTHS[d.getMonth()]} ${d.getDate()}, ${d.getFullYear()}`;
}

/** Local clock time for an airing slot, e.g. "19:30". */
export function clockTime(unix: number): string {
  const d = new Date(unix * 1000);
  return `${String(d.getHours()).padStart(2, "0")}:${String(
    d.getMinutes(),
  ).padStart(2, "0")}`;
}

/** "12 days, 4 hours" from a minute count (AniList's minutesWatched). */
export function humanMinutes(minutes: number | null | undefined): string {
  if (!minutes) return "0 minutes";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days > 0)
    return `${days} day${days === 1 ? "" : "s"}, ${hours} hour${
      hours === 1 ? "" : "s"
    }`;
  const mins = minutes % 60;
  if (hours > 0)
    return `${hours} hour${hours === 1 ? "" : "s"}, ${mins} minute${
      mins === 1 ? "" : "s"
    }`;
  return `${minutes} minute${minutes === 1 ? "" : "s"}`;
}

// --- Numbers / text ---------------------------------------------------

/** 12345 -> "12.3K", 1234567 -> "1.2M". */
export function compactNumber(n: number | null | undefined): string {
  if (n == null) return "—";
  if (Math.abs(n) < 1000) return `${n}`;
  if (Math.abs(n) < 1_000_000) return `${(n / 1000).toFixed(1)}K`;
  return `${(n / 1_000_000).toFixed(1)}M`;
}

/** AniList descriptions contain a little HTML; render them as plain text. */
export function stripHtml(html: string | null | undefined): string {
  if (!html) return "";
  return html
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/p>/gi, "\n\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

/** Relative luminance (WCAG) of a #rrggbb hex color. */
function luminance(hex: string): number {
  const c = hex.replace("#", "");
  const r = parseInt(c.substring(0, 2), 16) / 255;
  const g = parseInt(c.substring(2, 4), 16) / 255;
  const b = parseInt(c.substring(4, 6), 16) / 255;
  const lin = (v: number) =>
    v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

/** Dark or light text color that stays readable on a given background hex. */
export function contrastText(hex: string): string {
  if (!hex || !hex.startsWith("#") || hex.length < 7) return "#ffffff";
  return luminance(hex) > 0.42 ? "#1a0507" : "#ffffff";
}

/** "#rrggbb" + alpha (0–1) -> "rgba(...)", for fades built from a hex token. */
export function withAlpha(hex: string, alpha: number): string {
  const c = (hex || "").replace("#", "");
  if (c.length < 6) return `rgba(255,255,255,${alpha})`;
  const r = parseInt(c.substring(0, 2), 16);
  const g = parseInt(c.substring(2, 4), 16);
  const b = parseInt(c.substring(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

/**
 * Snap an arbitrary colour to its nearest stop in the Deep Ocean palette.
 * AniList hands us the dominant colour of each cover, which can be any hue
 * at all; snapping keeps every title visually tied to its own artwork while
 * guaranteeing the UI only ever paints palette colours.
 */
export function snapToPalette(color: string | null | undefined): string | null {
  if (!color || !/^#[0-9a-f]{6}$/i.test(color)) return null;
  const c = color.replace("#", "");
  const r = parseInt(c.substring(0, 2), 16);
  const g = parseInt(c.substring(2, 4), 16);
  const b = parseInt(c.substring(4, 6), 16);

  let best = PALETTE[0] as string;
  let bestDist = Infinity;
  for (const stop of PALETTE) {
    const s = stop.replace("#", "");
    const dr = r - parseInt(s.substring(0, 2), 16);
    const dg = g - parseInt(s.substring(2, 4), 16);
    const db = b - parseInt(s.substring(4, 6), 16);
    // Weighted to roughly match perceived difference.
    const dist = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
    if (dist < bestDist) {
      bestDist = dist;
      best = stop;
    }
  }
  return best;
}

/** A stable palette stop for an id, so a title keeps the same colour. */
export function paletteFor(id: number): string {
  return PALETTE[Math.abs(id) % PALETTE.length];
}

/**
 * The flat palette colour a given title is drawn with: snapped from its
 * cover art when AniList knows it, otherwise derived from its id.
 */
export function coverAccent(color: string | null | undefined, id = 0): string {
  return snapToPalette(color) ?? paletteFor(id);
}

export function relationLabel(relationType: string | null | undefined): string {
  if (!relationType) return "Related";
  if (relationType === "SPIN_OFF") return "Spin-off";
  return titleCaseEnum(relationType);
}

/**
 * Score ramp, walked straight down the Deep Ocean palette: a low score sits
 * at the crimson end, a high one at the teal end.
 */
export function scoreColor(score: number | null | undefined): string {
  if (!score) return "#6e5d61";
  if (score >= 88) return "#ff4f6d";
  if (score >= 80) return "#f2884b";
  if (score >= 72) return "#e6a23c";
  if (score >= 64) return "#8b96a8";
  if (score >= 56) return "#a98bd9";
  if (score >= 48) return "#c77dba";
  if (score >= 40) return "#8a6f74";
  if (score >= 30) return "#6f5a5f";
  return "#5a4a4e";
}

export const DAY_NAMES = [
  "Sunday",
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
];
