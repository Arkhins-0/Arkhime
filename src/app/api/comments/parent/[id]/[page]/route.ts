import type { NextRequest } from "next/server";
import { requestUser } from "@/lib/comments/auth";
import { fail, toInt } from "@/lib/comments/http";
import { replies } from "@/lib/comments/queries";
import { commentJson } from "@/lib/comments/serialize";

// GET /comments/parent/:commentId/:page -> { comments: Comment[], totalPages }
export async function GET(request: NextRequest, ctx: RouteContext<"/api/comments/parent/[id]/[page]">) {
  const params = await ctx.params;
  const parentId = toInt(params.id);
  const page = toInt(params.page);
  if (parentId === null || page === null || page < 1) return fail(400, "Invalid comment or page");

  const viewer = await requestUser(request);
  const result = await replies(parentId, page, viewer?.user_id ?? null);
  return Response.json({ comments: result.comments.map(commentJson), totalPages: result.totalPages });
}
