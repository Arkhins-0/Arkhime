import { redirect } from "next/navigation";
import { latest } from "@/lib/updates";
import { GITHUB_URL } from "@/lib/site";

// GET /download -> the newest stable release's universal APK on GitHub.
// Falls back to the releases page when there is no release (or GitHub can't be reached).
export async function GET() {
  let url = `${GITHUB_URL}/releases`;
  try {
    const info = await latest("stable");
    if (info?.downloadUrl) url = info.downloadUrl;
  } catch {
    // keep the releases page
  }
  redirect(url);
}
