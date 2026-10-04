"use client";

import { useEffect, useLayoutEffect, useRef } from "react";

/**
 * A numeric input that also increments/decrements on mouse-wheel while the
 * cursor is over it. The wheel listener is attached natively with
 * { passive: false } so we can preventDefault (React's onWheel is passive and
 * cannot stop the page from scrolling).
 */
export default function ScrollNumberInput({
  value,
  min,
  max,
  step = 1,
  onChange,
  className,
  placeholder,
  ariaLabel,
  emptyWhenZero = false,
}: {
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  className?: string;
  placeholder?: string;
  ariaLabel?: string;
  emptyWhenZero?: boolean;
}) {
  const ref = useRef<HTMLInputElement>(null);

  // Keep the latest props available to the native (once-bound) wheel handler.
  const state = useRef({ value, min, max, step, onChange });
  useLayoutEffect(() => {
    state.current = { value, min, max, step, onChange };
  });

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      const s = state.current;
      const dir = e.deltaY < 0 ? 1 : -1;
      s.onChange(snap(clamp(s.value + dir * s.step, s.min, s.max), s.step));
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, []);

  return (
    <input
      ref={ref}
      type="number"
      inputMode="decimal"
      min={min}
      max={max}
      step={step}
      value={emptyWhenZero && !value ? "" : value}
      placeholder={placeholder}
      aria-label={ariaLabel}
      onChange={(e) => {
        const raw = e.target.value;
        if (raw === "") {
          onChange(0);
          return;
        }
        const n = Number(raw);
        if (Number.isNaN(n)) return;
        onChange(snap(clamp(n, min, max), step));
      }}
      className={className}
    />
  );
}

function clamp(n: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, n));
}

/** Snap to the step grid and clean up floating-point drift (e.g. 0.5 steps). */
function snap(n: number, step: number): number {
  const inv = 1 / step;
  return Math.round(n * inv) / inv;
}
