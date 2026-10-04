import type { NextRequest } from "next/server";

// arkhime.arkhins.com/u/<AniList username> -> the user's AniList profile.
// Short, shareable profile link; can become a real profile page later.
export async function GET(_request: NextRequest, ctx: RouteContext<"/u/[user]">) {
  const { user } = await ctx.params;
  return Response.redirect(`https://anilist.co/user/${encodeURIComponent(user)}/`, 307);
}
