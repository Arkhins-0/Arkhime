"use client";

import { useEffect } from "react";
import { IconClose } from "./Icons";

export type ToastKind = "info" | "success" | "error";

export interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

const ACCENT: Record<ToastKind, string> = {
  info: "#e6a23c",
  success: "#ff6a4d",
  error: "#4a4a50",
};

/** Bottom-right stack of transient messages. Auto-dismisses after 4.5s. */
export default function Toasts({
  toasts,
  onDismiss,
}: {
  toasts: Toast[];
  onDismiss: (id: number) => void;
}) {
  return (
    <div className="pointer-events-none fixed inset-x-3 bottom-3 z-[80] flex flex-col gap-2 sm:inset-x-auto sm:bottom-4 sm:right-4 sm:w-[22rem]">
      {toasts.map((t) => (
        <ToastRow key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

function ToastRow({
  toast,
  onDismiss,
}: {
  toast: Toast;
  onDismiss: (id: number) => void;
}) {
  useEffect(() => {
    const id = setTimeout(() => onDismiss(toast.id), 4500);
    return () => clearTimeout(id);
  }, [toast.id, onDismiss]);

  const accent = ACCENT[toast.kind];

  return (
    <div
      role="status"
      className="surface rounded pointer-events-auto flex animate-fade items-start gap-0 overflow-hidden shadow-[0_18px_40px_-20px_rgba(0,0,0,0.9)]"
    >
      {/* Solid accent rail — the only colour on the toast. */}
      <span
        className="w-1.5 flex-shrink-0 self-stretch"
        style={{ backgroundColor: accent }}
      />
      <p className="flex-1 px-3 py-2.5 text-[12.5px] font-semibold leading-snug text-fg">
        {toast.message}
      </p>
      <button
        onClick={() => onDismiss(toast.id)}
        className="px-2.5 py-2.5 text-fg-light transition-colors hover:text-fg"
        aria-label="Dismiss"
      >
        <IconClose size={14} />
      </button>
    </div>
  );
}
