"use client";

import { useEffect, useMemo, useSyncExternalStore } from "react";
import { APP_SCHEME } from "@/lib/site";

/*
 * https://arkhime.arkhins.com is registered as the OAuth redirect URL.
 *   AniList (implicit grant) lands here with  #access_token=...&token_type=Bearer&...
 *   MyAnimeList (PKCE)       lands here with  ?code=...&state=...
 * Both are handed to the Android app through its custom scheme; the app parses them in
 * connections/anilist/Login.kt and connections/mal/Login.kt.
 */

type Forward =
  | { kind: "anilist" | "mal"; url: string }
  | { kind: "error"; message: string };

function parse(href: string): Forward | null {
  const url = new URL(href);
  const hash = url.hash.slice(1);
  const fragment = new URLSearchParams(hash);
  const query = url.searchParams;

  if (fragment.has("access_token")) {
    return { kind: "anilist", url: `${APP_SCHEME}://anilist#${hash}` };
  }
  if (query.has("code")) {
    return { kind: "mal", url: `${APP_SCHEME}://mal?${query.toString()}` };
  }
  const error = query.get("error") ?? fragment.get("error");
  if (error) {
    return {
      kind: "error",
      message: query.get("error_description") ?? fragment.get("error_description") ?? error,
    };
  }
  return null;
}

// Capture the landing URL once: the effect below strips the token from the address bar,
// and the overlay must keep showing the original result after that.
let landingHref: string | null = null;
const subscribe = () => () => {};
const getSnapshot = () => (landingHref ??= window.location.href);
const getServerSnapshot = () => null;

export default function AuthRedirect() {
  const href = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
  const forward = useMemo(() => (href ? parse(href) : null), [href]);

  useEffect(() => {
    if (!forward) return;
    // keep the token out of history and anything the page might share later
    window.history.replaceState(null, "", window.location.pathname);
    if (forward.kind !== "error") window.location.href = forward.url;
  }, [forward]);

  if (!forward) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/95 px-4 backdrop-blur">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-card p-6 text-center shadow-xl">
        {forward.kind === "error" ? (
          <>
            <h1 className="text-xl font-semibold">Login didn&apos;t finish</h1>
            <p className="mt-2 text-sm text-muted">{forward.message}</p>
            <p className="mt-4 text-sm text-muted">Go back to Arkhime and try logging in again.</p>
          </>
        ) : (
          <>
            <h1 className="text-xl font-semibold">
              Signing you in to {forward.kind === "anilist" ? "AniList" : "MyAnimeList"}
            </h1>
            <p className="mt-2 text-sm text-muted">
              Arkhime should open on its own. If it doesn&apos;t, tap the button below.
            </p>
            <a
              href={forward.url}
              className="mt-6 inline-flex w-full items-center justify-center rounded-full bg-accent-solid px-5 py-3 font-medium text-accent-contrast hover:opacity-90"
            >
              Open Arkhime
            </a>
            <p className="mt-4 text-xs text-muted">This only works on the phone where Arkhime is installed.</p>
          </>
        )}
      </div>
    </div>
  );
}
