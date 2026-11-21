import type { Metadata } from "next";
import { readFile } from "node:fs/promises";
import path from "node:path";
import MarkdownPage from "../markdown-page";

export const metadata: Metadata = {
  title: "Privacy Policy",
};

// Single source of truth: the same file the app links to on GitHub
export default async function PrivacyPage() {
  return <MarkdownPage text={await readFile(path.join(process.cwd(), "privacy_policy.md"), "utf8")} />;
}
