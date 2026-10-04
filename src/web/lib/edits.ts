import { fuzzyEquals } from "./format";
import type { FuzzyDate, ListSlot, MediaListStatus } from "./types";

/**
 * A pending, unsaved change to one list entry. Only fields the user actually
 * touched are present, so a patch is always a minimal diff against the slot.
 */
export interface EntryPatch {
  status?: MediaListStatus;
  score?: number;
  progress?: number;
  progressVolumes?: number;
  repeat?: number;
  notes?: string;
  private?: boolean;
  hiddenFromStatusLists?: boolean;
  startedAt?: FuzzyDate | null;
  completedAt?: FuzzyDate | null;
}

/** The slot's value for a field, with any pending patch applied on top. */
export function effective(
  slot: ListSlot,
  patch: EntryPatch | undefined,
): ListSlot {
  if (!patch) return slot;
  return {
    ...slot,
    status: patch.status ?? slot.status,
    score: patch.score ?? slot.score,
    progress: patch.progress ?? slot.progress,
    progressVolumes: patch.progressVolumes ?? slot.progressVolumes,
    repeat: patch.repeat ?? slot.repeat,
    notes: patch.notes ?? slot.notes,
    private: patch.private ?? slot.private,
    hiddenFromStatusLists:
      patch.hiddenFromStatusLists ?? slot.hiddenFromStatusLists,
    startedAt: patch.startedAt !== undefined ? patch.startedAt : slot.startedAt,
    completedAt:
      patch.completedAt !== undefined ? patch.completedAt : slot.completedAt,
  };
}

/**
 * Drop every field of `patch` that already matches the saved slot, so a user
 * who nudges a value and puts it back does not leave a phantom "1 change".
 */
export function normalizePatch(
  slot: ListSlot,
  patch: EntryPatch,
): EntryPatch | null {
  const out: EntryPatch = {};
  if (patch.status !== undefined && patch.status !== slot.status)
    out.status = patch.status;
  if (patch.score !== undefined && patch.score !== slot.score)
    out.score = patch.score;
  if (patch.progress !== undefined && patch.progress !== slot.progress)
    out.progress = patch.progress;
  if (
    patch.progressVolumes !== undefined &&
    patch.progressVolumes !== slot.progressVolumes
  )
    out.progressVolumes = patch.progressVolumes;
  if (patch.repeat !== undefined && patch.repeat !== slot.repeat)
    out.repeat = patch.repeat;
  if (patch.notes !== undefined && patch.notes !== slot.notes)
    out.notes = patch.notes;
  if (patch.private !== undefined && patch.private !== slot.private)
    out.private = patch.private;
  if (
    patch.hiddenFromStatusLists !== undefined &&
    patch.hiddenFromStatusLists !== slot.hiddenFromStatusLists
  )
    out.hiddenFromStatusLists = patch.hiddenFromStatusLists;
  if (
    patch.startedAt !== undefined &&
    !fuzzyEquals(patch.startedAt, slot.startedAt)
  )
    out.startedAt = patch.startedAt;
  if (
    patch.completedAt !== undefined &&
    !fuzzyEquals(patch.completedAt, slot.completedAt)
  )
    out.completedAt = patch.completedAt;

  return Object.keys(out).length === 0 ? null : out;
}

export function patchDiffers(
  slot: ListSlot,
  patch: EntryPatch | undefined,
): boolean {
  if (!patch) return false;
  return normalizePatch(slot, patch) !== null;
}

/**
 * AniList's own "smart" behaviour when you edit an entry: finishing the last
 * episode completes it, starting from zero sets the start date, and so on.
 * Returns extra fields to merge into the patch.
 */
export function derivedFields(
  slot: ListSlot,
  patch: EntryPatch,
  today: FuzzyDate,
): EntryPatch {
  const extra: EntryPatch = {};
  const total =
    slot.media.type === "ANIME" ? slot.media.episodes : slot.media.chapters;
  const nextProgress = patch.progress ?? slot.progress;
  const nextStatus = patch.status ?? slot.status;

  // First progress on a fresh entry -> start watching today.
  if (
    patch.progress !== undefined &&
    slot.progress === 0 &&
    nextProgress > 0 &&
    (nextStatus === null || nextStatus === "PLANNING")
  ) {
    extra.status = "CURRENT";
    if (!slot.startedAt?.year) extra.startedAt = today;
  }

  // Reached the final episode/chapter -> complete it.
  if (
    patch.progress !== undefined &&
    total != null &&
    nextProgress >= total &&
    nextStatus !== "COMPLETED" &&
    nextStatus !== "REPEATING"
  ) {
    extra.status = "COMPLETED";
    if (!slot.completedAt?.year) extra.completedAt = today;
  }

  // Manually marking complete fills in progress + the finish date.
  if (patch.status === "COMPLETED") {
    if (total != null && slot.progress < total) extra.progress = total;
    if (!slot.completedAt?.year) extra.completedAt = today;
  }

  // Starting something for the first time stamps the start date.
  if (
    (patch.status === "CURRENT" || patch.status === "REPEATING") &&
    !slot.startedAt?.year
  ) {
    extra.startedAt = today;
  }

  return extra;
}
