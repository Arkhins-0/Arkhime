// JSON shapes must match the @Serializable classes in the app's CommentsAPI.kt exactly:
// the app uses a strict Json parser, so unknown or missing keys make parsing fail.
// Booleans are sent as 0/1 (NumericBooleanSerializer).
import type { DbUser } from "./auth";

const bit = (value: boolean | null | undefined) => (value ? 1 : 0);

// "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" is what the app parses
const iso = (value: Date | string) => new Date(value).toISOString();

export function userJson(u: DbUser) {
  return {
    user_id: u.user_id,
    username: u.username,
    profile_picture_url: u.profile_picture_url,
    is_banned: bit(u.is_banned),
    is_mod: bit(u.is_mod),
    is_admin: bit(u.is_admin),
    total_votes: u.total_votes,
    warnings: u.warnings,
  };
}

export type CommentRow = {
  comment_id: number;
  user_id: string;
  media_id: number;
  parent_comment_id: number | null;
  content: string;
  created_at: Date;
  deleted: boolean;
  tag: number | null;
  upvotes: number;
  downvotes: number;
  user_vote_type: number | null;
  username: string;
  profile_picture_url: string | null;
  is_mod: boolean;
  is_admin: boolean;
  reply_count: number;
  total_votes: number;
};

export function commentJson(c: CommentRow) {
  return {
    comment_id: c.comment_id,
    user_id: c.user_id,
    media_id: c.media_id,
    parent_comment_id: c.parent_comment_id,
    content: c.content,
    timestamp: iso(c.created_at),
    deleted: bit(c.deleted),
    tag: c.tag,
    upvotes: c.upvotes,
    downvotes: c.downvotes,
    user_vote_type: c.user_vote_type,
    username: c.username,
    profile_picture_url: c.profile_picture_url,
    is_mod: bit(c.is_mod),
    is_admin: bit(c.is_admin),
    reply_count: c.reply_count,
    total_votes: c.total_votes,
  };
}

// Response to POST /comments (ReturnedComment)
export function createdCommentJson(c: {
  comment_id: number;
  user_id: string;
  media_id: number;
  parent_comment_id: number | null;
  content: string;
  created_at: Date;
  deleted: boolean;
  tag: number | null;
}) {
  return {
    id: c.comment_id,
    comment_id: c.comment_id,
    user_id: c.user_id,
    media_id: c.media_id,
    parent_comment_id: c.parent_comment_id,
    content: c.content,
    timestamp: iso(c.created_at),
    deleted: bit(c.deleted),
    tag: c.tag,
  };
}
