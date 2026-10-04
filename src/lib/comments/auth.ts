import { SignJWT, jwtVerify } from "jose";
import { db } from "@/lib/db";

export type DbUser = {
  user_id: string;
  username: string;
  profile_picture_url: string | null;
  is_banned: boolean;
  is_mod: boolean;
  is_admin: boolean;
  warnings: number;
  total_votes: number;
};

const ISSUER = "arkhime-comments";
// The app caches the token for 6 days, so it must outlive that
const TOKEN_LIFETIME = "7d";

function secret(): Uint8Array {
  const value = process.env.COMMENTS_JWT_SECRET;
  if (!value || value.length < 32) {
    throw new Error("COMMENTS_JWT_SECRET must be set to at least 32 characters");
  }
  return new TextEncoder().encode(value);
}

export function adminIds(): Set<string> {
  return new Set(
    (process.env.COMMENTS_ADMIN_ANILIST_IDS ?? "")
      .split(",")
      .map((id) => id.trim())
      .filter(Boolean),
  );
}

export async function issueToken(userId: string): Promise<string> {
  return new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(userId)
    .setIssuer(ISSUER)
    .setIssuedAt()
    .setExpirationTime(TOKEN_LIFETIME)
    .sign(secret());
}

async function verifyToken(token: string): Promise<string | null> {
  try {
    const { payload } = await jwtVerify(token, secret(), { issuer: ISSUER, algorithms: ["HS256"] });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

export async function loadUser(userId: string): Promise<DbUser | null> {
  const sql = db();
  const [user] = await sql<DbUser[]>`
    select u.user_id, u.username, u.profile_picture_url, u.is_banned, u.is_mod, u.is_admin, u.warnings,
           (select coalesce(sum(v.vote), 0)::int
              from comment_votes v join comments c on c.comment_id = v.comment_id
             where c.user_id = u.user_id) as total_votes
      from comment_users u
     where u.user_id = ${userId}`;
  return user ?? null;
}

// The app sends the raw token in Authorization (no "Bearer" prefix); accept both.
export async function requestUser(request: Request): Promise<DbUser | null> {
  const header = request.headers.get("authorization");
  if (!header) return null;
  const token = header.replace(/^Bearer\s+/i, "").trim();
  const userId = await verifyToken(token);
  return userId ? loadUser(userId) : null;
}

export type AniListViewer = { id: number; name: string; avatar: string | null };

// Proves the caller owns an AniList account without storing their AniList token
export async function fetchAniListViewer(token: string): Promise<AniListViewer | null> {
  try {
    const res = await fetch("https://graphql.anilist.co", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({ query: "query { Viewer { id name avatar { large } } }" }),
      cache: "no-store",
    });
    if (!res.ok) return null;
    const body = await res.json();
    const viewer = body?.data?.Viewer;
    if (!viewer?.id || !viewer?.name) return null;
    return { id: viewer.id, name: viewer.name, avatar: viewer.avatar?.large ?? null };
  } catch {
    return null;
  }
}
