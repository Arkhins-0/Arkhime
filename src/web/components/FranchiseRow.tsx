"use client";

import { memo, useState } from "react";
import EntryTile from "./EntryTile";
import { Meter } from "./ui";
import { IconChevronDown } from "./Icons";
import type { EntryPatch } from "@/web/lib/edits";
import { franchiseCompletion, type Franchise } from "@/web/lib/franchise";
import { coverAccent, withAlpha } from "@/web/lib/format";
import type { ListSlot, ScoreFormat } from "@/web/lib/types";

function FranchiseRow({
  franchise,
  edits,
  scoreFormat,
  isChanged,
  onPatch,
  onOpen,
}: {
  franchise: Franchise;
  edits: Record<number, EntryPatch>;
  scoreFormat: ScoreFormat | null | undefined;
  isChanged: (slot: ListSlot) => boolean;
  onPatch: (mediaId: number, patch: EntryPatch) => void;
  onOpen: (mediaId: number) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);

  const first = franchise.entries[0];
  const accent = coverAccent(first?.media.coverImage?.color, franchise.id);
  const completion = franchiseCompletion(franchise);
  const onList = franchise.entries.filter((e) => e.onList).length;
  const missing = franchise.entries.length - onList;

  return (
    <section className="animate-fade mb-7 sm:mb-9">
      {/* Header is its own solid strip so each series reads as a block. */}
      <div className="mb-2.5 flex items-stretch gap-0 border border-bg-300 bg-bg-grey">
        <span
          className="w-1.5 flex-shrink-0"
          style={{ backgroundColor: accent }}
        />

        <button
          onClick={() => setCollapsed((c) => !c)}
          className="flex min-w-0 flex-1 items-center gap-2 px-2.5 py-2 text-left sm:px-3"
          title={collapsed ? "Expand series" : "Collapse series"}
          aria-expanded={!collapsed}
        >
          <IconChevronDown
            size={15}
            className={`flex-shrink-0 text-fg-light transition-transform ${
              collapsed ? "-rotate-90" : ""
            }`}
          />
          <h2 className="truncate font-sans text-[15px] font-extrabold uppercase tracking-tight text-fg sm:text-xl">
            {franchise.label}
          </h2>

          <span
            className="ml-auto flex-shrink-0 px-1.5 py-0.5 text-[10.5px] font-extrabold tabular-nums"
            style={{ backgroundColor: withAlpha(accent, 0.18), color: accent }}
            title={`${onList} on your list${
              missing ? `, ${missing} related not added` : ""
            }`}
          >
            {onList}
            {missing > 0 ? (
              <span className="opacity-60">{` +${missing}`}</span>
            ) : null}
          </span>

          {completion > 0 ? (
            <span
              className="hidden flex-shrink-0 text-[11px] font-extrabold tabular-nums sm:block"
              style={{ color: accent }}
              title="Share of this series you have finished"
            >
              {Math.round(completion * 100)}%
            </span>
          ) : null}
        </button>
      </div>

      {/* Completion rail sits directly under the header strip. */}
      {completion > 0 ? (
        <Meter
          value={completion}
          max={1}
          accent={accent}
          height={2}
          className="mb-2.5"
        />
      ) : null}

      {!collapsed ? (
        <div className="grid grid-cols-1 gap-2.5 md:grid-cols-2 xl:grid-cols-[repeat(auto-fill,minmax(340px,1fr))]">
          {franchise.entries.map((slot) => (
            <EntryTile
              key={slot.media.id}
              slot={slot}
              edit={edits[slot.media.id]}
              scoreFormat={scoreFormat}
              changed={isChanged(slot)}
              onPatch={onPatch}
              onOpen={onOpen}
            />
          ))}
        </div>
      ) : null}
    </section>
  );
}

export default memo(FranchiseRow);
