import type { NextRequest } from "next/server";
import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { cleanContent, fail, MAX_COMMENT_LENGTH, ok, readForm, toInt } from "@/lib/comments/http";
import { singleComment } from "@/lib/comments/queries";
import { commentJson } from "@/lib/comments/serialize";

// GET /comments/:id -> Comment
export async function GET(request: NextRequest, ctx: RouteContext<"/api/comments/[id]">) {
  const id = toInt((await ctx.params).id);
  if (id === null) return fail(400, "Invalid comment id");
  const viewer = await requestUser(request);
  const comment = await singleComment(id, viewer?.user_id ?? null);
  if (!comment) return fail(404, "Comment not found");
  return Response.json(commentJson(comment));
}

// PUT /comments/:id  form: content   (author only)
export async function PUT(request: NextRequest, ctx: RouteContext<"/api/comments/[id]">) {
  const id = toInt((await ctx.params).id);
  if (id === null) return fail(400, "Invalid comment id");
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");
  if (user.is_banned) return fail(403, "You are banned from commenting");

  const content = cleanContent((await readForm(request)).content);
  if (!content) return fail(400, `Comments must be 1 to ${MAX_COMMENT_LENGTH} characters`);

  const updated = await db()`
    update comments set content = ${content}, edited_at = now()
     where comment_id = ${id} and user_id = ${user.user_id} and not deleted
    returning comment_id`;
  if (updated.length === 0) return fail(404, "Comment not found or not yours");
  return ok("Comment updated");
}

// DELETE /comments/:id   (author, mod or admin). Soft delete keeps reply threads intact.
export async function DELETE(request: NextRequest, ctx: RouteContext<"/api/comments/[id]">) {
  const id = toInt((await ctx.params).id);
  if (id === null) return fail(400, "Invalid comment id");
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");

  const sql = db();
  const canModerate = user.is_mod || user.is_admin;
  const deleted = await sql`
    update comments set deleted = true, content = '[deleted]'
     where comment_id = ${id} and not deleted
       ${canModerate ? sql`` : sql`and user_id = ${user.user_id}`}
    returning comment_id`;
  if (deleted.length === 0) return fail(404, "Comment not found or not yours");
  return ok("Comment deleted");
}
