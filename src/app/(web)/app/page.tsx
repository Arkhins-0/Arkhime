"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import Header from "@/web/components/Header";
import Hero from "@/web/components/Hero";
import LoginScreen from "@/web/components/LoginScreen";
import SaveBar, { SaveProgress } from "@/web/components/SaveBar";
import Toasts, { type Toast, type ToastKind } from "@/web/components/Toasts";
import MediaModal from "@/web/components/MediaModal";
import CollectionView from "@/web/components/CollectionView";
import {
  AniListError,
  fetchMediaListCollection,
  fetchViewer,
  flattenCollection,
  saveAll,
  type SaveInput,
} from "@/web/lib/anilist";
import { buildFranchises } from "@/web/lib/franchise";
import {
  derivedFields,
  normalizePatch,
  patchDiffers,
  type EntryPatch,
} from "@/web/lib/edits";
import { todayFuzzy } from "@/web/lib/format";
import { WEB_TOKEN_KEY } from "@/web/lib/config";
import { ACCENT } from "@/web/lib/config";
import type {
  ListSlot,
  MediaListEntry,
  MediaListStatus,
  MediaType,
  Viewer,
} from "@/web/lib/types";

const TOKEN_KEY = WEB_TOKEN_KEY;

function readTokenFromHash(): string | null {
  if (typeof window === "undefined") return null;
  const hash = window.location.hash;
  if (!hash || !hash.includes("access_token")) return null;
  const params = new URLSearchParams(hash.slice(1));
  const token = params.get("access_token");
  if (token) window.history.replaceState(null, "", window.location.pathname);
  return token;
}

// The AniList token lives in localStorage; this tiny store lets login/logout re-render.
const tokenListeners = new Set<() => void>();
function subscribeToken(listener: () => void) {
  tokenListeners.add(listener);
  return () => {
    tokenListeners.delete(listener);
  };
}
function readToken(): string | null {
  try {
    // A fresh login lands here with #access_token=...; keep it and clean the address bar
    const fromHash = readTokenFromHash();
    if (fromHash) localStorage.setItem(TOKEN_KEY, fromHash);
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
function writeToken(value: string | null) {
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // storage blocked: the session just won't persist
  }
  tokenListeners.forEach((listener) => listener());
}

export default function HomePage() {
  // undefined while rendering on the server, then the stored token (or null)
  const token = useSyncExternalStore(subscribeToken, readToken, () => undefined);
  const ready = token !== undefined;

  const [viewer, setViewer] = useState<Viewer | null>(null);
  const [type, setType] = useState<MediaType>("ANIME");
  const [entries, setEntries] = useState<MediaListEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<MediaListStatus | "ALL">(
    "ALL",
  );
  const [modalMediaId, setModalMediaId] = useState<number | null>(null);

  // Pending edits keyed by mediaId (kept minimal — only differing fields).
  const [edits, setEdits] = useState<Record<number, EntryPatch>>({});
  const [saving, setSaving] = useState(false);
  const [saveProgress, setSaveProgress] = useState<SaveProgress | null>(null);

  const [toasts, setToasts] = useState<Toast[]>([]);
  const toastId = useRef(0);

  const notify = useCallback((message: string, kind: ToastKind = "info") => {
    const id = ++toastId.current;
    setToasts((prev) => [...prev.slice(-3), { id, kind, message }]);
  }, []);
  const dismissToast = useCallback(
    (id: number) => setToasts((prev) => prev.filter((t) => t.id !== id)),
    [],
  );

  // --- Franchise clustering (includes related titles not yet on the list) ---
  const franchises = useMemo(
    () => buildFranchises(entries, type),
    [entries, type],
  );

  const slotByMediaId = useMemo(() => {
    const m = new Map<number, ListSlot>();
    for (const f of franchises) for (const s of f.entries) m.set(s.media.id, s);
    return m;
  }, [franchises]);

  const slotByMediaIdRef = useRef(slotByMediaId);
  useEffect(() => {
    slotByMediaIdRef.current = slotByMediaId;
  }, [slotByMediaId]);

  const logout = useCallback(() => {
    writeToken(null);
    setViewer(null);
    setEntries([]);
    setEdits({});
  }, []);

  /** Shared handling for a dead/expired token. */
  const handleAuthError = useCallback(
    (e: unknown, fallback: string) => {
      if (e instanceof AniListError && e.status === 401) {
        logout();
        setError("Your session expired — please log in again.");
        return;
      }
      setError(e instanceof Error ? e.message : fallback);
    },
    [logout],
  );

  // --- Load viewer ---
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    fetchViewer(token)
      .then((v) => !cancelled && setViewer(v))
      .catch(
        (e) => !cancelled && handleAuthError(e, "Failed to load your profile."),
      );
    return () => {
      cancelled = true;
    };
  }, [token, handleAuthError]);

  // --- Load list on viewer/type change ---
  const loadList = useCallback(
    async (silent = false) => {
      if (!token || !viewer) return;
      if (silent) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const collection = await fetchMediaListCollection(
          token,
          viewer.id,
          type,
        );
        setEntries(flattenCollection(collection));
      } catch (e) {
        handleAuthError(e, "Failed to load your list.");
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [token, viewer, type, handleAuthError],
  );

  // Pending edits belong to one list: drop them when the account or anime/manga switches
  const listKey = `${viewer?.id ?? 0}:${type}`;
  const [editsListKey, setEditsListKey] = useState(listKey);
  if (editsListKey !== listKey) {
    setEditsListKey(listKey);
    setEdits({});
    if (token && viewer) {
      setLoading(true);
      setError(null);
    }
  }

  // Initial load for each list; a stale answer (switched list meanwhile) is ignored
  useEffect(() => {
    if (!token || !viewer) return;
    let cancelled = false;
    fetchMediaListCollection(token, viewer.id, type)
      .then((collection) => {
        if (!cancelled) setEntries(flattenCollection(collection));
      })
      .catch((e) => {
        if (!cancelled) handleAuthError(e, "Failed to load your list.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token, viewer, type, handleAuthError]);

  // Warn before leaving with unsaved edits.
  const changedCount = Object.keys(edits).length;
  useEffect(() => {
    if (changedCount === 0) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [changedCount]);

  // --- Editing ---
  const onPatch = useCallback((mediaId: number, patch: EntryPatch) => {
    setEdits((prev) => {
      const slot = slotByMediaIdRef.current.get(mediaId);
      if (!slot) return prev;

      // Fold in AniList's own conveniences (finishing completes the entry,
      // first progress starts it, and so on) before diffing.
      const withDerived = {
        ...patch,
        ...derivedFields(slot, patch, todayFuzzy()),
      };
      const merged: EntryPatch = { ...prev[mediaId], ...withDerived };
      const cleaned = normalizePatch(slot, merged);

      const next = { ...prev };
      if (!cleaned) delete next[mediaId];
      else next[mediaId] = cleaned;
      return next;
    });
  }, []);

  const discard = useCallback(() => setEdits({}), []);

  const handleSave = useCallback(async () => {
    if (!token || !viewer || changedCount === 0) return;
    const inputs: SaveInput[] = [];
    for (const [mediaIdStr, patch] of Object.entries(edits)) {
      const mediaId = Number(mediaIdStr);
      const slot = slotByMediaIdRef.current.get(mediaId);
      if (!slot) continue;
      const status = patch.status ?? slot.status;
      if (!status) continue; // AniList requires a status to save an entry
      inputs.push({
        mediaId,
        status,
        score: patch.score ?? slot.score,
        progress: patch.progress ?? slot.progress,
        progressVolumes:
          slot.media.type === "MANGA"
            ? (patch.progressVolumes ?? slot.progressVolumes)
            : undefined,
        repeat: patch.repeat ?? slot.repeat,
        notes: patch.notes ?? slot.notes,
        private: patch.private ?? slot.private,
        hiddenFromStatusLists:
          patch.hiddenFromStatusLists ?? slot.hiddenFromStatusLists,
        startedAt: patch.startedAt !== undefined ? patch.startedAt : undefined,
        completedAt:
          patch.completedAt !== undefined ? patch.completedAt : undefined,
      });
    }

    setSaving(true);
    setSaveProgress({ done: 0, total: inputs.length });
    setError(null);

    try {
      const result = await saveAll(token, inputs, (done, total) =>
        setSaveProgress({ done, total }),
      );

      // Re-fetch so newly-added titles (previously unlisted) become real
      // entries and every value reflects what AniList actually stored.
      if (result.saved > 0) {
        const collection = await fetchMediaListCollection(
          token,
          viewer.id,
          type,
        );
        setEntries(flattenCollection(collection));
      }

      const failedIds = new Set(result.failed.map((f) => f.mediaId));
      setEdits((prev) => {
        const next: Record<number, EntryPatch> = {};
        for (const id of failedIds) if (prev[id]) next[id] = prev[id];
        return next;
      });

      if (result.failed.some((f) => f.status === 401)) {
        logout();
        setError("Your session expired mid-save — please log in again.");
      } else if (result.failed.length > 0) {
        notify(
          `Saved ${result.saved}. ${result.failed.length} failed — you can retry them.`,
          "error",
        );
      } else {
        notify(
          `Saved ${result.saved} change${result.saved === 1 ? "" : "s"} to AniList.`,
          "success",
        );
      }
    } catch (e) {
      handleAuthError(e, "Save failed.");
    } finally {
      setSaving(false);
      setSaveProgress(null);
    }
  }, [
    token,
    viewer,
    type,
    edits,
    changedCount,
    logout,
    notify,
    handleAuthError,
  ]);

  const onTypeChange = useCallback(
    (t: MediaType) => {
      if (
        changedCount > 0 &&
        !window.confirm("Discard your unsaved changes and switch?")
      )
        return;
      setType(t);
    },
    [changedCount],
  );

  const counts = useMemo(() => {
    const c = new Map<MediaListStatus, number>();
    for (const e of entries) c.set(e.status, (c.get(e.status) ?? 0) + 1);
    return c;
  }, [entries]);

  const isChanged = useCallback(
    (slot: ListSlot) => patchDiffers(slot, edits[slot.media.id]),
    [edits],
  );

  // --- Render states ---
  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-fg-light">
        Loading…
      </div>
    );
  }

  if (!token) return <LoginScreen error={error} />;

  return (
    <div
      className="min-h-screen pb-24 sm:pb-28"
      style={{ ["--accent-a" as string]: ACCENT, ["--accent-b" as string]: ACCENT }}
    >
      {viewer ? (
        <Header
          viewer={viewer}
          type={type}
          onTypeChange={onTypeChange}
          onRefresh={() => loadList(true)}
          refreshing={refreshing}
          onLogout={logout}
        />
      ) : null}

      {viewer ? (
        <Hero
          viewer={viewer}
          type={type}
          total={entries.length}
          counts={counts}
          statusFilter={statusFilter}
          onStatusFilter={setStatusFilter}
        />
      ) : null}

      <main className="mx-auto max-w-[1700px] px-3 pt-5 sm:px-6 sm:pt-7">
        {error ? (
          <div className="mb-4 border border-status-dropped/40 bg-pal-crimson/10 px-4 py-3 text-sm font-semibold text-status-dropped">
            {error}
          </div>
        ) : null}

        {viewer ? (
          <CollectionView
            franchises={franchises}
            type={type}
            statusFilter={statusFilter}
            edits={edits}
            scoreFormat={viewer.mediaListOptions?.scoreFormat}
            loading={loading}
            totalEntries={entries.length}
            isChanged={isChanged}
            onPatch={onPatch}
            onOpen={setModalMediaId}
          />
        ) : null}
      </main>

      <SaveBar
        count={changedCount}
        saving={saving}
        progress={saveProgress}
        onSave={handleSave}
        onDiscard={discard}
      />

      {modalMediaId != null ? (
        <MediaModal
          mediaId={modalMediaId}
          token={token}
          scoreFormat={viewer?.mediaListOptions?.scoreFormat}
          onClose={() => setModalMediaId(null)}
          onChanged={() => loadList(true)}
          notify={notify}
        />
      ) : null}

      <Toasts toasts={toasts} onDismiss={dismissToast} />
    </div>
  );
}
