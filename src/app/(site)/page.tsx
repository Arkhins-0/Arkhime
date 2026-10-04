import Link from "next/link";
import { latest } from "@/lib/updates";
import { GITHUB_URL } from "@/lib/site";
import AuthRedirect from "./auth-redirect";

const FEATURES = [
  {
    title: "Series, not scattered titles",
    body: "Seasons, movies, OVAs and spin-offs grouped into one row, with what you have finished and what you have not added yet.",
    tone: "accent",
  },
  {
    title: "Edit many at once",
    body: "Bump progress, change status and set scores across your whole list, then save it to AniList in one go.",
    tone: "accent-2",
  },
  {
    title: "Release alerts",
    body: "New anime and manga on AniList, premieres, every episode and finales, with artwork and one-tap Planning, Watching or Completed.",
    tone: "accent-3",
  },
  {
    title: "Updates feed",
    body: "Everything that happened, newest first, filtered by type: additions, releases, data changes, merges and more.",
    tone: "accent",
  },
  {
    title: "Widgets",
    body: "What airs next and what to continue, right on your home screen.",
    tone: "accent-2",
  },
  {
    title: "Updates itself",
    body: "New versions download inside the app and install in a tap. No store needed.",
    tone: "accent-3",
  },
] as const;

const TONE: Record<(typeof FEATURES)[number]["tone"], string> = {
  accent: "bg-accent",
  "accent-2": "bg-accent-2",
  "accent-3": "bg-accent-3",
};

const SERIES = [
  { title: "Season 1", meta: "TV · 2023 · 28 eps", progress: 1, state: "Completed" },
  { title: "Season 2", meta: "TV · 2026 · 12 eps", progress: 0.58, state: "Watching 7/12" },
  { title: "The Movie", meta: "Movie · 2026", progress: 0, state: "Not on your list" },
];

export default async function Home() {
  // Cached for 10 minutes in lib/updates; a missing release just hides the version
  const release = await latest("stable").catch(() => null);

  return (
    <main>
      <AuthRedirect />

      {/* Hero */}
      <section className="relative overflow-hidden">
        <div aria-hidden className="hero-glow absolute inset-0" />
        <div aria-hidden className="grid-fade absolute inset-0" />
        <div className="relative mx-auto grid max-w-6xl items-center gap-14 px-4 pb-20 pt-14 sm:px-6 md:grid-cols-[1.1fr_0.9fr] md:pb-28 md:pt-20">
          <div>
            <p className="inline-flex items-center gap-2 rounded-full border border-border bg-card/70 px-3 py-1 text-xs font-semibold text-muted backdrop-blur">
              <span className="h-1.5 w-1.5 rounded-full bg-accent" />
              {release ? `Version ${release.version} is out` : "For Android and the web"}
            </p>
            <h1 className="mt-6 text-4xl font-extrabold leading-[1.05] tracking-tight sm:text-6xl">
              Track every season.
              <br />
              <span className="bg-linear-to-r from-accent via-accent-2 to-accent-3 bg-clip-text text-transparent">
                Never miss a release.
              </span>
            </h1>
            <p className="mt-6 max-w-xl text-lg leading-relaxed text-muted">
              Arkhime is an AniList tracker that groups whole series together, edits your list in batches and tells you
              the moment something new is announced, airs or finishes.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <a
                href="/download"
                className="inline-flex items-center gap-2 rounded-full bg-accent-solid px-6 py-3.5 font-semibold text-accent-contrast shadow-[0_10px_30px_-8px_var(--glow)] transition hover:-translate-y-0.5 hover:opacity-95"
              >
                <DownloadIcon />
                Download for Android
              </a>
              <Link
                href="/login"
                className="inline-flex items-center rounded-full border border-border bg-card/60 px-6 py-3.5 font-semibold backdrop-blur transition hover:bg-card-2"
              >
                Open on the web
              </Link>
            </div>
            <p className="mt-4 text-sm text-muted">
              Free and open source · Android 8 or newer · Sign in with AniList
            </p>
          </div>

          <PhoneMockup />
        </div>
      </section>

      {/* Features */}
      <section id="features" className="border-t border-border">
        <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <p className="text-sm font-semibold text-accent">Why Arkhime</p>
          <h2 className="mt-2 max-w-2xl text-3xl font-extrabold tracking-tight sm:text-4xl">
            Built for people who actually keep their list up to date.
          </h2>
          <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <div
                key={f.title}
                className="group rounded-3xl border border-border bg-card p-6 transition hover:-translate-y-1 hover:bg-card-2"
              >
                <span className={`block h-1.5 w-10 rounded-full ${TONE[f.tone]}`} />
                <h3 className="mt-5 text-lg font-bold">{f.title}</h3>
                <p className="mt-2 leading-relaxed text-muted">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Two ways in */}
      <section className="border-t border-border bg-card/40">
        <div className="mx-auto grid max-w-6xl gap-4 px-4 py-20 sm:px-6 md:grid-cols-2">
          <div className="rounded-3xl border border-border bg-card p-8">
            <p className="text-sm font-semibold text-accent">Android</p>
            <h3 className="mt-2 text-2xl font-extrabold tracking-tight">The full app, in your pocket</h3>
            <p className="mt-3 text-muted">
              Lists, release alerts with artwork, the updates feed, widgets and in-app updates.
            </p>
            <a
              href="/download"
              className="mt-6 inline-flex items-center gap-2 rounded-full bg-accent-solid px-5 py-3 font-semibold text-accent-contrast hover:opacity-90"
            >
              <DownloadIcon />
              Download the APK
            </a>
          </div>
          <div className="rounded-3xl border border-border bg-card p-8">
            <p className="text-sm font-semibold text-accent-2">Web</p>
            <h3 className="mt-2 text-2xl font-extrabold tracking-tight">Mass edit on a big screen</h3>
            <p className="mt-3 text-muted">
              Your whole list grouped into series, with filters and sort, and batch editing that saves straight to
              AniList. Everything else is in the Android app.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex items-center rounded-full border border-border px-5 py-3 font-semibold hover:bg-card-2"
            >
              Log in with AniList
            </Link>
          </div>
        </div>
      </section>

      {/* Closing call to action */}
      <section className="border-t border-border">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-16 sm:px-6 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-2xl font-extrabold tracking-tight sm:text-3xl">Ready when the next season is.</h2>
            <p className="mt-2 text-muted">
              Open source on{" "}
              <a href={GITHUB_URL} className="font-semibold text-foreground underline decoration-accent underline-offset-4">
                GitHub
              </a>
              . No ads, no accounts beyond AniList.
            </p>
          </div>
          <a
            href="/download"
            className="inline-flex items-center gap-2 rounded-full bg-accent-solid px-6 py-3.5 font-semibold text-accent-contrast hover:opacity-90"
          >
            <DownloadIcon />
            Get Arkhime
          </a>
        </div>
      </section>
    </main>
  );
}

function DownloadIcon() {
  return (
    <svg aria-hidden width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 4v11m0 0l-4.5-4.5M12 15l4.5-4.5M5 20h14" />
    </svg>
  );
}

/** A drawn phone showing a series row and a release alert, so the page needs no screenshots. */
function PhoneMockup() {
  return (
    <div aria-hidden className="float relative mx-auto w-full max-w-[330px]">
      {/* Alert floating over the phone */}
      <div className="absolute -left-6 top-10 z-10 w-[270px] rounded-2xl border border-border bg-card/95 p-3 shadow-2xl backdrop-blur sm:-left-14">
        <div className="flex items-center gap-2 text-[11px] text-muted">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/icon.svg" alt="" width={16} height={16} className="rounded" />
          Arkhime · Finished airing · now
        </div>
        <p className="mt-1.5 text-sm font-bold">Season 2 is complete</p>
        <p className="text-xs text-muted">Final episode (12) aired</p>
        <div className="mt-2.5 flex gap-2">
          <span className="rounded-full bg-accent-solid px-3 py-1 text-[11px] font-semibold text-accent-contrast">Completed</span>
          <span className="rounded-full border border-border px-3 py-1 text-[11px] font-semibold">Open</span>
        </div>
      </div>

      <div className="rounded-[2.6rem] border border-border bg-card-2 p-2.5 shadow-[0_40px_80px_-30px_var(--glow)]">
        <div className="overflow-hidden rounded-[2.1rem] bg-background">
          <div className="flex items-center justify-between px-5 pb-2 pt-4 text-[11px] text-muted">
            <span>9:41</span>
            <span className="h-4 w-16 rounded-full bg-card-2" />
            <span>100%</span>
          </div>
          <div className="px-4 pb-5 pt-14">
            <p className="text-xs font-semibold text-muted">My anime list · grouped by series</p>
            <div className="mt-3 flex items-center justify-between">
              <p className="font-bold">Frieren</p>
              <span className="text-xs font-bold text-accent">2 +1 · 74%</span>
            </div>
            <div className="mt-2 h-1 rounded-full bg-card-2">
              <div className="h-full w-[74%] rounded-full bg-accent" />
            </div>
            <ul className="mt-4 space-y-2.5">
              {SERIES.map((s, i) => (
                <li key={s.title} className={`flex gap-3 rounded-2xl bg-card p-2.5 ${i === 1 ? "ring-2 ring-accent" : ""}`}>
                  <div
                    className={`h-16 w-11 shrink-0 rounded-lg ${
                      i === 0 ? "bg-linear-to-br from-accent to-accent-2" : i === 1 ? "bg-linear-to-br from-accent-2 to-accent-3" : "bg-card-2"
                    } ${s.progress === 0 ? "opacity-60" : ""}`}
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-bold">{s.title}</p>
                    <p className="text-[11px] text-muted">{s.meta}</p>
                    <p className={`mt-1 text-[11px] font-bold ${s.progress === 0 ? "text-muted" : "text-accent"}`}>{s.state}</p>
                    {s.progress > 0 ? (
                      <div className="mt-1.5 h-1 rounded-full bg-card-2">
                        <div className="h-full rounded-full bg-accent" style={{ width: `${s.progress * 100}%` }} />
                      </div>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
            <div className="mt-4 flex items-center justify-between rounded-2xl bg-card px-4 py-2.5">
              <span className="text-xs font-bold">2 changes</span>
              <span className="rounded-full bg-accent-solid px-3 py-1 text-[11px] font-semibold text-accent-contrast">Save</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
