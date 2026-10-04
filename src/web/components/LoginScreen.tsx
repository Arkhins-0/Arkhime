"use client";

import { ANILIST_CLIENT_ID, getAuthorizeUrl, markWebLogin } from "@/web/lib/config";
import { PALETTE } from "@/web/lib/format";

const FEATURES: { label: string; detail: string; accent: string }[] = [
  {
    label: "Series rows",
    detail: "Every season, spin-off and movie grouped into one row",
    accent: "#ff4f6d",
  },
  {
    label: "Mass edit",
    detail: "Change progress, status and scores, then save them in one batch",
    accent: "#ffb59a",
  },
  {
    label: "Filters and sort",
    detail: "By status, genre and format; sort by score, progress or release",
    accent: "#ffc768",
  },
  {
    label: "Not yet added",
    detail: "Related seasons you have not added show up in each series",
    accent: "#a98bd9",
  },
];

export default function LoginScreen({ error }: { error?: string | null }) {
  const misconfigured = !ANILIST_CLIENT_ID;

  return (
    <div className="flex min-h-screen flex-col">
      {/* The palette itself, as nine flat bands across the top. */}
      <div className="flex h-2 w-full flex-shrink-0">
        {PALETTE.map((c) => (
          <span key={c} className="flex-1" style={{ backgroundColor: c }} />
        ))}
      </div>

      <div className="flex flex-1 items-center justify-center px-4 py-8 sm:px-6 sm:py-12">
        <div className="animate-fade w-full max-w-4xl">
          <div className="grid gap-4 lg:grid-cols-[1.05fr,1fr]">
            {/* ---------------- Pitch ---------------- */}
            <div className="surface rounded p-6 sm:p-8">
              <div className="mb-5 flex items-center gap-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src="/web/arkhime-192.png"
                  alt="Arkhime"
                  width={56}
                  height={56}
                  className="h-12 w-12 flex-shrink-0 object-cover sm:h-14 sm:w-14"
                  style={{ borderBottom: "3px solid #ff4f6d" }}
                />
                <h1 className="font-sans text-3xl font-extrabold tracking-tight sm:text-4xl">
                  Ark<span className="text-pal-teal">hime</span>
                </h1>
              </div>

              <p className="mb-6 text-[14px] leading-relaxed text-fg-light sm:text-[15px]">
                Arkhime on the web: your AniList collection grouped by series,
                with batch editing that saves straight back to AniList. Everything
                else is in the Arkhime app for Android.
              </p>

              {misconfigured ? (
                <div className="border-l-4 border-status-dropped bg-bg-fg px-4 py-3 text-sm text-fg">
                  This deployment is missing its AniList Client ID. Set{" "}
                  <span className="font-mono text-status-dropped">
                    NEXT_PUBLIC_ANILIST_CLIENT_ID
                  </span>{" "}
                  in the environment and redeploy.
                </div>
              ) : (
                <a
                  href={getAuthorizeUrl()}
                  onClick={markWebLogin}
                  className="inline-flex w-full items-center justify-center bg-[color:var(--accent)] px-8 py-3.5 text-[15px] font-extrabold text-white transition-opacity hover:opacity-90 active:opacity-80 sm:w-auto"
                >
                  Login with AniList
                </a>
              )}

              {error ? (
                <div className="mt-5 border-l-4 border-status-dropped bg-bg-fg px-4 py-3 text-sm text-fg">
                  {error}
                </div>
              ) : null}

              <div className="mt-7 border-l-4 border-pal-steel bg-bg-grey px-4 py-3.5 text-left text-xs leading-relaxed text-fg-light">
                <p className="mb-1 font-bold text-fg-light">
                  Anyone can sign in here
                </p>
                Authorize with your own AniList account — your token stays in
                your browser, and nothing is stored on a server.
              </div>
            </div>

            {/* ---------------- Feature grid ---------------- */}
            <div className="grid grid-cols-2 gap-2.5 sm:gap-3">
              {FEATURES.map((f) => (
                <div
                  key={f.label}
                  className="surface rounded relative overflow-hidden p-3 pl-4 sm:p-4 sm:pl-5"
                >
                  <span
                    className="absolute inset-y-0 left-0 w-1.5"
                    style={{ backgroundColor: f.accent }}
                  />
                  <p
                    className="font-sans text-[12.5px] font-extrabold uppercase tracking-wide sm:text-sm"
                    style={{ color: f.accent }}
                  >
                    {f.label}
                  </p>
                  <p className="mt-1 text-[11px] leading-snug text-fg-light sm:text-[11.5px]">
                    {f.detail}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
