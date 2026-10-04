import { preferredTitle } from "./format";
import type {
  ListSlot,
  Media,
  MediaListEntry,
  MediaListEntryLite,
  MediaType,
  RelatedMedia,
} from "./types";

// Relation types that keep media in the same "series" cluster: sequels,
// prequels, side stories, spin-offs, recap/compilation films, etc.
// (ADAPTATION / CHARACTER / SOURCE / OTHER are deliberately excluded so we
// do not over-merge unrelated titles.)
const SAME_FRANCHISE_RELATIONS = new Set([
  "SEQUEL",
  "PREQUEL",
  "SIDE_STORY",
  "SPIN_OFF",
  "PARENT",
  "ALTERNATIVE",
  "SUMMARY",
  "COMPILATION",
  "CONTAINS",
]);

export interface Franchise {
  id: number; // stable id (smallest media id in the group)
  label: string; // title of the earliest entry
  entries: ListSlot[];
}

function slotFromEntry(e: MediaListEntry): ListSlot {
  return {
    media: e.media,
    entryId: e.id,
    status: e.status,
    score: e.score ?? 0,
    progress: e.progress ?? 0,
    progressVolumes: e.progressVolumes ?? 0,
    repeat: e.repeat ?? 0,
    notes: e.notes ?? "",
    private: !!e.private,
    hiddenFromStatusLists: !!e.hiddenFromStatusLists,
    startedAt: e.startedAt ?? null,
    completedAt: e.completedAt ?? null,
    updatedAt: e.updatedAt,
    onList: true,
  };
}

/** Build a slot from any Media that carries the viewer's own mediaListEntry. */
export function slotFromMedia(media: Media): ListSlot {
  const le: MediaListEntryLite | null | undefined = media.mediaListEntry;
  return {
    media,
    entryId: le?.id ?? null,
    status: le?.status ?? null,
    score: le?.score ?? 0,
    progress: le?.progress ?? 0,
    progressVolumes: le?.progressVolumes ?? 0,
    repeat: le?.repeat ?? 0,
    notes: le?.notes ?? "",
    private: !!le?.private,
    hiddenFromStatusLists: !!le?.hiddenFromStatusLists,
    startedAt: le?.startedAt ?? null,
    completedAt: le?.completedAt ?? null,
    updatedAt: null,
    onList: !!le?.status,
  };
}

function slotFromRelated(node: RelatedMedia): ListSlot {
  return slotFromMedia(node);
}

/** A sortable number from a start date (falls back to season year). */
function startValue(slot: ListSlot): number {
  const d = slot.media.startDate;
  if (d?.year) return d.year * 10000 + (d.month ?? 0) * 100 + (d.day ?? 0);
  if (slot.media.seasonYear) return slot.media.seasonYear * 10000;
  return Number.MAX_SAFE_INTEGER; // unknown dates sort to the end
}

function maxUpdated(f: Franchise): number {
  return f.entries.reduce((m, e) => Math.max(m, e.updatedAt ?? 0), 0);
}

/** Highest real user score in the franchise (0/unscored entries excluded). */
function maxMyScore(f: Franchise): number {
  return f.entries.reduce(
    (m, e) => Math.max(m, e.score > 0 ? e.score : -1),
    -1,
  );
}

/** Highest community average score in the franchise (missing ones excluded). */
function maxAvgScore(f: Franchise): number {
  return f.entries.reduce(
    (m, e) => Math.max(m, e.media.averageScore ?? -1),
    -1,
  );
}

/** Highest popularity in the franchise. */
function maxPopularity(f: Franchise): number {
  return f.entries.reduce((m, e) => Math.max(m, e.media.popularity ?? -1), -1);
}

/** Earliest known release date in the franchise (unknown dates sort last). */
function earliestStart(f: Franchise): number {
  return f.entries.reduce(
    (m, e) => Math.min(m, startValue(e)),
    Number.MAX_SAFE_INTEGER,
  );
}

/** Latest known release date in the franchise. */
function latestStart(f: Franchise): number {
  return f.entries.reduce(
    (m, e) =>
      Math.max(
        m,
        startValue(e) === Number.MAX_SAFE_INTEGER ? 0 : startValue(e),
      ),
    0,
  );
}

/**
 * Cluster the user's list entries — plus any related seasons/spin-offs/movies
 * they have not added yet — into franchises using a union-find over relation
 * edges. Each cluster is ordered chronologically so seasons appear
 * season 1 -> latest, and unlisted titles show up alongside owned ones.
 */
export function buildFranchises(
  entries: MediaListEntry[],
  type: MediaType,
  options: { includeUnlisted?: boolean } = {},
): Franchise[] {
  const includeUnlisted = options.includeUnlisted !== false;
  const slots = new Map<number, ListSlot>();
  for (const e of entries) slots.set(e.media.id, slotFromEntry(e));

  // Discover related media the user has not added, matching the active tab.
  if (includeUnlisted) {
    for (const e of entries) {
      for (const edge of e.media.relations?.edges ?? []) {
        if (
          !edge.relationType ||
          !SAME_FRANCHISE_RELATIONS.has(edge.relationType)
        )
          continue;
        const node = edge.node;
        if (node.type !== type) continue; // keep Anime/Manga tabs separate
        if (!slots.has(node.id)) slots.set(node.id, slotFromRelated(node));
      }
    }
  }

  const parent = new Map<number, number>();
  for (const id of slots.keys()) parent.set(id, id);

  const find = (x: number): number => {
    let root = x;
    while (parent.get(root) !== root) root = parent.get(root)!;
    let cur = x;
    while (parent.get(cur) !== root) {
      const next = parent.get(cur)!;
      parent.set(cur, root);
      cur = next;
    }
    return root;
  };
  const union = (a: number, b: number) => {
    const ra = find(a);
    const rb = find(b);
    if (ra !== rb) parent.set(ra, rb);
  };

  for (const e of entries) {
    for (const edge of e.media.relations?.edges ?? []) {
      if (
        !edge.relationType ||
        !SAME_FRANCHISE_RELATIONS.has(edge.relationType)
      )
        continue;
      if (edge.node.type !== type) continue;
      if (slots.has(edge.node.id)) union(e.media.id, edge.node.id);
    }
  }

  const groups = new Map<number, ListSlot[]>();
  for (const slot of slots.values()) {
    const root = find(slot.media.id);
    const arr = groups.get(root) ?? [];
    arr.push(slot);
    groups.set(root, arr);
  }

  const franchises: Franchise[] = [];
  for (const arr of groups.values()) {
    arr.sort(
      (a, b) =>
        startValue(a) - startValue(b) ||
        preferredTitle(a.media.title).localeCompare(
          preferredTitle(b.media.title),
        ),
    );
    franchises.push({
      id: Math.min(...arr.map((x) => x.media.id)),
      label: preferredTitle(arr[0].media.title),
      entries: arr,
    });
  }

  // Default order; callers should apply sortFranchises() for the final order.
  franchises.sort((a, b) => maxUpdated(b) - maxUpdated(a));
  return franchises;
}

export type SortMode =
  | "UPDATED"
  | "NAME"
  | "MY_SCORE"
  | "AVG_SCORE"
  | "POPULARITY"
  | "RELEASE"
  | "RELEASE_DESC"
  | "SIZE"
  | "PROGRESS";

export const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "UPDATED", label: "Last Updated" },
  { value: "NAME", label: "Name (A–Z)" },
  { value: "MY_SCORE", label: "Your Score" },
  { value: "AVG_SCORE", label: "Average Score" },
  { value: "POPULARITY", label: "Popularity" },
  { value: "RELEASE", label: "Release Date (oldest)" },
  { value: "RELEASE_DESC", label: "Release Date (newest)" },
  { value: "SIZE", label: "Entries in Series" },
  { value: "PROGRESS", label: "Completion %" },
];

/** Share of the franchise you have actually finished, 0–1. */
export function franchiseCompletion(f: Franchise): number {
  let done = 0;
  let total = 0;
  for (const s of f.entries) {
    const cap = s.media.type === "ANIME" ? s.media.episodes : s.media.chapters;
    if (!cap) continue;
    total += cap;
    done += Math.min(s.progress, cap);
  }
  if (total === 0) return 0;
  return done / total;
}

/** Order franchise rows by the chosen criterion (ties break alphabetically). */
export function sortFranchises(
  franchises: Franchise[],
  mode: SortMode,
): Franchise[] {
  const arr = [...franchises];
  const byName = (a: Franchise, b: Franchise) => a.label.localeCompare(b.label);

  switch (mode) {
    case "NAME":
      arr.sort(byName);
      break;
    case "MY_SCORE":
      arr.sort((a, b) => maxMyScore(b) - maxMyScore(a) || byName(a, b));
      break;
    case "AVG_SCORE":
      arr.sort((a, b) => maxAvgScore(b) - maxAvgScore(a) || byName(a, b));
      break;
    case "POPULARITY":
      arr.sort((a, b) => maxPopularity(b) - maxPopularity(a) || byName(a, b));
      break;
    case "RELEASE":
      arr.sort((a, b) => earliestStart(a) - earliestStart(b) || byName(a, b));
      break;
    case "RELEASE_DESC":
      arr.sort((a, b) => latestStart(b) - latestStart(a) || byName(a, b));
      break;
    case "SIZE":
      arr.sort((a, b) => b.entries.length - a.entries.length || byName(a, b));
      break;
    case "PROGRESS":
      arr.sort(
        (a, b) =>
          franchiseCompletion(b) - franchiseCompletion(a) || byName(a, b),
      );
      break;
    case "UPDATED":
    default:
      arr.sort((a, b) => maxUpdated(b) - maxUpdated(a) || byName(a, b));
      break;
  }
  return arr;
}
