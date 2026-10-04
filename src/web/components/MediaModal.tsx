"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import EntryEditor from "./EntryEditor";
import { DataList, Meter, SectionTitle, Tag } from "./ui";
import {
  IconChevronLeft,
  IconClose,
  IconExternal,
  IconHeart,
  IconPlay,
  IconStar,
} from "./Icons";
import {
  AniListError,
  deleteMediaListEntry,
  fetchMediaDetail,
  fetchReviews,
  saveMediaListEntry,
  toggleFavourite,
  type SaveInput,
} from "@/web/lib/anilist";
import { slotFromMedia } from "@/web/lib/franchise";
import {
  airingCountdown,
  compactNumber,
  contrastText,
  dateRangeLabel,
  formatFuzzyDate,
  mediaStatusLabel,
  preferredTitle,
  prettyFormat,
  PALETTE,
  relationLabel,
  relativeTime,
  scoreColor,
  seasonLabel,
  statusColor,
  statusLabel,
  stripHtml,
  titleCaseEnum,
  withAlpha,
} from "@/web/lib/format";
import type { MediaDetail, Review, ScoreFormat } from "@/web/lib/types";

type Tab = "OVERVIEW" | "WATCH" | "CHARACTERS" | "STAFF" | "STATS";

const TABS: { id: Tab; label: string }[] = [
  { id: "OVERVIEW", label: "Overview" },
  { id: "WATCH", label: "Watch" },
  { id: "CHARACTERS", label: "Characters" },
  { id: "STAFF", label: "Staff" },
  { id: "STATS", label: "Stats" },
];

/**
 * The media page, laid out the way AniList does it: a clean banner, the
 * cover overlapping its lower edge with the synopsis beside it, a centred
 * tab strip, then a narrow sidebar of facts next to the main content.
 * Keeps a small navigation stack so following a relation is undoable.
 */
export default function MediaModal({
  mediaId,
  token,
  scoreFormat,
  onClose,
  onChanged,
  notify,
}: {
  mediaId: number;
  token: string | null;
  scoreFormat: ScoreFormat | null | undefined;
  onClose: () => void;
  onChanged: () => void;
  notify: (message: string, kind?: "info" | "success" | "error") => void;
}) {
  const [stack, setStack] = useState<number[]>([mediaId]);
  const current = stack[stack.length - 1];

  const [detail, setDetail] = useState<MediaDetail | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("OVERVIEW");
  const [saving, setSaving] = useState(false);
  const [favouriting, setFavouriting] = useState(false);
  const [editing, setEditing] = useState(false);

  // A different title opened from outside starts a fresh stack
  const [openedFor, setOpenedFor] = useState(mediaId);
  if (openedFor !== mediaId) {
    setOpenedFor(mediaId);
    setStack([mediaId]);
  }

  const load = useCallback(
    async (id: number) => {
      setLoading(true);
      setError(null);
      try {
        const d = await fetchMediaDetail(id, token);
        setDetail(d);
        setReviews([]);
        fetchReviews(id, token)
          .then(setReviews)
          .catch(() => setReviews([]));
      } catch (e) {
        setError(e instanceof Error ? e.message : "Could not load this title.");
      } finally {
        setLoading(false);
      }
    },
    [token],
  );

  // Moving to another title resets the view, then its details load
  const [shownId, setShownId] = useState<number | null>(null);
  if (shownId !== current) {
    setShownId(current);
    setTab("OVERVIEW");
    setEditing(false);
    setLoading(true);
    setError(null);
  }

  useEffect(() => {
    let cancelled = false;
    fetchMediaDetail(current, token)
      .then((d) => {
        if (cancelled) return;
        setDetail(d);
        setReviews([]);
        fetchReviews(current, token)
          .then((r) => !cancelled && setReviews(r))
          .catch(() => !cancelled && setReviews([]));
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "Could not load this title.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [current, token]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== "Escape") return;
      setStack((s) => {
        if (s.length > 1) return s.slice(0, -1);
        onClose();
        return s;
      });
    };
    document.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  const push = useCallback((id: number) => setStack((s) => [...s, id]), []);
  const back = useCallback(() => setStack((s) => s.slice(0, -1)), []);

  const slot = useMemo(() => (detail ? slotFromMedia(detail) : null), [detail]);

  const handleSave = useCallback(
    async (input: SaveInput) => {
      if (!token) return;
      setSaving(true);
      try {
        await saveMediaListEntry(token, input);
        notify("Saved to AniList.", "success");
        await load(input.mediaId);
        onChanged();
        setEditing(false);
      } catch (e) {
        notify(
          e instanceof AniListError ? e.message : "Could not save this entry.",
          "error",
        );
      } finally {
        setSaving(false);
      }
    },
    [token, load, onChanged, notify],
  );

  const handleDelete = useCallback(async () => {
    if (!token || !slot?.entryId) return;
    setSaving(true);
    try {
      await deleteMediaListEntry(token, slot.entryId);
      notify("Removed from your list.", "success");
      await load(current);
      onChanged();
      setEditing(false);
    } catch (e) {
      notify(
        e instanceof AniListError ? e.message : "Could not delete this entry.",
        "error",
      );
    } finally {
      setSaving(false);
    }
  }, [token, slot, current, load, onChanged, notify]);

  const handleFavourite = useCallback(async () => {
    if (!token || !detail) return;
    setFavouriting(true);
    const wasFav = !!detail.isFavourite;
    setDetail({ ...detail, isFavourite: !wasFav });
    try {
      await toggleFavourite(
        token,
        detail.type === "ANIME"
          ? { animeId: detail.id }
          : { mangaId: detail.id },
      );
      notify(
        wasFav ? "Removed from favourites." : "Added to favourites.",
        "success",
      );
    } catch {
      setDetail((d) => (d ? { ...d, isFavourite: wasFav } : d));
      notify("Could not update favourites.", "error");
    } finally {
      setFavouriting(false);
    }
  }, [token, detail, notify]);

  const title = detail ? preferredTitle(detail.title) : "";
  const listColor = statusColor(slot?.status);

  return (
    <div
      className="fixed inset-0 z-[70] overflow-y-auto bg-black/70 sm:px-4 sm:py-6"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-label={title || "Media details"}
    >
      <div className="animate-fade relative mx-auto min-h-full w-full max-w-[1000px] bg-bg sm:min-h-0 sm:rounded sm:shadow-menu">
        {/* Floating controls */}
        <div className="absolute right-3 top-3 z-20 flex items-center gap-2">
          {stack.length > 1 ? (
            <button
              onClick={back}
              className="flex h-9 w-9 items-center justify-center rounded bg-[#000000]/80 text-fg-bright transition-colors hover:bg-[#000000]"
              aria-label="Back"
            >
              <IconChevronLeft size={17} />
            </button>
          ) : null}
          <button
            onClick={onClose}
            className="flex h-9 w-9 items-center justify-center rounded bg-[#000000]/80 text-fg-bright transition-colors hover:bg-[#000000]"
            aria-label="Close"
          >
            <IconClose size={17} />
          </button>
        </div>

        {loading && !detail ? (
          <div className="p-6">
            <div className="skeleton h-40 w-full" />
            <div className="skeleton mt-4 h-6 w-2/3" />
            <div className="skeleton mt-2 h-4 w-1/3" />
            <div className="skeleton mt-6 h-40 w-full" />
          </div>
        ) : error ? (
          <div className="p-12 text-center">
            <p className="text-[15px] font-bold text-status-dropped">{error}</p>
          </div>
        ) : detail ? (
          <>
            {/* ---------- Banner (clean, no scrim) ---------- */}
            {detail.bannerImage ? (
              <div className="h-[120px] w-full overflow-hidden bg-bg-300 sm:h-[200px] sm:rounded-t">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={detail.bannerImage}
                  alt=""
                  className="h-full w-full object-cover"
                />
              </div>
            ) : (
              <div className="h-[80px] w-full bg-bg-fg sm:rounded-t" />
            )}

            {/* ---------- Header: cover + synopsis ---------- */}
            <div className="bg-bg-fg">
              <div className="mx-auto flex max-w-[940px] gap-4 px-4 pb-4 sm:gap-6 sm:px-6">
                <div className="-mt-10 w-[100px] flex-shrink-0 sm:-mt-24 sm:w-[160px]">
                  <div className="cover shadow-card">
                    {detail.coverImage?.extraLarge ||
                    detail.coverImage?.large ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={
                          detail.coverImage.extraLarge ||
                          detail.coverImage.large!
                        }
                        alt={title}
                      />
                    ) : null}
                  </div>

                  {token && slot ? (
                    <div className="mt-2 flex gap-1.5">
                      <button
                        onClick={() => setEditing((v) => !v)}
                        className="flex-1 rounded px-2 py-2 text-[12px] font-bold transition-opacity hover:opacity-90"
                        style={{
                          backgroundColor: slot.onList ? listColor : "#ff4f6d",
                          color: contrastText(
                            slot.onList ? listColor : "#ff4f6d",
                          ),
                        }}
                      >
                        {slot.onList
                          ? statusLabel(slot.status!, detail.type)
                          : "Add to List"}
                      </button>
                      <button
                        onClick={handleFavourite}
                        disabled={favouriting}
                        title={detail.isFavourite ? "Unfavourite" : "Favourite"}
                        className="flex w-9 items-center justify-center rounded transition-colors disabled:opacity-50"
                        style={{
                          backgroundColor: detail.isFavourite
                            ? "#4a4a50"
                            : "#2a2a2e",
                          color: detail.isFavourite ? "#fff" : "#b4b4bb",
                        }}
                      >
                        <IconHeart size={15} filled={!!detail.isFavourite} />
                      </button>
                    </div>
                  ) : null}
                </div>

                <div className="min-w-0 flex-1 pt-3 sm:pt-4">
                  <h2 className="text-[19px] font-bold leading-tight text-fg-bright sm:text-[26px]">
                    {title}
                  </h2>

                  <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12.5px] text-fg-light">
                    <span>{prettyFormat(detail.format)}</span>
                    {detail.seasonYear ? (
                      <span>
                        {seasonLabel(detail.season, detail.seasonYear)}
                      </span>
                    ) : null}
                    <span>{mediaStatusLabel(detail.status)}</span>
                    {detail.averageScore ? (
                      <span
                        className="inline-flex items-center gap-1 font-semibold"
                        style={{ color: scoreColor(detail.averageScore) }}
                      >
                        <IconStar size={11} filled />
                        {detail.averageScore}%
                      </span>
                    ) : null}
                    {detail.favourites ? (
                      <span className="inline-flex items-center gap-1">
                        <IconHeart size={11} filled />{" "}
                        {compactNumber(detail.favourites)}
                      </span>
                    ) : null}
                  </div>

                  {airingCountdown(detail.nextAiringEpisode) ? (
                    <p className="mt-1.5 text-[12.5px] font-semibold text-[color:var(--accent)]">
                      {airingCountdown(detail.nextAiringEpisode)}
                    </p>
                  ) : null}

                  {/* Synopsis sits next to the cover, as on AniList. */}
                  <p className="clamp-4 mt-3 hidden text-[13.5px] leading-relaxed text-fg sm:block">
                    {stripHtml(detail.description) || "No synopsis available."}
                  </p>

                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    {detail.siteUrl ? (
                      <a
                        href={detail.siteUrl}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 text-[12px] font-semibold text-fg-light transition-colors hover:text-fg-bright"
                      >
                        <IconExternal size={12} /> AniList
                      </a>
                    ) : null}
                    {detail.trailer?.id && detail.trailer.site === "youtube" ? (
                      <a
                        href={`https://www.youtube.com/watch?v=${detail.trailer.id}`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 text-[12px] font-semibold text-fg-light transition-colors hover:text-fg-bright"
                      >
                        <IconPlay size={12} /> Trailer
                      </a>
                    ) : null}
                  </div>
                </div>
              </div>

              {/* ---------- Centred tab strip ---------- */}
              <div className="no-scrollbar flex justify-start gap-1 overflow-x-auto px-4 sm:justify-center sm:px-6">
                {TABS.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => setTab(t.id)}
                    className={`relative whitespace-nowrap px-3 py-2.5 text-[13.5px] font-semibold transition-colors ${
                      tab === t.id
                        ? "text-fg-bright"
                        : "text-fg-light hover:text-fg-bright"
                    }`}
                  >
                    {t.label}
                    {tab === t.id ? (
                      <span className="absolute inset-x-2 bottom-0 h-[3px] rounded-sm bg-[color:var(--accent)]" />
                    ) : null}
                  </button>
                ))}
              </div>
            </div>

            {/* ---------- Inline editor ---------- */}
            {editing && token && slot ? (
              <div className="mx-auto max-w-[940px] px-4 pt-5 sm:px-6">
                <EntryEditor
                  slot={slot}
                  scoreFormat={scoreFormat}
                  saving={saving}
                  onSave={handleSave}
                  onDelete={slot.entryId ? handleDelete : null}
                />
              </div>
            ) : null}

            {/* ---------- Body ---------- */}
            <div className="mx-auto max-w-[940px] px-4 pb-10 pt-5 sm:px-6">
              {tab === "OVERVIEW" ? (
                <Overview detail={detail} onOpen={push} />
              ) : null}
              {tab === "WATCH" ? <WatchTab detail={detail} /> : null}
              {tab === "CHARACTERS" ? <CharactersTab detail={detail} /> : null}
              {tab === "STAFF" ? <StaffTab detail={detail} /> : null}
              {tab === "STATS" ? (
                <StatsTab detail={detail} reviews={reviews} />
              ) : null}
            </div>
          </>
        ) : null}
      </div>
    </div>
  );
}

/* ===================== Overview ===================== */

function Overview({
  detail,
  onOpen,
}: {
  detail: MediaDetail;
  onOpen: (id: number) => void;
}) {
  const [showSpoilers, setShowSpoilers] = useState(false);
  const isAnime = detail.type === "ANIME";
  const safeTags = (detail.tags ?? []).filter((t) => !t.isMediaSpoiler);
  const spoilerTags = (detail.tags ?? []).filter((t) => t.isMediaSpoiler);
  const recs = (detail.recommendations?.nodes ?? [])
    .map((n) => n.mediaRecommendation)
    .filter(Boolean)
    .slice(0, 12);
  const relations = detail.relations?.edges ?? [];

  const facts: { label: string; value: React.ReactNode }[] = [
    { label: "Format", value: prettyFormat(detail.format) },
    {
      label: isAnime ? "Episodes" : "Chapters",
      value: (isAnime ? detail.episodes : detail.chapters)?.toString() ?? "—",
    },
  ];
  if (!isAnime)
    facts.push({ label: "Volumes", value: detail.volumes?.toString() ?? "—" });
  if (isAnime)
    facts.push({
      label: "Episode Duration",
      value: detail.duration ? `${detail.duration} mins` : "—",
    });
  facts.push(
    { label: "Status", value: mediaStatusLabel(detail.status) },
    { label: "Start Date", value: formatFuzzyDate(detail.startDate) ?? "—" },
    {
      label: "Aired",
      value:
        dateRangeLabel(detail.startDate, detail.endDate, detail.status) ?? "—",
    },
  );
  if (detail.season)
    facts.push({
      label: "Season",
      value: seasonLabel(detail.season, detail.seasonYear),
    });
  if (detail.studios?.nodes?.length)
    facts.push({
      label: "Studios",
      value: detail.studios.nodes.map((s) => s.name).join(", "),
    });
  if (detail.source)
    facts.push({ label: "Source", value: titleCaseEnum(detail.source) });
  facts.push(
    {
      label: "Mean Score",
      value: detail.meanScore ? `${detail.meanScore}%` : "—",
    },
    { label: "Popularity", value: compactNumber(detail.popularity) },
    { label: "Favourites", value: compactNumber(detail.favourites) },
  );
  if (detail.synonyms?.length)
    facts.push({
      label: "Synonyms",
      value: detail.synonyms.slice(0, 5).join(", "),
    });

  return (
    <div className="grid gap-6 lg:grid-cols-[230px,1fr]">
      {/* Sidebar */}
      <aside className="min-w-0 space-y-3">
        {detail.rankings?.length
          ? detail.rankings
              .filter((r) => r.rank <= 100 && r.allTime)
              .slice(0, 2)
              .map((r) => (
                <div
                  key={r.id}
                  className="surface flex items-center gap-2 rounded px-3 py-2.5 text-[12.5px] font-semibold text-fg-bright"
                >
                  {r.type === "RATED" ? (
                    <IconStar
                      size={13}
                      filled
                      className="text-[color:var(--accent)]"
                    />
                  ) : (
                    <IconHeart
                      size={13}
                      filled
                      className="text-status-dropped"
                    />
                  )}
                  #{r.rank} {r.context}
                </div>
              ))
          : null}
        <DataList rows={facts} />
      </aside>

      {/* Main */}
      <div className="min-w-0">
        {/* On phones the synopsis is hidden up top, so show it here. */}
        <p className="mb-6 text-[13.5px] leading-relaxed text-fg sm:hidden">
          {stripHtml(detail.description) || "No synopsis available."}
        </p>

        {detail.genres?.length ? (
          <div className="mb-6">
            <SectionTitle title="Genres" />
            <div className="flex flex-wrap gap-1.5">
              {detail.genres.map((g, i) => (
                <Tag key={g} color={PALETTE[i % PALETTE.length]} solid>
                  {g}
                </Tag>
              ))}
            </div>
          </div>
        ) : null}

        {relations.length ? (
          <div className="mb-6">
            <SectionTitle title="Relations" />
            <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4 pb-1 sm:mx-0 sm:px-0">
              {relations.slice(0, 12).map((e) => (
                <button
                  key={`${e.node.id}-${e.relationType}`}
                  onClick={() => onOpen(e.node.id)}
                  className="card w-[104px] flex-shrink-0 self-start text-left"
                >
                  <span className="cover">
                    {e.node.coverImage?.large ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={e.node.coverImage.large}
                        alt=""
                        loading="lazy"
                      />
                    ) : null}
                    <span className="cover-caption truncate text-[10.5px] font-bold uppercase tracking-wide text-fg-bright">
                      {relationLabel(e.relationType)}
                    </span>
                  </span>
                  <span className="card-title clamp-2 block">
                    {preferredTitle(e.node.title)}
                  </span>
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {safeTags.length ? (
          <div className="mb-6">
            <SectionTitle title="Tags" />
            <div className="flex flex-wrap gap-1.5">
              {safeTags.slice(0, 20).map((t) => (
                <Tag key={t.id} title={t.description ?? undefined}>
                  {t.name}
                  {t.rank ? (
                    <span className="opacity-60">{t.rank}%</span>
                  ) : null}
                </Tag>
              ))}
              {spoilerTags.length && !showSpoilers ? (
                <button
                  onClick={() => setShowSpoilers(true)}
                  className="rounded-sm px-2 py-1 text-[11.5px] font-semibold text-status-dropped"
                  style={{ backgroundColor: withAlpha("#4a4a50", 0.18) }}
                >
                  Show {spoilerTags.length} spoiler tags
                </button>
              ) : null}
              {showSpoilers
                ? spoilerTags.map((t) => (
                    <Tag key={t.id} color="#4a4a50">
                      {t.name}
                      {t.rank ? (
                        <span className="opacity-60">{t.rank}%</span>
                      ) : null}
                    </Tag>
                  ))
                : null}
            </div>
          </div>
        ) : null}

        {recs.length ? (
          <div>
            <SectionTitle title="Recommendations" />
            <div className="no-scrollbar -mx-4 flex gap-3 overflow-x-auto px-4 pb-1 sm:mx-0 sm:px-0">
              {recs.map((m) => (
                <button
                  key={m!.id}
                  onClick={() => onOpen(m!.id)}
                  className="card w-[104px] flex-shrink-0 self-start text-left"
                >
                  <span className="cover">
                    {m!.coverImage?.large ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={m!.coverImage.large} alt="" loading="lazy" />
                    ) : null}
                  </span>
                  <span className="card-title clamp-2 block">
                    {preferredTitle(m!.title)}
                  </span>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}

/* ===================== Watch ===================== */

function WatchTab({ detail }: { detail: MediaDetail }) {
  const streaming = detail.streamingEpisodes ?? [];
  const links = detail.externalLinks ?? [];
  if (!streaming.length && !links.length)
    return <p className="text-[13px] text-fg-light">No links available.</p>;

  return (
    <div className="space-y-6">
      {links.length ? (
        <div>
          <SectionTitle title="External & Streaming" />
          <div className="flex flex-wrap gap-2">
            {links.map((l) => {
              const c =
                l.color && /^#[0-9a-f]{6}$/i.test(l.color)
                  ? l.color
                  : "#e6a23c";
              return (
                <a
                  key={l.id}
                  href={l.url ?? "#"}
                  target="_blank"
                  rel="noreferrer"
                  className="surface inline-flex items-center gap-2 rounded px-3 py-2 text-[12.5px] font-semibold text-fg-bright transition-colors hover:bg-bg-300"
                >
                  <span
                    className="h-2.5 w-2.5 rounded-sm"
                    style={{ backgroundColor: c }}
                  />
                  {l.site}
                  {l.language ? (
                    <span className="text-fg-light">{l.language}</span>
                  ) : null}
                </a>
              );
            })}
          </div>
        </div>
      ) : null}

      {streaming.length ? (
        <div>
          <SectionTitle title="Episodes" count={streaming.length} />
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {streaming.slice(0, 24).map((ep, i) => (
              <a
                key={`${ep.url}-${i}`}
                href={ep.url ?? "#"}
                target="_blank"
                rel="noreferrer"
                className="group"
              >
                <div className="aspect-video w-full overflow-hidden rounded bg-bg-300">
                  {ep.thumbnail ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={ep.thumbnail}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover"
                    />
                  ) : null}
                </div>
                <p className="clamp-2 mt-1.5 text-[12px] font-semibold text-fg transition-colors group-hover:text-[color:var(--accent)]">
                  {ep.title}
                </p>
              </a>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}

/* ===================== Characters / Staff ===================== */

function PersonGrid({
  people,
}: {
  people: {
    key: string;
    name: string;
    role: string;
    image: string | null;
    url: string | null;
    rightName?: string | null;
    rightRole?: string | null;
    rightImage?: string | null;
    rightUrl?: string | null;
  }[];
}) {
  return (
    <div className="grid gap-2.5 sm:grid-cols-2">
      {people.map((p) => (
        <div
          key={p.key}
          className="surface flex items-stretch justify-between overflow-hidden rounded"
        >
          <a
            href={p.url ?? "#"}
            target="_blank"
            rel="noreferrer"
            className="flex min-w-0 flex-1 items-center gap-2.5"
          >
            <span className="h-[62px] w-[44px] flex-shrink-0 overflow-hidden bg-bg-300">
              {p.image ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={p.image}
                  alt=""
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              ) : null}
            </span>
            <span className="min-w-0 py-1.5">
              <span className="clamp-2 block text-[12.5px] font-semibold text-fg-bright">
                {p.name}
              </span>
              <span className="block truncate text-[11px] text-fg-light">
                {p.role}
              </span>
            </span>
          </a>

          {p.rightName ? (
            <a
              href={p.rightUrl ?? "#"}
              target="_blank"
              rel="noreferrer"
              className="flex min-w-0 flex-1 items-center justify-end gap-2.5 text-right"
            >
              <span className="min-w-0 py-1.5">
                <span className="clamp-2 block text-[12.5px] font-semibold text-fg">
                  {p.rightName}
                </span>
                <span className="block truncate text-[11px] text-fg-light">
                  {p.rightRole}
                </span>
              </span>
              <span className="h-[62px] w-[44px] flex-shrink-0 overflow-hidden bg-bg-300">
                {p.rightImage ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={p.rightImage}
                    alt=""
                    loading="lazy"
                    className="h-full w-full object-cover"
                  />
                ) : null}
              </span>
            </a>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function CharactersTab({ detail }: { detail: MediaDetail }) {
  const edges = detail.characters?.edges ?? [];
  if (!edges.length)
    return <p className="text-[13px] text-fg-light">No character data.</p>;
  return (
    <PersonGrid
      people={edges.map((e) => {
        const va = e.voiceActors?.[0];
        return {
          key: `${e.node.id}-${e.id}`,
          name: e.node.name?.full ?? "",
          role: titleCaseEnum(e.role),
          image: e.node.image?.large ?? null,
          url: e.node.siteUrl,
          rightName: va?.name?.full ?? null,
          rightRole: va?.languageV2 ?? null,
          rightImage: va?.image?.large ?? null,
          rightUrl: va?.siteUrl ?? null,
        };
      })}
    />
  );
}

function StaffTab({ detail }: { detail: MediaDetail }) {
  const edges = detail.staff?.edges ?? [];
  if (!edges.length)
    return <p className="text-[13px] text-fg-light">No staff data.</p>;
  return (
    <PersonGrid
      people={edges.map((e) => ({
        key: `${e.node.id}-${e.id}`,
        name: e.node.name?.full ?? "",
        role: e.role ?? "",
        image: e.node.image?.large ?? null,
        url: e.node.siteUrl,
      }))}
    />
  );
}

/* ===================== Stats ===================== */

function StatsTab({
  detail,
  reviews,
}: {
  detail: MediaDetail;
  reviews: Review[];
}) {
  const scores = detail.stats?.scoreDistribution ?? [];
  const statuses = detail.stats?.statusDistribution ?? [];
  const maxScore = Math.max(1, ...scores.map((s) => s.amount));
  const statusTotal = statuses.reduce((a, s) => a + s.amount, 0) || 1;

  return (
    <div className="space-y-7">
      {scores.length ? (
        <div>
          <SectionTitle title="Score Distribution" />
          <div className="surface rounded p-4">
            <div className="flex h-36 gap-1.5">
              {scores.map((s) => (
                <div
                  key={s.score}
                  className="flex h-full flex-1 flex-col items-center gap-1"
                  title={`${s.amount} users scored ${s.score}`}
                >
                  <span className="text-[9.5px] tabular-nums text-fg-light">
                    {compactNumber(s.amount)}
                  </span>
                  <div className="relative w-full flex-1">
                    <div
                      className="absolute inset-x-0 bottom-0 rounded-t-sm"
                      style={{
                        height: `${Math.max(2, (s.amount / maxScore) * 100)}%`,
                        backgroundColor: scoreColor(s.score),
                      }}
                    />
                  </div>
                  <span className="text-[10px] font-semibold tabular-nums text-fg-light">
                    {s.score}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : null}

      {statuses.length ? (
        <div>
          <SectionTitle title="Status Distribution" />
          <div className="surface space-y-2.5 rounded p-4">
            {statuses.map((s) => (
              <div key={s.status} className="flex items-center gap-3">
                <span
                  className="w-24 flex-shrink-0 text-[12px] font-semibold"
                  style={{ color: statusColor(s.status) }}
                >
                  {statusLabel(s.status, detail.type)}
                </span>
                <Meter
                  value={s.amount}
                  max={statusTotal}
                  accent={statusColor(s.status)}
                  height={8}
                />
                <span className="w-14 flex-shrink-0 text-right text-[12px] tabular-nums text-fg-light">
                  {compactNumber(s.amount)}
                </span>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {reviews.length ? (
        <div>
          <SectionTitle title="Reviews" count={reviews.length} />
          <div className="space-y-2">
            {reviews.map((r) => (
              <a
                key={r.id}
                href={r.siteUrl ?? "#"}
                target="_blank"
                rel="noreferrer"
                className="surface block rounded p-3 transition-colors hover:bg-bg-300"
              >
                <div className="mb-1.5 flex items-center gap-2">
                  {r.user?.avatar?.large ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={r.user.avatar.large}
                      alt=""
                      className="h-6 w-auto max-w-[36px] rounded-sm object-contain"
                    />
                  ) : null}
                  <span className="text-[12px] font-semibold text-fg-bright">
                    {r.user?.name}
                  </span>
                  {r.score ? (
                    <span
                      className="rounded-sm px-1.5 py-0.5 text-[11px] font-bold"
                      style={{
                        color: scoreColor(r.score),
                        backgroundColor: withAlpha(scoreColor(r.score), 0.16),
                      }}
                    >
                      {r.score}
                    </span>
                  ) : null}
                  <span className="ml-auto text-[11px] text-fg-light">
                    {relativeTime(r.createdAt)}
                  </span>
                </div>
                <p className="clamp-3 text-[13px] leading-relaxed text-fg">
                  {r.summary}
                </p>
              </a>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
