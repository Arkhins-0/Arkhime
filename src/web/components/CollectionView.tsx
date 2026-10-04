"use client";

import { useMemo, useState } from "react";
import FranchiseRow from "./FranchiseRow";
import MediaCard from "./MediaCard";
import ScrollNumberInput from "./ScrollNumberInput";
import { EmptyState, Meter, SectionTitle, Select } from "./ui";
import { IconGrid, IconList, IconRows, IconSearch } from "./Icons";
import { effective, type EntryPatch } from "@/web/lib/edits";
import {
  sortFranchises,
  SORT_OPTIONS,
  type Franchise,
  type SortMode,
} from "@/web/lib/franchise";
import {
  mediaStatusLabel,
  preferredTitle,
  prettyFormat,
  scoreColor,
  scoreInputProps,
  statusColor,
  STATUS_META,
} from "@/web/lib/format";
import type {
  ListSlot,
  Media,
  MediaListStatus,
  MediaType,
  ScoreFormat,
} from "@/web/lib/types";

type Layout = "GRID" | "SERIES" | "COMPACT";

const LAYOUTS: { id: Layout; label: string; icon: React.ReactNode }[] = [
  { id: "GRID", label: "Card grid", icon: <IconGrid size={15} /> },
  { id: "SERIES", label: "Series rows", icon: <IconRows size={15} /> },
  { id: "COMPACT", label: "Compact list", icon: <IconList size={15} /> },
];

const STATUS_OPTIONS: MediaListStatus[] = [
  "CURRENT",
  "PLANNING",
  "COMPLETED",
  "PAUSED",
  "DROPPED",
  "REPEATING",
];

/** Re-project a slot (plus any pending edit) back into a Media for MediaCard. */
function slotAsMedia(slot: ListSlot, patch: EntryPatch | undefined): Media {
  const eff = effective(slot, patch);
  return {
    ...slot.media,
    mediaListEntry: eff.status
      ? {
          id: eff.entryId,
          status: eff.status,
          score: eff.score,
          progress: eff.progress,
          repeat: eff.repeat,
        }
      : null,
  };
}

/**
 * The collection, laid out like AniList's list page: a narrow filter rail
 * on the left and the entries on the right. The default card grid matches
 * AniList's, with progress and score captioned over the cover.
 */
export default function CollectionView({
  franchises,
  type,
  statusFilter,
  edits,
  scoreFormat,
  loading,
  totalEntries,
  isChanged,
  onPatch,
  onOpen,
}: {
  franchises: Franchise[];
  type: MediaType;
  statusFilter: MediaListStatus | "ALL";
  edits: Record<number, EntryPatch>;
  scoreFormat: ScoreFormat | null | undefined;
  loading: boolean;
  totalEntries: number;
  isChanged: (slot: ListSlot) => boolean;
  onPatch: (mediaId: number, patch: EntryPatch) => void;
  onOpen: (mediaId: number) => void;
}) {
  const [layout, setLayout] = useState<Layout>("SERIES");
  const [sortMode, setSortMode] = useState<SortMode>("UPDATED");
  const [search, setSearch] = useState("");
  const [genre, setGenre] = useState("");
  const [format, setFormat] = useState("");
  const [year, setYear] = useState("");
  const [airing, setAiring] = useState("");
  const [includeUnlisted, setIncludeUnlisted] = useState(true);
  const [onlyUnfinished, setOnlyUnfinished] = useState(false);

  const { genres, formats, years } = useMemo(() => {
    const g = new Set<string>();
    const f = new Set<string>();
    const y = new Set<number>();
    for (const fr of franchises)
      for (const s of fr.entries) {
        for (const x of s.media.genres ?? []) g.add(x);
        if (s.media.format) f.add(s.media.format);
        const yr = s.media.seasonYear ?? s.media.startDate?.year;
        if (yr) y.add(yr);
      }
    return {
      genres: [...g].sort(),
      formats: [...f].sort(),
      years: [...y].sort((a, b) => b - a),
    };
  }, [franchises]);

  const matchesSlot = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (s: ListSlot) => {
      if (!includeUnlisted && !s.onList) return false;
      if (statusFilter !== "ALL" && s.status !== statusFilter) return false;
      if (genre && !(s.media.genres ?? []).includes(genre)) return false;
      if (format && s.media.format !== format) return false;
      if (airing && s.media.status !== airing) return false;
      if (year) {
        const yr = s.media.seasonYear ?? s.media.startDate?.year;
        if (String(yr ?? "") !== year) return false;
      }
      if (onlyUnfinished) {
        const total =
          s.media.type === "ANIME" ? s.media.episodes : s.media.chapters;
        if (s.status === "COMPLETED") return false;
        if (total != null && s.progress >= total) return false;
      }
      if (q) {
        const t = s.media.title;
        const hit = [t?.english, t?.romaji, t?.native, t?.userPreferred]
          .filter(Boolean)
          .some((str) => str!.toLowerCase().includes(q));
        if (!hit) return false;
      }
      return true;
    };
  }, [
    search,
    statusFilter,
    genre,
    format,
    year,
    airing,
    includeUnlisted,
    onlyUnfinished,
  ]);

  /**
   * Filters apply per-card, not per-row: a row survives if any of its cards
   * does, and then only the surviving cards are shown.
   */
  const visible = useMemo(() => {
    const out: Franchise[] = [];
    for (const f of franchises) {
      const kept = f.entries.filter(matchesSlot);
      if (kept.length === 0) continue;
      out.push({ ...f, entries: kept });
    }
    return sortFranchises(out, sortMode);
  }, [franchises, matchesSlot, sortMode]);

  const flatSlots = useMemo(() => visible.flatMap((f) => f.entries), [visible]);

  if (loading) {
    return (
      <div className="grid gap-6 lg:grid-cols-[210px,1fr]">
        <div className="skeleton hidden h-[420px] lg:block" />
        <div className="grid grid-cols-3 gap-x-4 gap-y-6 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6">
          {Array.from({ length: 18 }).map((_, i) => (
            <div key={i}>
              <div className="skeleton aspect-[185/265] w-full" />
              <div className="skeleton mt-2 h-3 w-4/5" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[210px,1fr]">
      {/* ---------------- Filter rail ---------------- */}
      <aside className="min-w-0 space-y-4">
        <div>
          <span className="mb-1.5 block text-[12.5px] font-semibold text-fg-bright">
            Search
          </span>
          <div className="relative">
            <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-fg-light">
              <IconSearch size={14} />
            </span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Filter titles…"
              className="field pl-9"
            />
          </div>
        </div>

        <Select
          label="Format"
          value={format}
          onChange={setFormat}
          accent="var(--accent)"
        >
          <option value="">Any</option>
          {formats.map((f) => (
            <option key={f} value={f}>
              {prettyFormat(f)}
            </option>
          ))}
        </Select>

        <Select
          label="Genre"
          value={genre}
          onChange={setGenre}
          accent="var(--accent)"
        >
          <option value="">Any</option>
          {genres.map((g) => (
            <option key={g} value={g}>
              {g}
            </option>
          ))}
        </Select>

        <Select
          label="Year"
          value={year}
          onChange={setYear}
          accent="var(--accent)"
        >
          <option value="">Any</option>
          {years.map((y) => (
            <option key={y} value={String(y)}>
              {y}
            </option>
          ))}
        </Select>

        <Select
          label="Airing Status"
          value={airing}
          onChange={setAiring}
          accent="var(--accent)"
        >
          <option value="">Any</option>
          {[
            "RELEASING",
            "FINISHED",
            "NOT_YET_RELEASED",
            "HIATUS",
            "CANCELLED",
          ].map((s) => (
            <option key={s} value={s}>
              {mediaStatusLabel(s)}
            </option>
          ))}
        </Select>

        <Select
          label="Sort"
          value={sortMode}
          onChange={(v) => setSortMode(v as SortMode)}
          accent="var(--accent)"
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>

        <div className="space-y-2 pt-1">
          <ToggleRow
            label="Show unlisted relations"
            checked={includeUnlisted}
            onChange={setIncludeUnlisted}
          />
          <ToggleRow
            label="Unfinished only"
            checked={onlyUnfinished}
            onChange={setOnlyUnfinished}
          />
        </div>
      </aside>

      {/* ---------------- Entries ---------------- */}
      <div className="min-w-0">
        <div className="mb-3 flex items-center gap-2">
          <SectionTitle
            title={statusFilter === "ALL" ? "All" : statusFilter.toLowerCase()}
            count={flatSlots.length}
          />
          <div className="ml-auto flex items-center gap-0.5">
            {LAYOUTS.map((l) => (
              <button
                key={l.id}
                onClick={() => setLayout(l.id)}
                title={l.label}
                aria-label={l.label}
                className={`flex h-8 w-8 items-center justify-center rounded transition-colors ${
                  layout === l.id
                    ? "bg-bg-fg text-[color:var(--accent)]"
                    : "text-fg-light hover:text-fg-bright"
                }`}
              >
                {l.icon}
              </button>
            ))}
          </div>
        </div>

        {visible.length === 0 ? (
          <EmptyState
            title={
              totalEntries === 0
                ? `Nothing on your ${type.toLowerCase()} list yet.`
                : "No titles match your filters."
            }
            hint={
              totalEntries === 0
                ? "Add titles in the Arkhime app or on AniList, then reload."
                : "Try clearing a filter or widening your search."
            }
            action={
              totalEntries === 0 ? (
                <a
                  href={`https://anilist.co/search/${type.toLowerCase()}`}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-1 rounded bg-[color:var(--accent)] px-5 py-2 text-[12.5px] font-bold text-white"
                >
                  Browse AniList
                </a>
              ) : null
            }
          />
        ) : layout === "SERIES" ? (
          visible.map((f) => (
            <FranchiseRow
              key={f.id}
              franchise={f}
              edits={edits}
              scoreFormat={scoreFormat}
              isChanged={isChanged}
              onPatch={onPatch}
              onOpen={onOpen}
            />
          ))
        ) : layout === "GRID" ? (
          <div className="grid grid-cols-3 gap-x-4 gap-y-6 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6">
            {flatSlots.map((s) => {
              const eff = effective(s, edits[s.media.id]);
              const total =
                s.media.type === "ANIME" ? s.media.episodes : s.media.chapters;
              return (
                <MediaCard
                  key={s.media.id}
                  media={slotAsMedia(s, edits[s.media.id])}
                  onOpen={onOpen}
                  caption={
                    s.onList
                      ? {
                          left: `${eff.progress}${total ? `/${total}` : ""}`,
                          right: eff.score ? `${eff.score}` : undefined,
                        }
                      : undefined
                  }
                />
              );
            })}
          </div>
        ) : (
          <CompactList
            slots={flatSlots}
            edits={edits}
            scoreFormat={scoreFormat}
            isChanged={isChanged}
            onPatch={onPatch}
            onOpen={onOpen}
          />
        )}
      </div>
    </div>
  );
}

function ToggleRow({
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
      aria-pressed={checked}
      className="flex w-full items-center gap-2 text-left text-[12.5px] font-semibold text-fg-light transition-colors hover:text-fg-bright"
    >
      <span
        className="flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-sm"
        style={{
          backgroundColor: checked ? "var(--accent)" : "#2a2a2e",
        }}
      >
        {checked ? (
          <span className="h-1.5 w-1.5 rounded-sm bg-[#1a0507]" />
        ) : null}
      </span>
      {label}
    </button>
  );
}

/* ---------------- Compact table-style list ---------------- */

function CompactList({
  slots,
  edits,
  scoreFormat,
  isChanged,
  onPatch,
  onOpen,
}: {
  slots: ListSlot[];
  edits: Record<number, EntryPatch>;
  scoreFormat: ScoreFormat | null | undefined;
  isChanged: (slot: ListSlot) => boolean;
  onPatch: (mediaId: number, patch: EntryPatch) => void;
  onOpen: (mediaId: number) => void;
}) {
  const sp = scoreInputProps(scoreFormat);

  return (
    <div className="surface overflow-hidden rounded">
      <div className="hidden items-center gap-3 px-3 py-2 text-[11px] font-bold uppercase tracking-wide text-fg-light sm:flex">
        <span className="w-9" />
        <span className="flex-1">Title</span>
        <span className="w-24">Status</span>
        <span className="w-28 text-center">Progress</span>
        <span className="w-14 text-center">Score</span>
      </div>

      {slots.map((s, i) => {
        const patch = edits[s.media.id];
        const eff = effective(s, patch);
        const total =
          s.media.type === "ANIME" ? s.media.episodes : s.media.chapters;
        const color = statusColor(eff.status);
        const changed = isChanged(s);

        return (
          <div
            key={s.media.id}
            className={`flex flex-wrap items-center gap-3 px-3 py-2 transition-colors ${
              i % 2 ? "bg-bg-grey/40" : ""
            } ${changed ? "bg-bg-300" : "hover:bg-bg-300"}`}
          >
            <button
              onClick={() => onOpen(s.media.id)}
              className="h-12 w-9 flex-shrink-0 overflow-hidden rounded-sm bg-bg-300"
            >
              {s.media.coverImage?.medium ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={s.media.coverImage.medium}
                  alt=""
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              ) : null}
            </button>

            <button
              onClick={() => onOpen(s.media.id)}
              className="min-w-[150px] flex-1 text-left"
            >
              <span className="clamp-1 block text-[13px] font-semibold text-fg-bright">
                {preferredTitle(s.media.title)}
              </span>
              <span className="block truncate text-[11px] text-fg-light">
                {prettyFormat(s.media.format)}
              </span>
            </button>

            {eff.status ? (
              <select
                value={eff.status}
                onChange={(e) =>
                  onPatch(s.media.id, {
                    status: e.target.value as MediaListStatus,
                  })
                }
                className="w-24 cursor-pointer rounded-sm bg-transparent py-1 text-[11.5px] font-semibold outline-none"
                style={{ color }}
                aria-label="Status"
              >
                {STATUS_OPTIONS.map((st) => (
                  <option key={st} value={st}>
                    {STATUS_META[st].label(s.media.type)}
                  </option>
                ))}
              </select>
            ) : (
              <button
                onClick={() => onPatch(s.media.id, { status: "PLANNING" })}
                className="w-24 rounded-sm py-1 text-[11.5px] font-semibold text-fg-light transition-colors hover:text-[color:var(--accent)]"
              >
                + Add
              </button>
            )}

            <div className="flex w-28 flex-shrink-0 items-center gap-1.5">
              <ScrollNumberInput
                value={eff.progress}
                min={0}
                max={total ?? 100000}
                step={1}
                onChange={(v) => onPatch(s.media.id, { progress: v })}
                className="w-9 bg-transparent text-right text-[12.5px] font-semibold tabular-nums text-fg-bright outline-none"
                ariaLabel="Progress"
              />
              <span className="text-[11px] text-fg-light">/{total ?? "?"}</span>
              {total ? (
                <Meter
                  value={eff.progress}
                  max={total}
                  accent={color}
                  height={3}
                />
              ) : null}
            </div>

            <ScrollNumberInput
              value={eff.score}
              min={sp.min}
              max={sp.max}
              step={sp.step}
              emptyWhenZero
              placeholder="—"
              onChange={(v) => onPatch(s.media.id, { score: v })}
              className="w-14 flex-shrink-0 bg-transparent text-center text-[12.5px] font-semibold tabular-nums outline-none"
              ariaLabel="Your score"
            />

            <span
              className="hidden w-12 flex-shrink-0 text-center text-[12px] font-semibold tabular-nums sm:block"
              style={{ color: scoreColor(s.media.averageScore) }}
            >
              {s.media.averageScore ?? "—"}
            </span>
          </div>
        );
      })}
    </div>
  );
}
