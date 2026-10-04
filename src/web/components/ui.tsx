"use client";

import { ReactNode } from "react";
import { contrastText, withAlpha } from "@/web/lib/format";

/* =====================================================================
   Primitives, following AniList's patterns: borderless filled surfaces,
   4px radii, generous spacing, quiet muted type. Accent colours come
   from the Deep Ocean palette and are always flat fills.
   ===================================================================== */

/**
 * AniList's section header: an uppercase muted heading on the left and an
 * optional action ("View All") on the right. No rules, bars or badges —
 * the spacing does the work.
 */
export function SectionTitle({
  title,
  count,
  action,
  subtitle,
}: {
  title: ReactNode;
  count?: number;
  accent?: string;
  action?: ReactNode;
  subtitle?: string;
}) {
  return (
    <div className="mb-3 flex items-baseline gap-3">
      <div className="min-w-0">
        <h2 className="section-heading truncate">
          {title}
          {count != null ? (
            <span className="ml-2 font-semibold text-fg-light">{count}</span>
          ) : null}
        </h2>
        {subtitle ? (
          <p className="mt-0.5 truncate text-[12px] text-fg-light">
            {subtitle}
          </p>
        ) : null}
      </div>
      {action ? <div className="ml-auto flex-shrink-0">{action}</div> : null}
    </div>
  );
}

/** The muted uppercase "View All" link AniList puts beside a heading. */
export function SectionLink({
  children,
  onClick,
}: {
  children: ReactNode;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="section-heading transition-colors hover:text-[color:var(--accent)]"
    >
      {children}
    </button>
  );
}

/** Filter / toggle pill. */
export function Chip({
  active,
  color,
  children,
  onClick,
  title,
  size = "md",
}: {
  active?: boolean;
  color?: string;
  children: ReactNode;
  onClick?: () => void;
  title?: string;
  size?: "sm" | "md";
}) {
  const accent = color ?? "#ff4f6d";
  const pad =
    size === "sm" ? "px-2.5 py-1 text-[11px]" : "px-3 py-1.5 text-[12px]";
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      className={`inline-flex items-center gap-1.5 rounded font-semibold transition-colors ${pad} ${
        active ? "" : "surface text-fg-light hover:text-fg-bright"
      }`}
      style={
        active
          ? { backgroundColor: accent, color: contrastText(accent) }
          : undefined
      }
    >
      {children}
    </button>
  );
}

/** A small flat square marker (status dot). */
export function Dot({ color, size = 8 }: { color: string; size?: number }) {
  return (
    <span
      className="inline-block flex-shrink-0 rounded-sm"
      style={{ backgroundColor: color, width: size, height: size }}
    />
  );
}

/** Progress bar — solid track, solid fill. */
export function Meter({
  value,
  max,
  accent = "#ff4f6d",
  height = 4,
  className = "",
}: {
  value: number;
  max: number;
  accent?: string;
  height?: number;
  className?: string;
}) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <div
      className={`w-full overflow-hidden rounded-sm bg-bg-300 ${className}`}
      style={{ height }}
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div
        className="h-full transition-[width] duration-300"
        style={{ width: `${pct}%`, backgroundColor: accent }}
      />
    </div>
  );
}

export function GhostButton({
  children,
  onClick,
  disabled,
  title,
  danger,
  className = "",
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  title?: string;
  danger?: boolean;
  className?: string;
  type?: "button" | "submit";
}) {
  return (
    <button
      type={type}
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`surface rounded px-4 py-2 text-[12.5px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40 ${
        danger
          ? "text-status-dropped hover:brightness-125"
          : "text-fg-light hover:text-fg-bright"
      } ${className}`}
    >
      {children}
    </button>
  );
}

/** Primary action — AniList's solid filled button. */
export function AccentButton({
  children,
  onClick,
  disabled,
  title,
  accent = "#ff4f6d",
  className = "",
  type = "button",
}: {
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  title?: string;
  accent?: string;
  className?: string;
  type?: "button" | "submit";
}) {
  return (
    <button
      type={type}
      title={title}
      onClick={onClick}
      disabled={disabled}
      className={`rounded px-5 py-2 text-[12.5px] font-bold transition-opacity hover:opacity-90 active:opacity-80 disabled:cursor-not-allowed disabled:opacity-40 ${className}`}
      style={{ backgroundColor: accent, color: contrastText(accent) }}
    >
      {children}
    </button>
  );
}

export const NeonButton = AccentButton;

export function IconButton({
  children,
  onClick,
  title,
  active,
  accent = "#ff4f6d",
  className = "",
}: {
  children: ReactNode;
  onClick?: () => void;
  title?: string;
  active?: boolean;
  accent?: string;
  className?: string;
}) {
  return (
    <button
      type="button"
      title={title}
      aria-label={title}
      onClick={onClick}
      className={`surface flex h-9 w-9 flex-shrink-0 items-center justify-center rounded transition-colors ${
        active ? "" : "text-fg-light hover:text-fg-bright"
      } ${className}`}
      style={active ? { color: accent } : undefined}
    >
      {children}
    </button>
  );
}

export function EmptyState({
  title,
  hint,
  action,
}: {
  title: string;
  hint?: string;
  action?: ReactNode;
}) {
  return (
    <div className="surface flex flex-col items-center gap-3 rounded px-6 py-14 text-center">
      <p className="text-[15px] font-bold text-fg-bright">{title}</p>
      {hint ? (
        <p className="max-w-md text-[13px] text-fg-light">{hint}</p>
      ) : null}
      {action}
    </div>
  );
}

/**
 * AniList's stat card: a row of big numbers with small labels beneath,
 * all on one filled surface.
 */
export function StatRow({
  items,
  accent = "#ff4f6d",
}: {
  items: { label: string; value: ReactNode }[];
  accent?: string;
}) {
  return (
    <div className="surface grid grid-cols-3 gap-2 rounded px-4 py-5 sm:px-6">
      {items.map((it) => (
        <div key={it.label} className="text-center">
          <p
            className="text-[20px] font-semibold leading-tight tabular-nums sm:text-[24px]"
            style={{ color: accent }}
          >
            {it.value}
          </p>
          <p className="mt-1 text-[11.5px] text-fg-light sm:text-[12.5px]">
            {it.label}
          </p>
        </div>
      ))}
    </div>
  );
}

/** Single statistic tile. */
export function StatBlock({
  label,
  value,
  sub,
  accent = "#ff4f6d",
}: {
  label: string;
  value: ReactNode;
  sub?: string;
  accent?: string;
}) {
  return (
    <div className="surface rounded px-4 py-4 text-center">
      <p
        className="text-[20px] font-semibold leading-tight tabular-nums sm:text-[24px]"
        style={{ color: accent }}
      >
        {value}
      </p>
      <p className="mt-1 text-[12px] text-fg-light">{label}</p>
      {sub ? <p className="mt-0.5 text-[11px] text-fg-light">{sub}</p> : null}
    </div>
  );
}

/** AniList's sidebar data card: bold labels with muted values beneath. */
export function DataList({
  rows,
}: {
  rows: { label: string; value: ReactNode }[];
}) {
  return (
    <div className="surface rounded px-4 py-3.5">
      {rows.map((r, i) => (
        <div key={r.label} className={i > 0 ? "mt-3.5" : ""}>
          <p className="label">{r.label}</p>
          <p className="value mt-0.5 break-words">{r.value}</p>
        </div>
      ))}
    </div>
  );
}

/** Small filled tag, used for genres, ranking badges and metadata. */
export function Tag({
  children,
  color,
  solid,
  title,
}: {
  children: ReactNode;
  color?: string;
  solid?: boolean;
  title?: string;
}) {
  const c = color ?? "#e6a23c";
  return (
    <span
      title={title}
      className="inline-flex items-center gap-1 rounded-sm px-2 py-1 text-[11.5px] font-semibold"
      style={
        solid
          ? { backgroundColor: c, color: contrastText(c) }
          : { backgroundColor: withAlpha(c, 0.18), color: c }
      }
    >
      {children}
    </span>
  );
}

/** Filled select matching AniList's filter dropdowns. */
export function Select({
  label,
  value,
  onChange,
  children,
  accent = "#ff4f6d",
  disabled,
  className = "",
}: {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  children: ReactNode;
  accent?: string;
  disabled?: boolean;
  className?: string;
}) {
  return (
    <label className={`block ${className}`}>
      {label ? (
        <span className="mb-1.5 block text-[12.5px] font-semibold text-fg-bright">
          {label}
        </span>
      ) : null}
      <span className="relative block">
        <select
          value={value}
          disabled={disabled}
          aria-label={label}
          onChange={(e) => onChange(e.target.value)}
          className="field"
          style={value ? { color: accent } : undefined}
        >
          {children}
        </select>
        <span
          className="pointer-events-none absolute right-3 top-1/2 h-0 w-0 -translate-y-1/2"
          style={{
            borderLeft: "5px solid transparent",
            borderRight: "5px solid transparent",
            borderTop: `6px solid ${value ? accent : "#b4b4bb"}`,
          }}
        />
      </span>
    </label>
  );
}

/** Poster-grid loading placeholder, matching the real grid metrics. */
export function CardSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div className="grid grid-cols-3 gap-x-4 gap-y-6 sm:grid-cols-4 md:grid-cols-5 lg:grid-cols-6 xl:grid-cols-7">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i}>
          <div className="skeleton aspect-[185/265] w-full" />
          <div className="skeleton mt-2 h-3 w-4/5" />
        </div>
      ))}
    </div>
  );
}
