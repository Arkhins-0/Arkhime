import type { NextRequest } from "next/server";
import { db } from "@/lib/db";
import { requestUser } from "@/lib/comments/auth";
import { fail, ok, readForm, toInt } from "@/lib/comments/http";

// POST /report/:commentId  form: username, mediaName, reporter, reportedId
export async function POST(request: NextRequest, ctx: RouteContext<"/api/report/[commentId]">) {
  const commentId = toInt((await ctx.params).commentId);
  if (commentId === null) return fail(400, "Invalid comment id");
  const user = await requestUser(request);
  if (!user) return fail(401, "Not logged in");

  const sql = db();
  const [comment] = await sql<{ user_id: string; content: string; media_id: number }[]>`
    select user_id, content, media_id from comments where comment_id = ${commentId}`;
  if (!comment) return fail(404, "Comment not found");

  const form = await readForm(request);
  const inserted = await sql`
    insert into comment_reports (comment_id, reporter_id, reported_id, media_name)
    values (${commentId}, ${user.user_id}, ${comment.user_id}, ${form.mediaName ?? null})
    on conflict (comment_id, reporter_id) do nothing
    returning report_id`;

  // Optional: send new reports to a Telegram chat for moderators
  const botToken = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  if (botToken && chatId && inserted.length > 0) {
    const text = [
      `<b>Comment #${commentId} reported</b>`,
      `Author: ${escapeHtml(form.username || comment.user_id)}`,
      `Reporter: ${escapeHtml(user.username)}`,
      `Media: ${escapeHtml(form.mediaName ?? "?")} (${comment.media_id})`,
      "",
      `<blockquote>${escapeHtml(comment.content.slice(0, 1500))}</blockquote>`,
    ].join("\n");
    await fetch(`https://api.telegram.org/bot${botToken}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, text, parse_mode: "HTML", disable_web_page_preview: true }),
    }).catch(() => {});
  }
  return ok("Report received");
}

// Telegram's HTML mode only needs these three escaped
function escapeHtml(value: string) {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
