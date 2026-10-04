import { db } from "@/lib/db";
import { adminIds, fetchAniListViewer, issueToken, loadUser } from "@/lib/comments/auth";
import { fail, readForm } from "@/lib/comments/http";
import { userJson } from "@/lib/comments/serialize";

// POST form: token=<AniList access token>  ->  { authToken, user }
export async function POST(request: Request) {
  const { token } = await readForm(request);
  if (!token) return fail(400, "Missing AniList token");

  const viewer = await fetchAniListViewer(token);
  if (!viewer) return fail(401, "Could not verify your AniList login");

  const userId = String(viewer.id);
  const isAdmin = adminIds().has(userId);
  await db()`
    insert into comment_users (user_id, username, profile_picture_url, is_admin)
    values (${userId}, ${viewer.name}, ${viewer.avatar}, ${isAdmin})
    on conflict (user_id) do update set
      username = excluded.username,
      profile_picture_url = excluded.profile_picture_url,
      is_admin = comment_users.is_admin or excluded.is_admin,
      updated_at = now()`;

  const user = await loadUser(userId);
  if (!user) return fail(500, "Failed to create comments account");
  return Response.json({ authToken: await issueToken(userId), user: userJson(user) });
}
