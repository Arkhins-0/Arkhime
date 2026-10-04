import type { NextRequest } from "next/server";
import { requestUser } from "@/lib/comments/auth";
import { fail, toInt } from "@/lib/comments/http";
import { topLevelComments } from "@/lib/comments/queries";
import { commentJson } from "@/lib/comments/serialize";

// GET /comments/:mediaId/:page?tag=&sort=newest|oldest|highest_rated|lowest_rated
//   -> { comments: Comment[], totalPages }
export async function GET(request: NextRequest, ctx: RouteContext<"/api/comments/[id]/[page]">) {
  const params = await ctx.params;
  const mediaId = toInt(params.id);
  const page = toInt(params.page);
  if (mediaId === null || page === null || page < 1) return fail(400, "Invalid media or page");

  const search = request.nextUrl.searchParams;
  const tag = search.has("tag") ? toInt(search.get("tag")) : null;
  const viewer = await requestUser(request);
  const result = await topLevelComments(mediaId, page, viewer?.user_id ?? null, tag, search.get("sort"));
  return Response.json({ comments: result.comments.map(commentJson), totalPages: result.totalPages });
}
