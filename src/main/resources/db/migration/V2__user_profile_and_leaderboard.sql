alter table users
    add column if not exists full_name varchar(80),
    add column if not exists email varchar(160),
    add column if not exists city varchar(80),
    add column if not exists favorite_player varchar(80);

update users
set full_name = username
where full_name is null or btrim(full_name) = '';

create unique index if not exists uq_users_email_lower
    on users (lower(email))
    where email is not null;

create index if not exists idx_users_leaderboard
    on users (matches_won desc, total_runs desc, matches_played desc, created_at asc);
