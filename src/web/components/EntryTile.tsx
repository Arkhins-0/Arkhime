"use client";

import { memo } from "react";
import ScrollNumberInput from "./ScrollNumberInput";
import { Meter } from "./ui";
import { IconPlus, IconCheck } from "./Icons";
import {
  airingCountdown,
  contrastText,
  coverAccent,
  dateRangeLabel,
  mediaStatusColor,
  mediaStatusLabel,
  preferredTitle,
  prettyFormat,
  scoreColor,
  scoreInputProps,
  statusColor,
  STATUS_META,
  withAlpha,
} from "@/web/lib/format";
import type { EntryPatch } from "@/web/lib/edits";
import type { ListSlot, MediaListStatus, ScoreFormat } from "@/web/lib/types";

export type { EntryPatch };

const STATUS_OPTIONS: MediaListStatus[] = [
  "CURRENT",
  "PLANNING",
  "COMPLETED",
  "PAUSED",
  "DROPPED",
  "REPEATING",
];

/**
 * One editable title inside a series row. A flat block: solid status spine,
 * artwork, then controls — no gradients, no overlays on the poster.
 */
function EntryTile({
  slot,
  edit,
  scoreFormat,
  changed,
  onPatch,
  onOpen,
}: {
  slot: ListSlot;
  edit: EntryPatch | undefined;
  scoreFormat: ScoreFormat | null | undefined;
  changed: boolean;
  onPatch: (mediaId: number, patch: EntryPatch) => void;
  onOpen: (mediaId: number) => void;
}) {
  const { media } = slot;
  const mediaId = media.id;

  const status: MediaListStatus | null = edit?.status ?? slot.status;
  const score = edit?.score ?? slot.score;
  const progress = edit?.progress ?? slot.progress;
  const isUnlisted = status === null;

  const title = preferredTitle(media.title);
  const cover = media.coverImage?.large || media.coverImage?.medium || "";
  const total = media.type === "ANIME" ? media.episodes : media.chapters;
  const unit = media.type === "ANIME" ? "EP" : "CH";
  const sp = scoreInputProps(scoreFormat);
  const color = status ? statusColor(status) : "#55474a";
  const accent = coverAccent(media.coverImage?.color, media.id);

  const airingLabel = mediaStatusLabel(media.status);
  const airingColor = mediaStatusColor(media.status);
  const dateRange = dateRangeLabel(
    media.startDate,
    media.endDate,
    media.status,
  );
  const countdown = airingCountdown(media.nextAiringEpisode);

  const canIncrement = !isUnlisted && (total == null || progress < total);

  return (
    <div
      className={`group relative flex gap-2.5 overflow-hidden border p-2 transition-colors sm:gap-3 sm:p-2.5 ${
        changed ? "bg-bg-300" : "bg-bg-fg hover:bg-bg-300"
      }`}
      style={{
        ["--" as string]: changed ? "#ff6a4d" : accent,
        borderColor: changed ? "#ff6a4d" : "#2a1d20",
      }}
    >
      {/* Status spine — solid, full height. */}
      <span
        className="absolute left-0 top-0 h-full w-1"
        style={{ backgroundColor: color }}
      />

      <button
        type="button"
        onClick={() => onOpen(mediaId)}
        title={`Open ${title}`}
        className={`relative ml-1 aspect-[2/3] w-[68px] flex-shrink-0 overflow-hidden bg-bg-fg sm:w-[82px] ${
          isUnlisted ? "opacity-60 group-hover:opacity-100" : ""
        } transition-opacity`}
      >
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={cover}
            alt={title}
            loading="lazy"
            className="h-full w-full object-cover"
          />
        ) : null}
        {media.averageScore ? (
          <span
            className="absolute right-0 top-0 bg-bg px-1 py-px text-[9px] font-extrabold tabular-nums"
            style={{ color: scoreColor(media.averageScore) }}
          >
            {media.averageScore}
          </span>
        ) : null}
      </button>

      <div className="flex min-w-0 flex-1 flex-col">
        <button
          type="button"
          onClick={() => onOpen(mediaId)}
          className="clamp-2 text-left text-[12.5px] font-bold leading-snug text-fg transition-colors hover:text-[color:var(--accent)] sm:text-[13px]"
          title={title}
        >
          {title}
          {changed ? (
            <span
              className="ml-1.5 inline-block h-2 w-2 bg-[color:var(--accent)] align-middle"
              title={slot.onList ? "Edited" : "Adding to list"}
            />
          ) : null}
        </button>

        <div className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[9.5px] font-bold uppercase tracking-wide text-fg-light">
          <span>{prettyFormat(media.format)}</span>
          {isUnlisted && total != null ? (
            <>
              <span className="text-fg-light">/</span>
              <span>
                {total} {unit === "EP" ? (total === 1 ? "ep" : "eps") : "ch"}
              </span>
            </>
          ) : null}
          {media.duration ? (
            <>
              <span className="text-fg-light">/</span>
              <span>{media.duration}m</span>
            </>
          ) : null}
          {airingLabel ? (
            <>
              <span className="text-fg-light">/</span>
              <span className="inline-flex items-center gap-1 normal-case">
                <span
                  className="h-1.5 w-1.5 flex-shrink-0"
                  style={{ backgroundColor: airingColor }}
                />
                {airingLabel}
              </span>
            </>
          ) : null}
        </div>

        {dateRange || countdown ? (
          <div className="mt-0.5 flex flex-wrap items-center gap-x-2 text-[10px] font-medium text-fg-light">
            {dateRange ? <span>{dateRange}</span> : null}
            {countdown ? (
              <span className="font-extrabold text-[color:var(--accent)]">
                {countdown}
              </span>
            ) : null}
          </div>
        ) : null}

        {isUnlisted ? (
          <button
            onClick={() => onPatch(mediaId, { status: "PLANNING" })}
            className="mt-2 inline-flex w-fit items-center gap-1 border border-dashed border-bg-300 px-2.5 py-1 text-[11px] font-extrabold text-fg-light transition-colors hover:border-[color:var(--accent)] hover:text-[color:var(--accent)]"
          >
            <IconPlus size={11} /> Add to list
          </button>
        ) : (
          <>
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              <select
                value={status}
                onChange={(e) =>
                  onPatch(mediaId, {
                    status: e.target.value as MediaListStatus,
                  })
                }
                className="flat-select cursor-pointer border-0 py-1 pl-2 text-[10.5px] font-extrabold outline-none"
                style={{ backgroundColor: color, color: contrastText(color) }}
                aria-label="Status"
              >
                {STATUS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {STATUS_META[s].label(media.type)}
                  </option>
                ))}
              </select>

              {canIncrement ? (
                <button
                  onClick={() => onPatch(mediaId, { progress: progress + 1 })}
                  title={`Watched one more ${
                    unit === "EP" ? "episode" : "chapter"
                  }`}
                  className="inline-flex items-center gap-0.5 border border-bg-300 px-2 py-1 text-[10px] font-extrabold text-fg-light transition-colors hover:border-[color:var(--accent)] hover:text-[color:var(--accent)]"
                >
                  <IconPlus size={10} /> 1
                </button>
              ) : (
                <span className="inline-flex items-center gap-1 px-1 text-[10px] font-extrabold text-[color:var(--accent)]">
                  <IconCheck size={11} /> Done
                </span>
              )}

              {slot.repeat > 0 ? (
                <span
                  className="px-1.5 py-1 text-[10px] font-extrabold"
                  style={{
                    backgroundColor: withAlpha(statusColor("REPEATING"), 0.22),
                    color: statusColor("REPEATING"),
                  }}
                  title={`Repeated ${slot.repeat}x`}
                >
                  x{slot.repeat}
                </span>
              ) : null}
            </div>

            <div className="mt-2 flex items-center gap-3 text-[11px]">
              <label
                className="flex items-center gap-1"
                title="Scroll over the number to change progress"
              >
                <span className="text-[9.5px] font-extrabold tracking-wide text-fg-light">
                  {unit}
                </span>
                <ScrollNumberInput
                  value={progress}
                  min={0}
                  max={total ?? 100000}
                  step={1}
                  onChange={(v) => onPatch(mediaId, { progress: v })}
                  className="w-8 border-b border-bg-300 bg-transparent text-center font-extrabold tabular-nums text-fg outline-none focus:border-[color:var(--accent)]"
                  ariaLabel="Progress"
                />
                <span className="text-fg-light">/{total ?? "?"}</span>
              </label>

              <label
                className="flex items-center gap-1"
                title="Scroll over the number to change your score"
              >
                <span className="text-[9.5px] font-extrabold tracking-wide text-fg-light">
                  SCORE
                </span>
                <ScrollNumberInput
                  value={score}
                  min={sp.min}
                  max={sp.max}
                  step={sp.step}
                  emptyWhenZero
                  placeholder="—"
                  onChange={(v) => onPatch(mediaId, { score: v })}
                  className="w-9 border-b border-bg-300 bg-transparent text-center font-extrabold tabular-nums text-[color:var(--accent)] outline-none placeholder:text-fg-light focus:border-[color:var(--accent)]"
                  ariaLabel="Your score"
                />
              </label>
            </div>

            {total ? (
              <Meter
                value={progress}
                max={total}
                accent={color}
                height={3}
                className="mt-2"
              />
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}

// Memoized so a wheel/keystroke edit only re-renders the one touched card —
// without this, updating the shared `edits` state re-renders every card in
// every row on every scroll tick, which is what made editing feel slow.
export default memo(EntryTile);
