import type { NextRequest } from "next/server";
import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { fail, ok, toInt } from "@/lib/comments/http";

// POST /comments/vote/:commentId/:voteType   voteType: 1 up, -1 down, 0 remove vote
export async function POST(request: NextRequest, ctx: RouteContext<"/api/comments/vote/[commentId]/[voteType]">) {
  const params = await ctx.params;
  const commentId = toInt(params.commentId);
  const voteType = toInt(params.voteType);
  if (commentId === null || voteType === null || ![-1, 0, 1].includes(voteType)) {
    return fail(400, "Invalid vote");
  }
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");
  if (user.is_banned) return fail(403, "You are banned from voting");

  const sql = db();
  const [comment] = await sql<{ user_id: string; deleted: boolean }[]>`
    select user_id, deleted from comments where comment_id = ${commentId}`;
  if (!comment || comment.deleted) return fail(404, "Comment not found");
  if (comment.user_id === user.user_id) return fail(400, "You can't vote on your own comment");

  if (voteType === 0) {
    await sql`delete from comment_votes where comment_id = ${commentId} and user_id = ${user.user_id}`;
  } else {
    await sql`
      insert into comment_votes (comment_id, user_id, vote) values (${commentId}, ${user.user_id}, ${voteType})
      on conflict (comment_id, user_id) do update set vote = excluded.vote, created_at = now()`;
  }
  return ok("Vote saved");
}
