import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { fail } from "@/lib/comments/http";

// GET /notification/reply -> { notifications: Notification[] }
// Each notification is returned once, then marked delivered.
export async function GET(request: Request) {
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");

  const rows = await db()<{
    notification_id: number; type: number; media_id: number; comment_id: number;
    actor_username: string; content: string | null;
  }[]>`
    update comment_notifications set delivered = true
     where user_id = ${user.user_id} and not delivered
    returning notification_id, type, media_id, comment_id, actor_username, content`;

  return Response.json({
    notifications: rows
      .sort((a, b) => a.notification_id - b.notification_id)
      .map((n) => ({
        username: n.actor_username,
        media_id: n.media_id,
        comment_id: n.comment_id,
        type: n.type,
        content: n.content,
        notification_id: n.notification_id,
      })),
  });
}
