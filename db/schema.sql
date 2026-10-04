-- Arkhime comments schema. Safe to run repeatedly (npm run db:migrate).

create table if not exists comment_users (
    user_id             text primary key,            -- AniList user id
    username            text not null,
    profile_picture_url text,
    is_banned           boolean not null default false,
    is_mod              boolean not null default false,
    is_admin            boolean not null default false,
    warnings            integer not null default 0,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create table if not exists comments (
    comment_id        serial primary key,
    user_id           text not null references comment_users (user_id),
    media_id          integer not null,              -- AniList media id
    parent_comment_id integer references comments (comment_id) on delete cascade,
    content           text not null,
    tag               integer,                       -- episode / chapter number, optional
    deleted           boolean not null default false,
    created_at        timestamptz not null default now(),
    edited_at         timestamptz
);

create index if not exists comments_media_idx on comments (media_id, created_at desc) where parent_comment_id is null;
create index if not exists comments_parent_idx on comments (parent_comment_id, created_at);
create index if not exists comments_user_idx on comments (user_id);

create table if not exists comment_votes (
    comment_id integer not null references comments (comment_id) on delete cascade,
    user_id    text not null references comment_users (user_id),
    vote       smallint not null check (vote in (-1, 1)),
    created_at timestamptz not null default now(),
    primary key (comment_id, user_id)
);

create table if not exists comment_reports (
    report_id   serial primary key,
    comment_id  integer not null references comments (comment_id) on delete cascade,
    reporter_id text not null references comment_users (user_id),
    reported_id text not null,
    media_name  text,
    resolved    boolean not null default false,
    created_at  timestamptz not null default now(),
    unique (comment_id, reporter_id)
);

-- type: 1 = reply, 2 = warning, 3 = app update (matches the app's NotificationType mapping)
create table if not exists comment_notifications (
    notification_id serial primary key,
    user_id         text not null references comment_users (user_id),   -- recipient
    type            smallint not null,
    media_id        integer not null default 0,
    comment_id      integer not null default 0,
    actor_username  text not null default '',
    content         text,
    delivered       boolean not null default false,
    created_at      timestamptz not null default now()
);

create index if not exists comment_notifications_pending_idx
    on comment_notifications (user_id) where not delivered;
