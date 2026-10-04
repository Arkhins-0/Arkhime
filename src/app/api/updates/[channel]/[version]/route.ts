import type { NextRequest } from "next/server";
import { byVersion, isChannel } from "@/lib/updates";

// GET /api/updates/:channel/:version -> { version, changelog, downloadUrl }
export async function GET(_request: NextRequest, ctx: RouteContext<"/api/updates/[channel]/[version]">) {
  const { channel, version } = await ctx.params;
  if (!isChannel(channel)) return Response.json({ message: "Unknown channel" }, { status: 404 });
  try {
    const info = await byVersion(version);
    if (!info) return Response.json({ message: `Release ${version} not found` }, { status: 404 });
    return Response.json(info);
  } catch {
    return Response.json({ message: "Could not reach GitHub" }, { status: 502 });
  }
}
