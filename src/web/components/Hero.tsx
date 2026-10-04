"use client";

import { Chip, Dot } from "./ui";
import {
  contrastText,
  statusColor,
  STATUS_ORDER,
  statusLabel,
} from "@/web/lib/format";
import { ACCENT } from "@/web/lib/config";
import type { MediaListStatus, MediaType, Viewer } from "@/web/lib/types";

/**
 * AniList's profile header: a full-bleed banner with the username sitting
 * over its bottom-left corner, and a quiet centred tab strip on the
 * foreground surface directly beneath it.
 */
export default function Hero({
  viewer,
  type,
  total,
  counts,
  statusFilter,
  onStatusFilter,
}: {
  viewer: Viewer;
  type: MediaType;
  total: number;
  counts: Map<MediaListStatus, number>;
  statusFilter: MediaListStatus | "ALL";
  onStatusFilter: (s: MediaListStatus | "ALL") => void;
}) {
  const accent = ACCENT;

  return (
    <section>
      {/* Banner */}
      <div className="relative h-[140px] w-full overflow-hidden sm:h-[230px]">
        {viewer.bannerImage ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={viewer.bannerImage}
            alt=""
            className="h-full w-full object-cover"
          />
        ) : (
          <div className="h-full w-full" style={{ backgroundColor: accent }} />
        )}

        {/* AniList darkens the lower band so the name stays readable. A
            flat translucent fill does the same without a gradient. */}
        <div className="absolute inset-x-0 bottom-0 h-[64px] bg-[#000000]/55" />

        <div className="absolute inset-x-0 bottom-0">
          <div className="mx-auto flex max-w-site items-end gap-3 px-4 pb-3 sm:px-6 sm:pb-4">
            {viewer.avatar?.large ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={viewer.avatar.large}
                alt={viewer.name}
                className="h-12 w-auto max-w-[96px] flex-shrink-0 rounded object-contain sm:h-16 sm:max-w-[128px]"
              />
            ) : null}
            <h1 className="truncate text-[20px] font-bold text-white sm:text-[26px]">
              {viewer.name}
            </h1>
            <span className="ml-auto hidden flex-shrink-0 text-[12.5px] font-semibold text-white/80 sm:block">
              {total} {type === "ANIME" ? "anime" : "manga"}
            </span>
          </div>
        </div>
      </div>

      {/* Status filter strip, on the foreground surface like AniList's profile tab bar */}
      {(
        <div className="bg-bg-fg">
          <div className="mx-auto max-w-site px-4 sm:px-6">
            <div className="no-scrollbar flex gap-2 overflow-x-auto py-2.5">
              <Chip
                size="sm"
                active={statusFilter === "ALL"}
                color={accent}
                onClick={() => onStatusFilter("ALL")}
              >
                <Dot
                  color={statusFilter === "ALL" ? contrastText(accent) : accent}
                  size={7}
                />
                All
                <span className="tabular-nums opacity-70">{total}</span>
              </Chip>
              {STATUS_ORDER.filter((s) => counts.get(s)).map((s) => {
                const c = statusColor(s);
                const on = statusFilter === s;
                return (
                  <Chip
                    key={s}
                    size="sm"
                    active={on}
                    color={c}
                    onClick={() => onStatusFilter(s)}
                  >
                    <Dot color={on ? contrastText(c) : c} size={7} />
                    <span className="whitespace-nowrap">
                      {statusLabel(s, type)}
                    </span>
                    <span className="tabular-nums opacity-70">
                      {counts.get(s) ?? 0}
                    </span>
                  </Chip>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
