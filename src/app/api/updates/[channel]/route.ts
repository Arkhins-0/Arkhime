import type { NextRequest } from "next/server";
import { isChannel, latest } from "@/lib/updates";

// GET /api/updates/stable | /api/updates/beta -> { version, changelog, downloadUrl }
// Fallback for the in-app updater when the GitHub API can't be reached or is rate limited.
export async function GET(_request: NextRequest, ctx: RouteContext<"/api/updates/[channel]">) {
  const { channel } = await ctx.params;
  if (!isChannel(channel)) return Response.json({ message: "Unknown channel" }, { status: 404 });
  try {
    const info = await latest(channel);
    if (!info) return Response.json({ message: `No ${channel} release yet` }, { status: 404 });
    return Response.json(info);
  } catch {
    return Response.json({ message: "Could not reach GitHub" }, { status: 502 });
  }
}
