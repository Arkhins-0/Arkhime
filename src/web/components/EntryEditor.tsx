"use client";

import { useMemo, useState } from "react";
import ScrollNumberInput from "./ScrollNumberInput";
import { GhostButton, NeonButton } from "./ui";
import { IconTrash, IconEyeOff, IconMinus, IconPlus } from "./Icons";
import {
  contrastText,
  fuzzyToInputValue,
  inputValueToFuzzy,
  scoreInputProps,
  statusColor,
  STATUS_META,
  todayFuzzy,
} from "@/web/lib/format";
import type { SaveInput } from "@/web/lib/anilist";
import type {
  FuzzyDate,
  ListSlot,
  MediaListStatus,
  ScoreFormat,
} from "@/web/lib/types";

const STATUS_OPTIONS: MediaListStatus[] = [
  "CURRENT",
  "PLANNING",
  "COMPLETED",
  "PAUSED",
  "DROPPED",
  "REPEATING",
];

interface Draft {
  status: MediaListStatus;
  score: number;
  progress: number;
  progressVolumes: number;
  repeat: number;
  notes: string;
  private: boolean;
  hiddenFromStatusLists: boolean;
  startedAt: FuzzyDate | null;
  completedAt: FuzzyDate | null;
}

function draftFrom(slot: ListSlot): Draft {
  return {
    status: slot.status ?? "PLANNING",
    score: slot.score,
    progress: slot.progress,
    progressVolumes: slot.progressVolumes,
    repeat: slot.repeat,
    notes: slot.notes,
    private: slot.private,
    hiddenFromStatusLists: slot.hiddenFromStatusLists,
    startedAt: slot.startedAt,
    completedAt: slot.completedAt,
  };
}

/**
 * The full AniList list-entry editor: status, score, progress (+ volumes for
 * manga), repeat count, start/finish dates, notes, private and
 * hidden-from-status-lists flags. Saves this one entry immediately — the
 * batched "pending edits" flow is for the quick inline controls on cards.
 */
export default function EntryEditor({
  slot,
  scoreFormat,
  saving,
  onSave,
  onDelete,
}: {
  slot: ListSlot;
  scoreFormat: ScoreFormat | null | undefined;
  saving: boolean;
  onSave: (input: SaveInput) => void;
  onDelete: (() => void) | null;
}) {
  const [draft, setDraft] = useState<Draft>(() => draftFrom(slot));
  const [confirmDelete, setConfirmDelete] = useState(false);

  // Re-seed when the modal switches to a different title.
  const [seededFrom, setSeededFrom] = useState(slot);
  if (seededFrom !== slot) {
    setSeededFrom(slot);
    setDraft(draftFrom(slot));
    setConfirmDelete(false);
  }

  const media = slot.media;
  const isAnime = media.type === "ANIME";
  const total = isAnime ? media.episodes : media.chapters;
  const totalVolumes = media.volumes ?? null;
  const sp = scoreInputProps(scoreFormat);

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((d) => ({ ...d, [key]: value }));

  /** Mirror AniList's conveniences: completing fills progress + finish date. */
  const setStatus = (status: MediaListStatus) => {
    setDraft((d) => {
      const next: Draft = { ...d, status };
      const today = todayFuzzy();
      if (status === "COMPLETED") {
        if (total != null) next.progress = total;
        if (totalVolumes != null && !isAnime)
          next.progressVolumes = totalVolumes;
        if (!next.completedAt?.year) next.completedAt = today;
      }
      if (
        (status === "CURRENT" || status === "REPEATING") &&
        !next.startedAt?.year
      ) {
        next.startedAt = today;
      }
      return next;
    });
  };

  const setProgress = (value: number) => {
    setDraft((d) => {
      const next: Draft = { ...d, progress: value };
      const today = todayFuzzy();
      if (value > 0 && (d.status === "PLANNING" || !slot.onList)) {
        next.status = "CURRENT";
        if (!next.startedAt?.year) next.startedAt = today;
      }
      if (total != null && value >= total && d.status !== "REPEATING") {
        next.status = "COMPLETED";
        if (!next.completedAt?.year) next.completedAt = today;
      }
      return next;
    });
  };

  const dirty = useMemo(() => {
    const base = draftFrom(slot);
    return JSON.stringify(base) !== JSON.stringify(draft) || !slot.onList;
  }, [draft, slot]);

  const submit = () => {
    onSave({
      mediaId: media.id,
      status: draft.status,
      score: draft.score,
      progress: draft.progress,
      progressVolumes: isAnime ? undefined : draft.progressVolumes,
      repeat: draft.repeat,
      notes: draft.notes,
      private: draft.private,
      hiddenFromStatusLists: draft.hiddenFromStatusLists,
      startedAt: draft.startedAt,
      completedAt: draft.completedAt,
    });
  };

  return (
    <div
      className="border border-bg-300 p-4"
      style={{
        backgroundColor: "#2a1d20",
      }}
    >
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="font-sans text-sm font-extrabold uppercase tracking-[0.15em] text-fg">
          {slot.onList ? "Edit entry" : "Add to your list"}
        </h3>
        {slot.private ? (
          <span className="inline-flex items-center gap-1 text-[10px] font-bold text-fg-light">
            <IconEyeOff size={12} /> Private
          </span>
        ) : null}
      </div>

      {/* Status */}
      <div className="mb-3 flex flex-wrap gap-1.5">
        {STATUS_OPTIONS.map((s) => {
          const active = draft.status === s;
          const c = statusColor(s);
          return (
            <button
              key={s}
              onClick={() => setStatus(s)}
              className={` px-3 py-1.5 text-[11px] font-extrabold transition ${
                active ? "" : "surface text-fg-light hover:text-fg"
              }`}
              style={
                active
                  ? {
                      backgroundColor: "#2a1d20",
                      color: contrastText(c),
                      border: `1px solid ${c}`,
                    }
                  : undefined
              }
            >
              {STATUS_META[s].label(media.type)}
            </button>
          );
        })}
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Field label={`Score (${sp.max === 100 ? "/100" : `/${sp.max}`})`}>
          <ScrollNumberInput
            value={draft.score}
            min={sp.min}
            max={sp.max}
            step={sp.step}
            emptyWhenZero
            placeholder="—"
            onChange={(v) => set("score", v)}
            className="w-full bg-transparent text-base font-extrabold tabular-nums text-[color:var(--accent)] outline-none placeholder:text-fg-light"
            ariaLabel="Score"
          />
        </Field>

        <Field label={isAnime ? "Episodes" : "Chapters"}>
          <Stepper
            value={draft.progress}
            min={0}
            max={total ?? 100000}
            total={total}
            onChange={setProgress}
          />
        </Field>

        {!isAnime ? (
          <Field label="Volumes">
            <Stepper
              value={draft.progressVolumes}
              min={0}
              max={totalVolumes ?? 10000}
              total={totalVolumes}
              onChange={(v) => set("progressVolumes", v)}
            />
          </Field>
        ) : null}

        <Field label={isAnime ? "Total rewatches" : "Total rereads"}>
          <Stepper
            value={draft.repeat}
            min={0}
            max={999}
            total={null}
            onChange={(v) => set("repeat", v)}
          />
        </Field>

        <Field label="Start date">
          <input
            type="date"
            value={fuzzyToInputValue(draft.startedAt)}
            onChange={(e) =>
              set("startedAt", inputValueToFuzzy(e.target.value))
            }
            className="w-full bg-transparent text-xs font-bold text-fg outline-none"
          />
        </Field>

        <Field label="Finish date">
          <input
            type="date"
            value={fuzzyToInputValue(draft.completedAt)}
            onChange={(e) =>
              set("completedAt", inputValueToFuzzy(e.target.value))
            }
            className="w-full bg-transparent text-xs font-bold text-fg outline-none"
          />
        </Field>
      </div>

      <div className="mt-3">
        <Field label="Notes">
          <textarea
            value={draft.notes}
            onChange={(e) => set("notes", e.target.value)}
            rows={2}
            placeholder="Your private notes on this title…"
            className="w-full resize-y bg-transparent text-xs font-medium text-fg outline-none placeholder:text-fg-light"
          />
        </Field>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Toggle
          label="Private"
          checked={draft.private}
          onChange={(v) => set("private", v)}
        />
        <Toggle
          label="Hidden from status lists"
          checked={draft.hiddenFromStatusLists}
          onChange={(v) => set("hiddenFromStatusLists", v)}
        />
      </div>

      <div className="mt-4 flex items-center gap-2">
        <NeonButton onClick={submit} disabled={saving || !dirty}>
          {saving ? "Saving…" : slot.onList ? "Save entry" : "Add to list"}
        </NeonButton>
        {dirty ? (
          <GhostButton
            onClick={() => setDraft(draftFrom(slot))}
            disabled={saving}
          >
            Reset
          </GhostButton>
        ) : null}

        {onDelete ? (
          <div className="ml-auto">
            {confirmDelete ? (
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-bold text-status-dropped">
                  Remove from your list?
                </span>
                <GhostButton danger onClick={onDelete} disabled={saving}>
                  Confirm
                </GhostButton>
                <GhostButton onClick={() => setConfirmDelete(false)}>
                  Cancel
                </GhostButton>
              </div>
            ) : (
              <GhostButton danger onClick={() => setConfirmDelete(true)}>
                <span className="inline-flex items-center gap-1.5">
                  <IconTrash size={13} /> Delete
                </span>
              </GhostButton>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="surface rounded block px-3 py-2">
      <span className="mb-0.5 block text-[9.5px] font-bold uppercase tracking-[0.16em] text-fg-light">
        {label}
      </span>
      {children}
    </label>
  );
}

function Stepper({
  value,
  min,
  max,
  total,
  onChange,
}: {
  value: number;
  min: number;
  max: number;
  total: number | null;
  onChange: (v: number) => void;
}) {
  return (
    <div className="flex items-center gap-1">
      <button
        type="button"
        onClick={() => onChange(Math.max(min, value - 1))}
        className="flex h-5 w-5 flex-shrink-0 items-center justify-center text-fg-light transition hover:bg-bg-300 hover:text-fg"
        aria-label="Decrease"
      >
        <IconMinus size={11} />
      </button>
      <ScrollNumberInput
        value={value}
        min={min}
        max={max}
        step={1}
        onChange={onChange}
        className="w-10 bg-transparent text-center text-base font-extrabold tabular-nums text-fg outline-none"
        ariaLabel="Value"
      />
      {total != null ? (
        <span className="text-[11px] font-bold text-fg-light">/{total}</span>
      ) : null}
      <button
        type="button"
        onClick={() => onChange(Math.min(max, value + 1))}
        className="ml-auto flex h-5 w-5 flex-shrink-0 items-center justify-center text-fg-light transition hover:bg-bg-300 hover:text-fg"
        aria-label="Increase"
      >
        <IconPlus size={11} />
      </button>
    </div>
  );
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className={`flex items-center gap-2 px-3 py-1.5 text-[11px] font-bold transition ${
        checked
          ? "border border-[color:var(--accent)]/60 text-[color:var(--accent)]"
          : "surface text-fg-light hover:text-fg"
      }`}
      style={
        checked
          ? {
              backgroundColor: "#2a1d20",
            }
          : undefined
      }
      aria-pressed={checked}
    >
      <span
        className={`h-3.5 w-6 transition ${
          checked ? "bg-[color:var(--accent)]" : "bg-bg-300"
        } relative`}
      >
        <span
          className={`absolute top-0.5 h-2.5 w-2.5 bg-[#1a0507] transition-all ${
            checked ? "left-3" : "left-0.5"
          }`}
        />
      </span>
      {label}
    </button>
  );
}
