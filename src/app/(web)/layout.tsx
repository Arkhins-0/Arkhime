import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import "./app/web.css";

// Root layout for the web app (/app, /login). The marketing site has its own in (site).
const jakarta = Plus_Jakarta_Sans({
  variable: "--font-jakarta",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  metadataBase: new URL("https://arkhime.arkhins.com"),
  title: {
    default: "Arkhime Web",
    template: "%s · Arkhime",
  },
  description: "Your AniList collection on the web, grouped by series, with batch editing.",
};

export const viewport: Viewport = {
  themeColor: "#121013",
};

export default function WebLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${jakarta.variable} antialiased`}>
      <body>{children}</body>
    </html>
  );
}
