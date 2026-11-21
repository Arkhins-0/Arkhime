import type { Metadata } from "next";
import { readFile } from "node:fs/promises";
import path from "node:path";
import MarkdownPage from "../markdown-page";

export const metadata: Metadata = {
  title: "Terms and Conditions",
};

export default async function TermsPage() {
  return <MarkdownPage text={await readFile(path.join(process.cwd(), "terms.md"), "utf8")} />;
}
