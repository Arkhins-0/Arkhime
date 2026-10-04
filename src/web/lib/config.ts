// AniList OAuth configuration.
// Uses the "Implicit Grant" flow so everything runs client-side with each
// visitor's own access token — no backend/secret required. Any AniList user
// can log in with their own account; nothing here is tied to one person.
//
// IMPORTANT: AniList ties exactly one Redirect URL to each Client ID, so the
// client ID must differ between environments (e.g. localhost for dev vs.
// your production domain). Configure it via NEXT_PUBLIC_ANILIST_CLIENT_ID
// (see .env.example) rather than editing this file.
// (see https://anilist.co/settings/developer)

// Shared with the Android app (AniList client 36103, redirect https://arkhime.arkhins.com).
export const ANILIST_CLIENT_ID =
  process.env.NEXT_PUBLIC_ANILIST_CLIENT_ID ?? "36103";

export const ANILIST_AUTHORIZE_URL =
  "https://anilist.co/api/v2/oauth/authorize";

export const ANILIST_GRAPHQL_URL = "https://graphql.anilist.co";

/**
 * Builds the AniList authorize URL for the implicit grant flow.
 * The redirect URI is whatever's registered for this client ID in AniList's
 * developer settings — it must exactly match the origin this app is served
 * from (see .env.example).
 */
export function getAuthorizeUrl(): string {
  const params = new URLSearchParams({
    client_id: ANILIST_CLIENT_ID,
    response_type: "token",
  });
  return `${ANILIST_AUTHORIZE_URL}?${params.toString()}`;
}

/**
 * The web app and the Android app share one AniList client, whose redirect is the site root.
 * Before leaving for AniList the web app leaves this short-lived marker; the root page sees it
 * and sends the login back to /app instead of handing it to the Android app.
 */
export const WEB_LOGIN_KEY = "arkhime_web_login";
/** Where the web app keeps the AniList token in this browser */
export const WEB_TOKEN_KEY = "arkhime_web_token";

/** Arkhime rose, same as the Android app */
export const ACCENT = "#ff4f6d";
export const WEB_LOGIN_TTL_MS = 10 * 60 * 1000;

export function markWebLogin(): void {
  try {
    localStorage.setItem(WEB_LOGIN_KEY, String(Date.now()));
  } catch {
    // storage blocked: the login will be offered to the Android app instead
  }
}
