"use client";

import Link from "next/link";
import { IconLogout, IconRefresh } from "./Icons";
import { ACCENT } from "@/web/lib/config";
import type { MediaType, Viewer } from "@/web/lib/types";

/**
 * Top bar of the web app: logo, Anime/Manga switch and account controls. The web app is only
 * the collection (series rows and batch editing); everything else lives in the Android app.
 */
export default function Header({
  viewer,
  type,
  onTypeChange,
  onRefresh,
  refreshing,
  onLogout,
}: {
  viewer: Viewer;
  type: MediaType;
  onTypeChange: (t: MediaType) => void;
  onRefresh: () => void;
  refreshing: boolean;
  onLogout: () => void;
}) {
  return (
    <header className="sticky top-0 z-40 bg-bg-fg">
      <div className="mx-auto flex h-[60px] max-w-site items-center gap-3 px-4 sm:h-[68px] sm:px-6">
        <Link href="/" className="flex flex-shrink-0 items-center gap-2" title="Arkhime">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/web/arkhime-96.png"
            alt="Arkhime"
            width={34}
            height={34}
            className="h-[30px] w-[30px] rounded object-cover sm:h-[34px] sm:w-[34px]"
          />
          <span className="hidden text-[19px] font-bold tracking-tight text-fg-bright sm:block">
            Ark<span style={{ color: ACCENT }}>hime</span>
          </span>
        </Link>

        {/* Anime / Manga */}
        <div className="mx-auto flex items-center gap-1 rounded bg-bg p-1">
          {(["ANIME", "MANGA"] as MediaType[]).map((t) => (
            <button
              key={t}
              onClick={() => onTypeChange(t)}
              className={`rounded px-3 py-1.5 text-[13px] font-bold transition-colors ${
                type === t ? "text-white" : "text-fg-light hover:text-fg-bright"
              }`}
              style={type === t ? { backgroundColor: ACCENT } : undefined}
            >
              {t === "ANIME" ? "Anime" : "Manga"}
            </button>
          ))}
        </div>

        <div className="flex flex-shrink-0 items-center gap-2">
          <button
            onClick={onRefresh}
            disabled={refreshing}
            title="Reload from AniList"
            aria-label="Reload from AniList"
            className="flex h-9 w-9 items-center justify-center text-fg-light transition-colors hover:text-fg-bright disabled:opacity-40"
          >
            <IconRefresh size={16} className={refreshing ? "animate-spin" : ""} />
          </button>

          <a href={viewer.siteUrl ?? "https://anilist.co"} target="_blank" rel="noreferrer" title={`${viewer.name} on AniList`}>
            {viewer.avatar?.medium ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={viewer.avatar.medium}
                alt={viewer.name}
                className="h-[34px] w-auto max-w-[56px] rounded object-contain"
              />
            ) : null}
          </a>

          <button
            onClick={onLogout}
            title="Log out"
            aria-label="Log out"
            className="flex h-9 w-9 items-center justify-center text-fg-light transition-colors hover:text-status-dropped"
          >
            <IconLogout size={16} />
          </button>
        </div>
      </div>
    </header>
  );
}
