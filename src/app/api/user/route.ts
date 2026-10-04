import { requestUser } from "@/lib/comments/auth";
import { fail } from "@/lib/comments/http";
import { userJson } from "@/lib/comments/serialize";

// GET -> { user }. A 401 makes the app log in again via /authenticate.
export async function GET(request: Request) {
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");
  return Response.json({ user: userJson(user) });
}
