import type { NextRequest } from "next/server";
import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { fail, ok } from "@/lib/comments/http";

// POST /ban/:userId   (mods and admins). Admins can't be banned; only admins can ban mods.
export async function POST(request: NextRequest, ctx: RouteContext<"/api/ban/[userId]">) {
  const { userId } = await ctx.params;
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");
  if (!user.is_mod && !user.is_admin) return fail(403, "Only moderators can ban users");
  if (userId === user.user_id) return fail(400, "You can't ban yourself");

  const sql = db();
  const [target] = await sql<{ is_mod: boolean; is_admin: boolean }[]>`
    select is_mod, is_admin from comment_users where user_id = ${userId}`;
  if (!target) return fail(404, "User not found");
  if (target.is_admin || (target.is_mod && !user.is_admin)) return fail(403, "You can't ban this user");

  await sql`update comment_users set is_banned = true, updated_at = now() where user_id = ${userId}`;
  return ok("User banned");
}
