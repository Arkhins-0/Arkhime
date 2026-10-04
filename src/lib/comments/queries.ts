import type postgres from "postgres";
import { db } from "@/lib/db";
import type { CommentRow } from "./serialize";

export const PAGE_SIZE = 10;

const ORDER: Record<string, string> = {
  newest: "c.created_at desc",
  oldest: "c.created_at asc",
  highest_rated: "score desc, c.created_at desc",
  lowest_rated: "score asc, c.created_at desc",
};

// Everything the app's Comment class needs, from the point of view of `viewerId`
function select(sql: postgres.Sql, viewerId: string | null) {
  return sql`
    select c.comment_id, c.user_id, c.media_id, c.parent_comment_id, c.content, c.created_at,
           c.deleted, c.tag,
           coalesce(v.up, 0)::int as upvotes,
           coalesce(v.down, 0)::int as downvotes,
           coalesce(v.up, 0) - coalesce(v.down, 0) as score,
           uv.vote::int as user_vote_type,
           u.username, u.profile_picture_url, u.is_mod, u.is_admin,
           (select count(*)::int from comments r
             where r.parent_comment_id = c.comment_id and not r.deleted) as reply_count,
           (select coalesce(sum(av.vote), 0)::int
              from comment_votes av join comments ac on ac.comment_id = av.comment_id
             where ac.user_id = c.user_id) as total_votes
      from comments c
      join comment_users u on u.user_id = c.user_id
      left join lateral (
        select count(*) filter (where vote = 1) as up, count(*) filter (where vote = -1) as down
          from comment_votes where comment_id = c.comment_id
      ) v on true
      left join comment_votes uv on uv.comment_id = c.comment_id and uv.user_id = ${viewerId}`;
}

type Page = { comments: CommentRow[]; totalPages: number };

export async function topLevelComments(
  mediaId: number,
  page: number,
  viewerId: string | null,
  tag: number | null,
  sort: string | null,
): Promise<Page> {
  const sql = db();
  // deleted comments stay visible as "[deleted]" only while they still have replies
  const where = sql`
    where c.media_id = ${mediaId} and c.parent_comment_id is null
      ${tag !== null ? sql`and c.tag = ${tag}` : sql``}
      and (not c.deleted or exists (select 1 from comments r where r.parent_comment_id = c.comment_id and not r.deleted))`;
  const order = ORDER[sort ?? ""] ?? ORDER.newest;
  const [rows, [{ count }]] = await Promise.all([
    sql<CommentRow[]>`${select(sql, viewerId)} ${where}
      order by ${sql.unsafe(order)} limit ${PAGE_SIZE} offset ${(page - 1) * PAGE_SIZE}`,
    sql<{ count: number }[]>`select count(*)::int as count from comments c ${where}`,
  ]);
  return { comments: rows, totalPages: Math.ceil(count / PAGE_SIZE) };
}

export async function replies(parentId: number, page: number, viewerId: string | null): Promise<Page> {
  const sql = db();
  const where = sql`where c.parent_comment_id = ${parentId} and not c.deleted`;
  const [rows, [{ count }]] = await Promise.all([
    sql<CommentRow[]>`${select(sql, viewerId)} ${where}
      order by c.created_at asc limit ${PAGE_SIZE} offset ${(page - 1) * PAGE_SIZE}`,
    sql<{ count: number }[]>`select count(*)::int as count from comments c ${where}`,
  ]);
  return { comments: rows, totalPages: Math.ceil(count / PAGE_SIZE) };
}

export async function singleComment(commentId: number, viewerId: string | null): Promise<CommentRow | null> {
  const sql = db();
  const [row] = await sql<CommentRow[]>`${select(sql, viewerId)} where c.comment_id = ${commentId}`;
  return row ?? null;
}
