import { GITHUB_REPO } from "@/lib/site";

// Shape the app's AppUpdater.FallbackResponse expects
export type UpdateInfo = { version: string; changelog: string; downloadUrl: string | null };

type Release = {
  tag_name: string;
  body: string | null;
  draft: boolean;
  prerelease: boolean;
  published_at: string | null;
  assets: { name: string; browser_download_url: string }[];
};

export type Channel = "stable" | "beta";

async function releases(): Promise<Release[]> {
  const headers: Record<string, string> = { Accept: "application/vnd.github+json", "User-Agent": "arkhime-site" };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  // Cached for 10 minutes so app installs don't burn through GitHub's rate limit
  const res = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/releases?per_page=30`, {
    headers,
    next: { revalidate: 600 },
  });
  // 404 = repo not public yet; treat as "no releases" rather than an outage
  if (res.status === 404) return [];
  if (!res.ok) throw new Error(`GitHub responded ${res.status}`);
  return res.json();
}

// The fallback can't pick an ABI, so prefer the universal APK of the Google build
function apkUrl(release: Release): string | null {
  const apks = release.assets.filter((a) => a.name.endsWith(".apk") && !/fdroid/i.test(a.name));
  const pick = apks.find((a) => /universal/i.test(a.name)) ?? apks[0];
  return pick?.browser_download_url ?? null;
}

function toInfo(release: Release): UpdateInfo {
  return {
    version: release.tag_name.replace(/^v/, ""),
    changelog: release.body ?? "",
    downloadUrl: apkUrl(release),
  };
}

const newestFirst = (a: Release, b: Release) =>
  new Date(b.published_at ?? 0).getTime() - new Date(a.published_at ?? 0).getTime();

export async function latest(channel: Channel): Promise<UpdateInfo | null> {
  const candidates = (await releases())
    .filter((r) => !r.draft && !/fdroid/i.test(r.tag_name))
    .filter((r) => (channel === "beta" ? r.prerelease : !r.prerelease))
    .sort(newestFirst);
  return candidates[0] ? toInfo(candidates[0]) : null;
}

export async function byVersion(version: string): Promise<UpdateInfo | null> {
  const wanted = version.replace(/^v/, "");
  const release = (await releases()).find((r) => !r.draft && r.tag_name.replace(/^v/, "") === wanted);
  return release ? toInfo(release) : null;
}

export function isChannel(value: string): value is Channel {
  return value === "stable" || value === "beta";
}
