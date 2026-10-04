"use client";

export interface SaveProgress {
  done: number;
  total: number;
}

/**
 * Docked bar for the batched collection edits. Flat throughout: the
 * progress rail is a solid teal fill on a solid track.
 */
export default function SaveBar({
  count,
  saving,
  progress,
  onSave,
  onDiscard,
}: {
  count: number;
  saving: boolean;
  progress: SaveProgress | null;
  onSave: () => void;
  onDiscard: () => void;
}) {
  if (count === 0 && !saving) return null;

  const pct =
    progress && progress.total > 0
      ? Math.round((progress.done / progress.total) * 100)
      : 0;

  return (
    <div
      className="fixed inset-x-0 bottom-0 z-[60] animate-slide-up border-t border-bg-300 bg-bg-fg"
      style={{ paddingBottom: "var(--safe-bottom)" }}
    >
      <div className="h-[3px] w-full bg-bg-300">
        <div
          className={`h-full bg-[color:var(--accent)] transition-[width] duration-300 ${
            saving ? "" : "animate-pulse-bar"
          }`}
          style={{ width: saving ? `${pct}%` : "100%" }}
        />
      </div>

      <div className="mx-auto flex max-w-[1700px] items-center gap-2 px-3 py-2.5 sm:gap-3 sm:px-6 sm:py-3">
        <div className="min-w-0 text-[12px] font-bold sm:text-sm">
          {saving ? (
            <span className="text-fg">
              <span className="hidden sm:inline">Syncing to AniList… </span>
              <span className="sm:hidden">Syncing… </span>
              <span className="tabular-nums text-[color:var(--accent)]">
                {progress?.done ?? 0}/{progress?.total ?? count}
              </span>
            </span>
          ) : (
            <span className="text-fg-light">
              <span className="font-sans text-lg font-extrabold text-[color:var(--accent)]">
                {count}
              </span>{" "}
              {count === 1 ? "change" : "changes"}
              <span className="hidden sm:inline"> pending</span>
            </span>
          )}
        </div>

        <div className="ml-auto flex flex-shrink-0 items-center gap-2">
          <button
            onClick={onDiscard}
            disabled={saving}
            className="surface rounded px-3 py-2 text-[11px] font-bold text-fg-light transition-colors hover:text-fg disabled:opacity-40 sm:px-4 sm:text-xs"
          >
            Discard
          </button>
          <button
            onClick={onSave}
            disabled={saving || count === 0}
            className="bg-[color:var(--accent)] px-4 py-2 text-[11px] font-extrabold text-[#1a0507] transition-opacity hover:opacity-90 active:opacity-80 disabled:opacity-50 sm:px-6 sm:text-xs"
          >
            {saving ? "Saving…" : "Save to AniList"}
          </button>
        </div>
      </div>
    </div>
  );
}
