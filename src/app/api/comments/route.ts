import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { cleanContent, fail, MAX_COMMENT_LENGTH, readForm, toInt } from "@/lib/comments/http";
import { createdCommentJson } from "@/lib/comments/serialize";

const RATE_LIMIT_PER_MINUTE = 5;

// POST form: media_id, content, [tag], [parent_comment_id]  ->  ReturnedComment
// (the app also sends user_id; the logged-in user is taken from the token instead)
export async function POST(request: Request) {
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");
  if (user.is_banned) return fail(403, "You are banned from commenting");

  const form = await readForm(request);
  const mediaId = toInt(form.media_id);
  const content = cleanContent(form.content);
  const tag = form.tag ? toInt(form.tag) : null;
  const parentId = form.parent_comment_id ? toInt(form.parent_comment_id) : null;
  if (!mediaId || mediaId <= 0) return fail(400, "Invalid media");
  if (!content) return fail(400, `Comments must be 1 to ${MAX_COMMENT_LENGTH} characters`);

  const sql = db();
  const [{ recent }] = await sql<{ recent: number }[]>`
    select count(*)::int as recent from comments
     where user_id = ${user.user_id} and created_at > now() - interval '1 minute'`;
  if (recent >= RATE_LIMIT_PER_MINUTE) return fail(429, "You're commenting too fast. Try again in a minute.");

  let parent: { user_id: string; media_id: number; deleted: boolean } | undefined;
  if (parentId !== null) {
    [parent] = await sql<{ user_id: string; media_id: number; deleted: boolean }[]>`
      select user_id, media_id, deleted from comments where comment_id = ${parentId}`;
    if (!parent || parent.deleted) return fail(404, "The comment you replied to no longer exists");
    if (parent.media_id !== mediaId) return fail(400, "Reply doesn't match the comment's media");
  }

  const [created] = await sql<Parameters<typeof createdCommentJson>[0][]>`
    insert into comments (user_id, media_id, parent_comment_id, content, tag)
    values (${user.user_id}, ${mediaId}, ${parentId}, ${content}, ${tag})
    returning comment_id, user_id, media_id, parent_comment_id, content, created_at, deleted, tag`;

  // Notify the parent author about the reply (type 1), unless they replied to themselves
  if (parent && parent.user_id !== user.user_id) {
    await sql`
      insert into comment_notifications (user_id, type, media_id, comment_id, actor_username, content)
      values (${parent.user_id}, 1, ${mediaId}, ${created.comment_id}, ${user.username}, ${content.slice(0, 200)})`;
  }

  return Response.json(createdCommentJson(created));
}
