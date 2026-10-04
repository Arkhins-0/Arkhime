"use client";

import { memo } from "react";
import { IconPlus } from "./Icons";
import {
  airingCountdown,
  contrastText,
  preferredTitle,
  scoreColor,
  statusColor,
} from "@/web/lib/format";
import type { Media } from "@/web/lib/types";

/**
 * AniList's media card, matched to their metrics: a 185x265 cover at 4px
 * radius with the title as plain muted text underneath. No container,
 * border, panel or per-card button — hover tints the title, and the few
 * overlays that do appear sit on their own solid fill.
 */
function MediaCard({
  media,
  onOpen,
  onQuickAdd,
  rank,
  caption,
  showCountdown,
}: {
  media: Media;
  onOpen: (id: number) => void;
  onQuickAdd?: (media: Media) => void;
  rank?: number;
  /** Optional progress caption shown over the cover (list views). */
  caption?: { left: string; right?: string };
  /** Show "Ep 5 in 2d 4h" beneath the title (home dashboard). */
  showCountdown?: boolean;
}) {
  const title = preferredTitle(media.title);
  const cover =
    media.coverImage?.extraLarge ||
    media.coverImage?.large ||
    media.coverImage?.medium ||
    "";
  const entry = media.mediaListEntry;
  const onList = !!entry?.status;
  const listColor = statusColor(entry?.status);
  const countdown = showCountdown
    ? airingCountdown(media.nextAiringEpisode)
    : null;

  return (
    <div className="card group/card">
      <button
        type="button"
        onClick={() => onOpen(media.id)}
        className="cover"
        title={title}
      >
        {cover ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={cover} alt={title} loading="lazy" />
        ) : null}

        {/* Rank, for ranked shelves. */}
        {rank ? (
          <span
            className="absolute left-0 top-0 rounded-br-sm px-1.5 py-0.5 text-[11px] font-bold tabular-nums"
            style={{
              backgroundColor: "#ff4f6d",
              color: contrastText("#ff4f6d"),
            }}
          >
            {rank}
          </span>
        ) : null}

        {/* A thin status rail along the top edge, marking entries that are
            already on your list. */}
        {onList ? (
          <span
            className="absolute inset-x-0 top-0 h-[3px]"
            style={{ backgroundColor: listColor }}
          />
        ) : null}

        {/* Community score, bottom-right, on its own solid chip. */}
        {media.averageScore && !caption ? (
          <span
            className="absolute bottom-1 right-1 rounded-sm bg-[#000000]/90 px-1.5 py-0.5 text-[11px] font-bold tabular-nums"
            style={{ color: scoreColor(media.averageScore) }}
          >
            {media.averageScore}
          </span>
        ) : null}

        {/* Progress caption (list views) — solid bar, no scrim. */}
        {caption ? (
          <span className="cover-caption flex items-center justify-between gap-2">
            <span className="truncate text-[11px] font-semibold text-fg-bright">
              {caption.left}
            </span>
            {caption.right ? (
              <span className="flex-shrink-0 text-[11px] font-bold tabular-nums text-fg-bright">
                {caption.right}
              </span>
            ) : null}
          </span>
        ) : null}

        {/* Quick add, revealed on hover only. */}
        {onQuickAdd && !onList ? (
          <span
            role="button"
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation();
              onQuickAdd(media);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.stopPropagation();
                e.preventDefault();
                onQuickAdd(media);
              }
            }}
            title="Add to Planning"
            className="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-sm bg-[#000000]/85 text-fg-bright opacity-0 transition-opacity hover:bg-[#ff4f6d] hover:text-[#1a0507] focus:opacity-100 group-hover/card:opacity-100"
          >
            <IconPlus size={13} />
          </span>
        ) : null}
      </button>

      <button
        type="button"
        onClick={() => onOpen(media.id)}
        className="card-title clamp-2 block w-full text-left"
        title={title}
      >
        {title}
      </button>

      {countdown ? (
        <p className="mt-0.5 truncate text-[11.5px] font-semibold text-[color:var(--accent)]">
          {countdown}
        </p>
      ) : null}
    </div>
  );
}

export default memo(MediaCard);
