import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import Link from "next/link";
import { GITHUB_URL } from "@/lib/site";
import "./globals.css";

const jakarta = Plus_Jakarta_Sans({
  variable: "--font-jakarta",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  metadataBase: new URL("https://arkhime.arkhins.com"),
  title: {
    default: "Arkhime: your AniList, everywhere",
    template: "%s · Arkhime",
  },
  description:
    "Arkhime is an AniList tracker for Android and the web: series grouped together, batch editing, and alerts for new seasons, episodes and finales.",
};

export const viewport: Viewport = {
  themeColor: "#000000",
  colorScheme: "dark",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${jakarta.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col font-sans">
        <header className="sticky top-0 z-40 border-b border-border/60 bg-background/75 backdrop-blur-xl">
          <nav className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
            <Link href="/" className="flex items-center gap-2.5 font-bold tracking-tight">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src="/icon.svg" alt="" width={30} height={30} className="rounded-lg" />
              <span className="text-lg">
                Ark<span className="text-accent">hime</span>
              </span>
            </Link>
            <div className="flex items-center gap-1 text-sm sm:gap-2">
              <Link href="/#features" className="hidden rounded-full px-3 py-2 text-muted hover:text-foreground sm:inline">
                Features
              </Link>
              <a href={GITHUB_URL} className="hidden rounded-full px-3 py-2 text-muted hover:text-foreground sm:inline">
                GitHub
              </a>
              <Link href="/login" className="rounded-full px-3 py-2 font-medium text-foreground hover:bg-card-2">
                Log in
              </Link>
              <a
                href="/download"
                className="rounded-full bg-accent-solid px-4 py-2 font-semibold text-accent-contrast shadow-[0_6px_20px_-6px_var(--glow)] hover:opacity-90"
              >
                Get the app
              </a>
            </div>
          </nav>
        </header>

        <div className="flex-1">{children}</div>

        <footer className="border-t border-border">
          <div className="mx-auto grid max-w-6xl gap-6 px-4 py-10 text-sm text-muted sm:grid-cols-[1fr_auto] sm:px-6">
            <div>
              <p className="font-semibold text-foreground">
                Ark<span className="text-accent">hime</span>
              </p>
              <p className="mt-2 max-w-md">
                An AniList tracker for Android and the web. Open source under the UPL (GPLv3). Arkhime hosts no media.
              </p>
            </div>
            <div className="flex flex-wrap items-start gap-x-6 gap-y-2">
              <Link href="/app" className="hover:text-foreground">Web app</Link>
              <a href="/download" className="hover:text-foreground">Download</a>
              <Link href="/privacy" className="hover:text-foreground">Privacy</Link>
              <a href={GITHUB_URL} className="hover:text-foreground">Source</a>
            </div>
          </div>
        </footer>
      </body>
    </html>
  );
}
